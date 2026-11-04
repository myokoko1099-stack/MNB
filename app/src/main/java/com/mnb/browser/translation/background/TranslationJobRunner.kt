package com.mnb.browser.translation.background

import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.ChapterTranslation
import com.mnb.browser.translation.TranslationError
import com.mnb.browser.translation.TranslationJob
import com.mnb.browser.translation.TranslationStatus
import com.mnb.browser.translation.db.ChapterRepository
import com.mnb.browser.translation.db.TranslationJobRepository
import com.mnb.browser.translation.usecase.InstantTranslationFailure
import com.mnb.browser.translation.usecase.InstantTranslationOutcome
import com.mnb.browser.translation.usecase.InstantTranslationPipeline
import com.mnb.browser.translation.usecase.TranslateChapterRequest

enum class JobRunResult {
    SUCCESS,
    RETRY,
    FAILURE,

    /**
     * (I9-B1) A pending job with higher priority, or queued earlier with the same priority, must start first. The job stays PENDING.
     * (I10-D) Also: the chapter is being translated right now by another caller (instant or background); the job goes back to PENDING.
     */
    DEFERRED,
}

/**
 * Runs one chapter job (the body of [TranslationWorker]) with [InstantTranslationPipeline] and keeps
 * the job row in step. Blocking: call off the main thread. Finished chunks are reused by the
 * pipeline on every new attempt.
 *
 * Retryable failures (provider, partial, database) put the job back to PENDING until
 * [maxAttempts] attempts are used, then FAILED. Permanent failures (empty text, unsupported
 * language, invalid chunks, missing chapter) are FAILED at once. A stop (isStopped) leaves the job
 * PENDING so the work can resume, unless the job was cancelled meanwhile.
 *
 * Priority (I9-B1): a PENDING job only starts when no other PENDING job outranks it (higher
 * priority first, then FIFO). If one does, the runner waits up to [turnWaitMillis] for it to start
 * and then returns [JobRunResult.DEFERRED]. A job that is already PROCESSING (resumed) is never held back.
 *
 * Busy chapter (I10-D): when the pipeline reports DUPLICATE_REQUEST (the shared per-chapter lock is
 * held, for example by an instant translation) nothing was translated or stored. The job returns to
 * PENDING and the result is [JobRunResult.DEFERRED]; no retry attempt is used up.
 */
