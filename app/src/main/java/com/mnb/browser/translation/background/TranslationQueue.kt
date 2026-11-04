package com.mnb.browser.translation.background

import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.TargetLanguage
import com.mnb.browser.translation.TranslationJob
import com.mnb.browser.translation.TranslationPriority
import com.mnb.browser.translation.TranslationStatus
import com.mnb.browser.translation.db.ChapterRepository
import com.mnb.browser.translation.db.StoredTranslation
import com.mnb.browser.translation.db.TranslationJobRepository
import com.mnb.browser.translation.db.TranslationRepository

sealed interface EnqueueResult {
    /** A job row exists (new, or an orphaned unfinished one) and work is scheduled. */
    data class Enqueued(val job: TranslationJob) : EnqueueResult

    /** The chapter already has an unfinished job with live work; nothing was added. */
    data class AlreadyQueued(val job: TranslationJob) : EnqueueResult

    /** A completed, up-to-date translation by the same provider exists; nothing was added. */
    data class AlreadyTranslated(val translation: StoredTranslation) : EnqueueResult

    /** The chapter's original text is not stored yet (store it with ChapterRepository first). */
    object ChapterNotFound : EnqueueResult

    data class Failed(val message: String?) : EnqueueResult
}

/**
 * Background translation queue: one job row (database status) plus one unique WorkManager work per
 * chapter. Blocking: call off the main thread. The chapter's original text must already be stored.
 *
 * Priority (I9-B1): each new job gets a [TranslationPriority] (NORMAL by default). Waiting jobs
 * start highest priority first and FIFO within the same priority; [TranslationJobRunner] enforces
 * that order when a job's work starts. Enqueueing a chapter that already has an unfinished job never
 * changes that job's priority; a PENDING job's priority is changed with [TranslationPriorityManager] (I9-B2).
 */
class TranslationQueue(
    private val chapters: ChapterRepository,
    private val translations: TranslationRepository,
    private val jobs: TranslationJobRepository,
    private val scheduler: TranslationWorkScheduler,
    private val providerId: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Synchronized
    fun enqueue(
        chapterId: ChapterId,
        sourceLanguageCode: String?,
        targetLanguageCode: String = TargetLanguage.MYANMAR.code,
        priority: TranslationPriority = TranslationPriority.NORMAL,
    ): EnqueueResult {
        return try {
            if (chapters.get(chapterId) == null) return EnqueueResult.ChapterNotFound
            val stored = translations.getTranslation(chapterId)
            if (stored != null &&
                stored.status == TranslationStatus.COMPLETED &&
                stored.metadata.providerId == providerId &&
                !translations.isStale(chapterId)
            ) {
                return EnqueueResult.AlreadyTranslated(stored)
            }
            val latest = jobs.latestForChapter(chapterId)
            if (latest != null && !latest.status.isFinished) {
                if (scheduler.isScheduled(chapterId)) return EnqueueResult.AlreadyQueued(latest)
                // Row says unfinished but no live work exists: reuse the row (and its priority), schedule again.
                scheduler.schedule(chapterId, sourceLanguageCode, targetLanguageCode)
                return EnqueueResult.Enqueued(latest)
            }
            val job = jobs.create(chapterId, 0, clock(), priority)
            scheduler.schedule(chapterId, sourceLanguageCode, targetLanguageCode)
            EnqueueResult.Enqueued(job)
        } catch (e: Exception) {
            EnqueueResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Cancels the chapter's live work and marks its unfinished job CANCELLED. True when a job was cancelled. */
    @Synchronized
    fun cancel(chapterId: ChapterId): Boolean {
        scheduler.cancel(chapterId)
        val latest = jobs.latestForChapter(chapterId) ?: return false
        if (latest.status.isFinished) return false
        return jobs.updateProgress(
            latest.jobId,
            TranslationStatus.CANCELLED,
            latest.completedChunks,
            maxOf(clock(), latest.createdAtMillis),
        )
    }

    /**
     * Fixes DB/WorkManager mismatch after a restart: a PENDING or PROCESSING job with no live work
     * cannot make progress, so it is marked FAILED (enqueue it again; finished chunks are reused).
     * Jobs with live work are left alone: WorkManager resumes them by itself. Returns the number fixed.
     */
    @Synchronized
    fun recoverUnfinished(): Int {
        var fixed = 0
        for (job in jobs.findUnfinished()) {
            if (scheduler.isScheduled(job.chapterId)) continue
            jobs.updateProgress(
                job.jobId,
                TranslationStatus.FAILED,
                job.completedChunks,
                maxOf(clock(), job.createdAtMillis),
            )
            fixed++
        }
        return fixed
    }
}
