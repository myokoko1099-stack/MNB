package com.mnb.browser.translation.background

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.MockTranslationProvider
import com.mnb.browser.translation.TargetLanguage
import com.mnb.browser.translation.TranslationError
import com.mnb.browser.translation.TranslationProvider
import com.mnb.browser.translation.TranslationStatus
import com.mnb.browser.translation.chunking.ChapterChunkRepository
import com.mnb.browser.translation.db.ChapterRepository
import com.mnb.browser.translation.db.MnbDatabase
import com.mnb.browser.translation.db.TranslationJobRepository
import com.mnb.browser.translation.db.TranslationRepository
import com.mnb.browser.translation.memory.MemoryRepository
import com.mnb.browser.translation.usecase.InstantTranslationPipeline

/**
 * Translates one chapter in the background. WorkManager runs [doWork] on its own thread, never the
 * main thread. [isStopped] is passed to the pipeline as its cancellation check.
 *
 * (I10-D) Hard provider guard: when the worker builds its own provider-backed runner (the
 * production path), [TranslationWorkerGuard] must allow the work first. A missing provider, a mock
 * provider, a provider that differs from the reader's, or an unsupported source language ends the
 * work with failure; no provider is called and nothing is stored as a translation.
 */
class TranslationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): ListenableWorker.Result {
        val chapterId = inputData.getString(WorkManagerTranslationScheduler.KEY_CHAPTER_ID)
            ?.takeIf { it.isNotBlank() }
            ?: return ListenableWorker.Result.failure()
        val sourceLanguage = inputData.getString(WorkManagerTranslationScheduler.KEY_SOURCE_LANGUAGE)

        if (TranslationWorkEnvironment.usesDefaultRunner()) {
            val refusal = TranslationWorkEnvironment.checkProvider(sourceLanguage)
            if (refusal != null) {
                TranslationWorkEnvironment.markRefused(applicationContext, ChapterId(chapterId), refusal)
                return ListenableWorker.Result.failure()
            }
        }

        val runner = TranslationWorkEnvironment.runnerFactory(applicationContext)
        val result = runner.run(
            chapterId = ChapterId(chapterId),
            sourceLanguageCode = sourceLanguage,
            targetLanguageCode = inputData.getString(WorkManagerTranslationScheduler.KEY_TARGET_LANGUAGE)
                ?: TargetLanguage.MYANMAR.code,
            attempt = runAttemptCount,
            isStopped = { isStopped },
        )
        return when (result) {
            JobRunResult.SUCCESS -> ListenableWorker.Result.success()
            // DEFERRED (I9-B1): an outranking job starts first; WorkManager tries this work again later.
            // DEFERRED (I10-D): the chapter is being translated right now by another caller.
            JobRunResult.RETRY, JobRunResult.DEFERRED -> ListenableWorker.Result.retry()
            JobRunResult.FAILURE -> ListenableWorker.Result.failure()
        }
    }
}

/**
 * Where the worker gets its dependencies (no custom WorkerFactory or Application class needed).
 * [providerFactory] defaults to the mock provider because no real provider exists yet; set it
 * before enqueueing once one does. Tests replace [runnerFactory].
 *
 * (I10-D) The mock default is now harmless: the worker refuses it ([TranslationWorkerGuard]).
 * When a real provider is added, set [providerFactory] AND [readerProviderIdSource] to that same
 * provider (same id) in addition to the reader's provider source; otherwise the worker refuses.
 */
object TranslationWorkEnvironment {
    @Volatile var providerFactory: () -> TranslationProvider = { MockTranslationProvider() }

    /** The id of the provider the reader uses; null while the reader has none. */
    @Volatile var readerProviderIdSource: () -> String? = { null }

    private val defaultRunnerFactory: (Context) -> TranslationJobRunner = { context -> defaultRunner(context) }

    @Volatile var runnerFactory: (Context) -> TranslationJobRunner = defaultRunnerFactory

    /** Where a refused job is marked FAILED. Tests replace it. */
    @Volatile var jobsFactory: (Context) -> TranslationJobRepository =
        { context -> TranslationJobRepository(MnbDatabase.getInstance(context)) }

    /** True while the worker builds its own provider-backed runner (nobody injected one). */
    internal fun usesDefaultRunner(): Boolean = runnerFactory === defaultRunnerFactory

    /** Null when the worker may translate, otherwise why it must not. */
    internal fun checkProvider(sourceLanguageCode: String?): WorkerRefusal? {
        val provider: TranslationProvider? = try { providerFactory() } catch (e: Exception) { null }
        val readerId: String? = try { readerProviderIdSource() } catch (e: Exception) { null }
        return TranslationWorkerGuard.check(provider, readerId, sourceLanguageCode)
    }

    /**
     * Best effort: a refused job that is still PENDING/PROCESSING becomes FAILED so it does not
     * stay in the queue and hold back other jobs. Finished jobs are left alone.
     */
    internal fun markRefused(context: Context, chapterId: ChapterId, refusal: WorkerRefusal) {
        try {
            val jobs = jobsFactory(context)
            val job = jobs.latestForChapter(chapterId) ?: return
            if (job.status != TranslationStatus.PENDING && job.status != TranslationStatus.PROCESSING) return
            val error = if (refusal == WorkerRefusal.UNSUPPORTED_LANGUAGE) TranslationError.UNSUPPORTED_SOURCE_LANGUAGE else null
            jobs.updateProgress(
                job.jobId,
                TranslationStatus.FAILED,
                job.completedChunks,
                maxOf(System.currentTimeMillis(), job.createdAtMillis),
                error,
                null,
            )
        } catch (e: Exception) {
            // Ignored on purpose: the refusal itself already stopped the work.
        }
    }

    fun defaultRunner(context: Context): TranslationJobRunner {
        val db = MnbDatabase.getInstance(context)
        val chapters = ChapterRepository(db)
        val pipeline = InstantTranslationPipeline(
            provider = providerFactory(),
            chapters = chapters,
            chunkRepository = ChapterChunkRepository(db),
            translations = TranslationRepository(db),
            memory = MemoryRepository(db),
        )
        return TranslationJobRunner(chapters, TranslationJobRepository(db), pipeline)
    }

    /** The queue the app uses to add chapters. Call off the main thread when using it. */
    fun queue(context: Context): TranslationQueue {
        val db = MnbDatabase.getInstance(context)
        return TranslationQueue(
            chapters = ChapterRepository(db),
            translations = TranslationRepository(db),
            jobs = TranslationJobRepository(db),
            scheduler = WorkManagerTranslationScheduler(WorkManager.getInstance(context)),
            providerId = providerFactory().id,
        )
    }

    fun reset() {
        providerFactory = { MockTranslationProvider() }
        readerProviderIdSource = { null }
        runnerFactory = defaultRunnerFactory
        jobsFactory = { context -> TranslationJobRepository(MnbDatabase.getInstance(context)) }
    }
}
