package com.mnb.browser.translation.background

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.MockTranslationProvider
import com.mnb.browser.translation.TargetLanguage
import com.mnb.browser.translation.TranslationProvider
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
 */
class TranslationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): ListenableWorker.Result {
        val chapterId = inputData.getString(WorkManagerTranslationScheduler.KEY_CHAPTER_ID)
            ?.takeIf { it.isNotBlank() }
            ?: return ListenableWorker.Result.failure()
        val runner = TranslationWorkEnvironment.runnerFactory(applicationContext)
        val result = runner.run(
            chapterId = ChapterId(chapterId),
            sourceLanguageCode = inputData.getString(WorkManagerTranslationScheduler.KEY_SOURCE_LANGUAGE),
            targetLanguageCode = inputData.getString(WorkManagerTranslationScheduler.KEY_TARGET_LANGUAGE)
                ?: TargetLanguage.MYANMAR.code,
            attempt = runAttemptCount,
            isStopped = { isStopped },
        )
        return when (result) {
            JobRunResult.SUCCESS -> ListenableWorker.Result.success()
            JobRunResult.RETRY -> ListenableWorker.Result.retry()
            JobRunResult.FAILURE -> ListenableWorker.Result.failure()
        }
    }
}

/**
 * Where the worker gets its dependencies (no custom WorkerFactory or Application class needed).
 * [providerFactory] defaults to the mock provider because no real provider exists yet; set it
 * before enqueueing once one does. Tests replace [runnerFactory].
 */
object TranslationWorkEnvironment {
    @Volatile var providerFactory: () -> TranslationProvider = { MockTranslationProvider() }

    @Volatile var runnerFactory: (Context) -> TranslationJobRunner = { context -> defaultRunner(context) }

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
        runnerFactory = { context -> defaultRunner(context) }
    }
}
