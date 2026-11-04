package com.mnb.browser.translation.db

import com.mnb.browser.translation.ChapterId
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
     * [priority] (I9-B1) is fixed for the life of the job.
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
     * Updates a job's status and progress. Returns false when the job does not exist. Throws
     * IllegalArgumentException when [completedChunks] is outside 0..totalChunks. [totalChunks]
     * (I9-A) sets the chunk count once it is known; null keeps the stored value. The job's
     * priority is never changed here.
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