class TranslationJobRunner(
    private val chapters: ChapterRepository,
    private val jobs: TranslationJobRepository,
    private val pipeline: InstantTranslationPipeline,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val turnWaitMillis: Long = DEFAULT_TURN_WAIT_MILLIS,
) {
    /** [attempt] is the 0-based run attempt count (WorkManager's runAttemptCount). */
    fun run(
        chapterId: ChapterId,
        sourceLanguageCode: String?,
        targetLanguageCode: String,
        attempt: Int,
        isStopped: () -> Boolean,
    ): JobRunResult {
        val chapter: ChapterTranslation? = try {
            chapters.get(chapterId)
        } catch (e: Exception) {
            return retryOrFail(null, attempt, null, 0, null)
        }
        if (chapter == null) return JobRunResult.FAILURE

        val job: TranslationJob = try {
            jobs.latestForChapter(chapterId) ?: jobs.create(chapterId, 0, clock())
        } catch (e: Exception) {
            return retryOrFail(null, attempt, null, 0, null)
        }
        when (job.status) {
            TranslationStatus.COMPLETED, TranslationStatus.CANCELLED -> return JobRunResult.SUCCESS
            TranslationStatus.FAILED -> return JobRunResult.FAILURE
            else -> Unit
        }

        if (job.status == TranslationStatus.PENDING && !hasTurn(job, isStopped)) return JobRunResult.DEFERRED

        record(job, TranslationStatus.PROCESSING, job.completedChunks, null, null)
        val request = TranslateChapterRequest(
            chapterId,
            chapter.novelId,
            chapter.chapterNumber,
            chapter.originalText,
            sourceLanguageCode,
            targetLanguageCode,
        )
        val outcome = try {
            pipeline.translate(request, clock(), isStopped)
        } catch (e: Exception) {
            return retryOrFail(job, attempt, null, job.completedChunks, null)
        }

        return when (outcome) {
            is InstantTranslationOutcome.Completed -> {
                record(job, TranslationStatus.COMPLETED, outcome.chunkCount, outcome.chunkCount, null)
                JobRunResult.SUCCESS
            }
            is InstantTranslationOutcome.Cancelled -> {
                val current = try { jobs.get(job.jobId) } catch (e: Exception) { null }
                if (current?.status != TranslationStatus.CANCELLED) {
                    record(job, TranslationStatus.PENDING, outcome.completedChunks, outcome.totalChunks, null)
                }
                JobRunResult.RETRY
            }
            is InstantTranslationOutcome.Failed -> {
                if (outcome.reason == InstantTranslationFailure.DUPLICATE_REQUEST) {
                    // Another caller is translating this chapter right now: wait, do not use an attempt.
                    record(job, TranslationStatus.PENDING, job.completedChunks, null, null)
                    JobRunResult.DEFERRED
                } else {
                    val error = errorFor(outcome.reason)
                    if (isRetryable(outcome.reason)) {
                        retryOrFail(job, attempt, error, outcome.completedChunks, outcome.totalChunks)
                    } else {
                        record(job, TranslationStatus.FAILED, outcome.completedChunks, outcome.totalChunks, error)
                        JobRunResult.FAILURE
                    }
                }
            }
        }
    }

    /**
     * True when no other PENDING job outranks [job]. While one does, polls until [turnWaitMillis]
     * has passed (that job is normally about to start) and then gives up. A database error here
     * does not block the job: ordering is best effort.
     */
    private fun hasTurn(job: TranslationJob, isStopped: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + turnWaitMillis * NANOS_PER_MILLI
        while (true) {
            val blocked = try { jobs.hasOutrankingPendingJob(job) } catch (e: Exception) { false }
            if (!blocked) return true
            if (isStopped() || System.nanoTime() >= deadline) return false
            try {
                Thread.sleep(TURN_POLL_MILLIS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
    }

    private fun retryOrFail(job: TranslationJob?, attempt: Int, error: TranslationError?, completed: Int, total: Int?): JobRunResult {
        val last = attempt + 1 >= maxAttempts
        if (job != null) {
            record(job, if (last) TranslationStatus.FAILED else TranslationStatus.PENDING, completed, total, error)
        }
        return if (last) JobRunResult.FAILURE else JobRunResult.RETRY
    }

    /** Best effort: a database error here must not hide the translation result. */
    private fun record(job: TranslationJob, status: TranslationStatus, completed: Int, total: Int?, error: TranslationError?) {
        val newTotal = total?.takeIf { it > 0 }
        val limit = newTotal ?: job.totalChunks
        try {
            jobs.updateProgress(
                job.jobId,
                status,
                completed.coerceIn(0, limit),
                maxOf(clock(), job.createdAtMillis),
                error,
                newTotal,
            )
        } catch (e: Exception) {
            // Ignored on purpose.
        }
    }

    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 5

        /** How long a job waits for an outranking job to start before it is deferred. */
        const val DEFAULT_TURN_WAIT_MILLIS = 2_000L
        private const val TURN_POLL_MILLIS = 100L
        private const val NANOS_PER_MILLI = 1_000_000L

        /** False for problems a retry cannot fix. */
        fun isRetryable(reason: InstantTranslationFailure): Boolean = when (reason) {
            InstantTranslationFailure.EMPTY_INPUT,
            InstantTranslationFailure.UNSUPPORTED_LANGUAGE,
            InstantTranslationFailure.INVALID_CHUNK,
            -> false
            InstantTranslationFailure.PROVIDER_FAILURE,
            InstantTranslationFailure.PARTIAL_FAILURE,
            InstantTranslationFailure.DATABASE_FAILURE,
            InstantTranslationFailure.DUPLICATE_REQUEST,
            -> true
        }

        private fun errorFor(reason: InstantTranslationFailure): TranslationError? = when (reason) {
            InstantTranslationFailure.EMPTY_INPUT -> TranslationError.EMPTY_TEXT
            InstantTranslationFailure.UNSUPPORTED_LANGUAGE -> TranslationError.UNSUPPORTED_SOURCE_LANGUAGE
            InstantTranslationFailure.PROVIDER_FAILURE,
            InstantTranslationFailure.PARTIAL_FAILURE,
            -> TranslationError.PROVIDER_FAILURE
            InstantTranslationFailure.INVALID_CHUNK,
            InstantTranslationFailure.DATABASE_FAILURE,
            InstantTranslationFailure.DUPLICATE_REQUEST,
            -> null
        }
    }
}
