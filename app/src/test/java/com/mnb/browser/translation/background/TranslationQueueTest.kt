package com.mnb.browser.translation.background

import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.TranslationMetadata
import com.mnb.browser.translation.TranslationStatus
import com.mnb.browser.translation.db.ChapterRepository
import com.mnb.browser.translation.db.DbTestBase
import com.mnb.browser.translation.db.TranslationJobRepository
import com.mnb.browser.translation.db.TranslationRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TranslationQueueTest : DbTestBase() {
    private val c1 = ChapterId("c1")
    private val scheduler = FakeScheduler()
    private lateinit var jobs: TranslationJobRepository
    private lateinit var queue: TranslationQueue

    @Before fun setUp() {
        seedChapter(text = CHAPTER_TEXT)
        jobs = TranslationJobRepository(db)
        queue = TranslationQueue(ChapterRepository(db), TranslationRepository(db), jobs, scheduler, "fake") { 50L }
    }

    private fun saveCompleted(providerId: String) = TranslationRepository(db).saveTranslation(
        c1, "done", TranslationStatus.COMPLETED,
        TranslationMetadata(providerId = providerId, createdAtMillis = 10L, translationVersion = 1, isMock = false),
    )

    @Test fun enqueueCreatesPendingJobAndSchedulesWork() {
        val result = queue.enqueue(c1, "en") as EnqueueResult.Enqueued
        assertEquals(TranslationStatus.PENDING, result.job.status)
        assertEquals(listOf(c1), scheduler.scheduled)
        assertEquals(result.job, jobs.latestForChapter(c1))
    }

    @Test fun duplicateEnqueueWhileWorkIsLiveAddsNothing() {
        val first = queue.enqueue(c1, "en") as EnqueueResult.Enqueued
        val second = queue.enqueue(c1, "en") as EnqueueResult.AlreadyQueued
        assertEquals(first.job.jobId, second.job.jobId)
        assertEquals(1, scheduler.scheduled.size)
        assertEquals(1, jobs.findUnfinished().size)
    }

    @Test fun completedTranslationBySameProviderIsNotQueued() {
        saveCompleted("fake")
        assertTrue(queue.enqueue(c1, "en") is EnqueueResult.AlreadyTranslated)
        assertTrue(scheduler.scheduled.isEmpty())
        assertEquals(null, jobs.latestForChapter(c1))
    }

    @Test fun translationByAnotherProviderIsQueued() {
        saveCompleted("other")
        assertTrue(queue.enqueue(c1, "en") is EnqueueResult.Enqueued)
    }

    @Test fun unknownChapterIsReported() {
        assertEquals(EnqueueResult.ChapterNotFound, queue.enqueue(ChapterId("nope"), "en"))
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test fun finishedJobAllowsANewJob() {
        val first = queue.enqueue(c1, "en") as EnqueueResult.Enqueued
        jobs.updateProgress(first.job.jobId, TranslationStatus.FAILED, 0, 60L)
        val second = queue.enqueue(c1, "en") as EnqueueResult.Enqueued
        assertTrue(second.job.jobId != first.job.jobId)
    }

    @Test fun orphanedUnfinishedJobIsRescheduledWithoutANewRow() {
        val first = queue.enqueue(c1, "en") as EnqueueResult.Enqueued
        scheduler.dropWork(c1)
        val again = queue.enqueue(c1, "en") as EnqueueResult.Enqueued
        assertEquals(first.job.jobId, again.job.jobId)
        assertEquals(2, scheduler.scheduled.size)
        assertEquals(1, jobs.findUnfinished().size)
    }

    @Test fun recoverMarksJobsWithoutLiveWorkAsFailedAndKeepsLiveOnes() {
        seedChapter(chapterId = "c2", number = 2, text = "Another chapter.\n\nWith two paragraphs.")
        queue.enqueue(c1, "en")
        queue.enqueue(ChapterId("c2"), "en")
        scheduler.dropWork(c1)
        assertEquals(1, queue.recoverUnfinished())
        assertEquals(TranslationStatus.FAILED, jobs.latestForChapter(c1)!!.status)
        assertEquals(TranslationStatus.PENDING, jobs.latestForChapter(ChapterId("c2"))!!.status)
        assertEquals(0, queue.recoverUnfinished())
    }

    @Test fun cancelStopsWorkAndMarksJobCancelled() {
        queue.enqueue(c1, "en")
        assertTrue(queue.cancel(c1))
        assertFalse(scheduler.isScheduled(c1))
        assertEquals(TranslationStatus.CANCELLED, jobs.latestForChapter(c1)!!.status)
        assertFalse(queue.cancel(c1))
    }
}
