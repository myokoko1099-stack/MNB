package com.mnb.browser.translation.db

import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.NovelId
import com.mnb.browser.translation.TranslationError
import com.mnb.browser.translation.TranslationJob
import com.mnb.browser.translation.TranslationPriority
import com.mnb.browser.translation.TranslationStatus
import java.util.concurrent.Callable

/** Stores translation job progress so unfinished work can be resumed. Blocking: call off the main thread. */
class TranslationJobRepository(private val db: MnbDatabase) {
    private val dao = db.translationJobDao()

    /**
     * Creates a PENDING job and returns it with its generated id. Throws IllegalArgumentException
     * for invalid values and SQLiteConstraintException when the chapter does not exist.
     * [priority] (I9-B1) is the starting priority; only [changePendingPriority] (I9-B2) can change it later.
     */
    fun create(
        chapterId: ChapterId,
        totalChunks: Int,
        nowMillis: Long,
        priority: TranslationPriority = TranslationPriority.NORMAL,
    ): TranslationJob {
        val draft = TranslationJob(
            jobId = 0L,
            chapterId = chapterId,
            totalChunks = totalChunks,
            createdAtMillis = nowMillis,
            priority = priority,
        )
        return draft.copy(jobId = dao.insert(draft.toEntity()))
    }

    fun get(jobId: Long): TranslationJob? = dao.get(jobId)?.toModel()

    fun latestForChapter(chapterId: ChapterId): TranslationJob? = dao.latestForChapter(chapterId.value)?.toModel()

    /** PENDING or PROCESSING jobs, oldest first: the work to resume after the app restarts. */
    fun findUnfinished(): List<TranslationJob> = dao.unfinished().map { it.toModel() }

    /** PENDING jobs in the order they should start: higher priority first, then FIFO (I9-B1). */
    fun findPendingInRunOrder(): List<TranslationJob> = dao.pendingInRunOrder().map { it.toModel() }

    /** True while another PENDING job must start before [job] (higher priority, or same priority and queued earlier). */
    fun hasOutrankingPendingJob(job: TranslationJob): Boolean =
        dao.countOutranking(job.jobId, job.priority.rank) > 0

    /**
     * Changes the priority of [chapterId]'s latest job (I9-B2), but only while it is PENDING. A
     * PROCESSING or finished job is left exactly as it is, so work in progress is never disturbed.
     * Only the priority column is written: the job keeps its jobId, so FIFO order among jobs of the
     * same priority stays the order in which the jobs were queued.
     */
    fun changePendingPriority(chapterId: ChapterId, priority: TranslationPriority): PriorityChange =
        db.runInTransaction(
            Callable<PriorityChange> {
                val job = dao.latestForChapter(chapterId.value)?.toModel() ?: return@Callable PriorityChange.NOT_FOUND
                when {
                    job.status != TranslationStatus.PENDING -> PriorityChange.NOT_PENDING
                    job.priority == priority -> PriorityChange.UNCHANGED
                    dao.updatePendingPriority(job.jobId, priority.rank) == 1 -> PriorityChange.UPDATED
                    else -> PriorityChange.NOT_PENDING
                }
            },
        )

    /** PENDING jobs of [novelId] with their chapter numbers, in chapter order (I9-B2). */
    fun findPendingForNovel(novelId: NovelId): List<PendingJob> =
        dao.pendingForNovel(novelId.value).map { PendingJob(it.job.toModel(), it.chapterNumber) }

    /**
     * How many PENDING jobs will start before [chapterId]'s latest job (I9-B2): higher priority, or
     * the same priority and queued earlier. PROCESSING jobs are not counted (they have already
     * started and do not hold a pending job back). Null when the chapter has no PENDING job.
     */
    fun aheadCount(chapterId: ChapterId): Int? {
        val job = latestForChapter(chapterId)?.takeIf { it.status == TranslationStatus.PENDING } ?: return null
        return dao.countOutranking(job.jobId, job.priority.rank)
    }

    /**
     * Updates a job's status and progress. Returns false when the job does not exist. Throws
     * IllegalArgumentException when [completedChunks] is outside 0..totalChunks. [totalChunks]
     * (I9-A) sets the chunk count once it is known; null keeps the stored value. The job's
     * priority is never changed here (see [changePendingPriority]).
     */
    fun updateProgress(
        jobId: Long,
        status: TranslationStatus,
        completedChunks: Int,
        nowMillis: Long,
        errorCode: TranslationError? = null,
        totalChunks: Int? = null,
    ): Boolean = db.runInTransaction(
        Callable<Boolean> {
            val existing = dao.get(jobId) ?: return@Callable false
            val base = existing.toModel()
            val updated = base.copy(
                totalChunks = totalChunks ?: base.totalChunks,
                status = status,
                completedChunks = completedChunks,
                errorCode = errorCode,
                updatedAtMillis = nowMillis,
            )
            dao.update(updated.toEntity())
            true
        },
    )
}

/** Outcome of [TranslationJobRepository.changePendingPriority]. */
enum class PriorityChange {
    /** The PENDING job now has the new priority. */
    UPDATED,

    /** The PENDING job already had that priority. */
    UNCHANGED,

    /** The job is PROCESSING or finished, so it was left untouched. */
    NOT_PENDING,

    /** The chapter has no job. */
    NOT_FOUND,
}

/** A PENDING job and the number of its chapter. */
data class PendingJob(val job: TranslationJob, val chapterNumber: Int)
