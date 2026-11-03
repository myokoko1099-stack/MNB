package com.mnb.browser.translation.db

import android.database.sqlite.SQLiteConstraintException
import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.NovelId
import com.mnb.browser.translation.TranslationError
import com.mnb.browser.translation.TranslationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationJobRepositoryTest : DbTestBase() {
    private val repo get() = TranslationJobRepository(db)
    private val c1 = ChapterId("c1")

    @Test fun createdJobIsPendingAndReadable() {
        seedChapter()
        val job = repo.create(c1, totalChunks = 4, nowMillis = 100L)
        assertTrue(job.jobId > 0)
        assertEquals(TranslationStatus.PENDING, job.status)
        assertEquals(0, job.completedChunks)
        assertEquals(job, repo.get(job.jobId))
        assertEquals(job, repo.latestForChapter(c1))
    }

    @Test fun unknownJobIsNull() {
        assertNull(repo.get(12345L))
    }

    @Test fun jobNeedsAnExistingChapter() {
        assertThrows(SQLiteConstraintException::class.java) { repo.create(ChapterId("missing"), 1, 1L) }
    }

    @Test fun invalidJobsAreRejected() {
        seedChapter()
        assertThrows(IllegalArgumentException::class.java) { repo.create(c1, -1, 1L) }
        assertThrows(IllegalArgumentException::class.java) { repo.create(c1, 1, -1L) }
    }

    @Test fun progressIsUpdatedAndPersisted() {
        seedChapter()
        val job = repo.create(c1, 4, 100L)
        assertTrue(repo.updateProgress(job.jobId, TranslationStatus.PROCESSING, 2, nowMillis = 200L))
        val stored = repo.get(job.jobId)!!
        assertEquals(TranslationStatus.PROCESSING, stored.status)
        assertEquals(2, stored.completedChunks)
        assertEquals(200L, stored.updatedAtMillis)
        assertEquals(100L, stored.createdAtMillis)
    }

    @Test fun failedJobKeepsItsErrorCode() {
        seedChapter()
        val job = repo.create(c1, 2, 1L)
        repo.updateProgress(job.jobId, TranslationStatus.FAILED, 1, 2L, TranslationError.PROVIDER_FAILURE)
        assertEquals(TranslationError.PROVIDER_FAILURE, repo.get(job.jobId)!!.errorCode)
    }

    @Test fun updatingAMissingJobReturnsFalse() {
        assertFalse(repo.updateProgress(999L, TranslationStatus.PROCESSING, 0, 1L))
    }

    @Test fun progressBeyondTotalIsRejectedAndNothingChanges() {
        seedChapter()
        val job = repo.create(c1, 2, 1L)
        assertThrows(IllegalArgumentException::class.java) {
            repo.updateProgress(job.jobId, TranslationStatus.PROCESSING, 3, 2L)
        }
        assertEquals(TranslationStatus.PENDING, repo.get(job.jobId)!!.status)
    }

    @Test fun onlyPendingAndProcessingJobsAreResumable() {
        seedChapter(chapterId = "c1", number = 1)
        seedChapter(chapterId = "c2", number = 2)
        seedChapter(chapterId = "c3", number = 3)
        val pending = repo.create(ChapterId("c1"), 2, 1L)
        val processing = repo.create(ChapterId("c2"), 2, 2L)
        val done = repo.create(ChapterId("c3"), 2, 3L)
        repo.updateProgress(processing.jobId, TranslationStatus.PROCESSING, 1, 4L)
        repo.updateProgress(done.jobId, TranslationStatus.COMPLETED, 2, 5L)

        assertEquals(listOf(pending.jobId, processing.jobId), repo.findUnfinished().map { it.jobId })
    }

    @Test fun unfinishedJobSurvivesReopeningTheRepository() {
        seedChapter()
        val job = repo.create(c1, 3, 1L)
        repo.updateProgress(job.jobId, TranslationStatus.PROCESSING, 1, 2L)
        // A new repository over the same database stands in for the app being restarted.
        val resumed = TranslationJobRepository(db).findUnfinished().single()
        assertEquals(1, resumed.completedChunks)
        assertEquals(TranslationStatus.PROCESSING, resumed.status)
    }

    @Test fun changedChapterTextCancelsUnfinishedJobsOnly() {
        seedChapter(text = "Old")
        val open = repo.create(c1, 2, 1L)
        val finished = repo.create(c1, 2, 2L)
        repo.updateProgress(finished.jobId, TranslationStatus.COMPLETED, 2, 3L)

        ChapterRepository(db).saveOriginal(c1, NovelId("n1"), 1, "New", 40L)

        assertEquals(TranslationStatus.CANCELLED, repo.get(open.jobId)!!.status)
        assertEquals(TranslationStatus.COMPLETED, repo.get(finished.jobId)!!.status)
        assertTrue(repo.findUnfinished().isEmpty())
    }

    @Test fun latestForChapterReturnsTheNewestJob() {
        seedChapter()
        repo.create(c1, 1, 1L)
        val second = repo.create(c1, 1, 2L)
        assertEquals(second.jobId, repo.latestForChapter(c1)!!.jobId)
    }
}
