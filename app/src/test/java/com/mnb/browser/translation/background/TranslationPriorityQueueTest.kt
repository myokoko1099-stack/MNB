package com.mnb.browser.translation.background

import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.TranslationPriority
import com.mnb.browser.translation.TranslationStatus
import com.mnb.browser.translation.db.ChapterRepository
import com.mnb.browser.translation.db.DbTestBase
import com.mnb.browser.translation.db.TranslationJobRepository
import com.mnb.browser.translation.db.TranslationRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Priority ordering and FIFO of the waiting jobs (Phase 04-I9-B1). */
class TranslationPriorityQueueTest : DbTestBase() {
    private val scheduler = FakeScheduler()
    private lateinit var jobs: TranslationJobRepository
    private lateinit var queue: TranslationQueue

    @Before fun setUp() {
        for (i in 1..5) seedChapter(chapterId = "c$i", number = i, text = CHAPTER_TEXT)
        jobs = TranslationJobRepository(db)
        queue = newQueue { 50L }
    }

    private fun newQueue(clock: () -> Long) =
        TranslationQueue(ChapterRepository(db), TranslationRepository(db), jobs, scheduler, "fake", clock)

    private fun chapter(i: Int) = ChapterId("c$i")

    private fun enqueue(i: Int, priority: TranslationPriority? = null, q: TranslationQueue = queue) {
        val result = if (priority == null) q.enqueue(chapter(i), "en") else q.enqueue(chapter(i), "en", priority = priority)
        assertTrue(result is EnqueueResult.Enqueued)
    }

    private fun runOrder() = jobs.findPendingInRunOrder().map { it.chapterId.value }

    @Test fun jobWithoutAPriorityIsNormal() {
        enqueue(1)
        assertEquals(TranslationPriority.NORMAL, jobs.latestForChapter(chapter(1))!!.priority)
    }

    @Test fun givenPriorityIsStored() {
        enqueue(1, TranslationPriority.HIGH)
        enqueue(2, TranslationPriority.LOW)
        assertEquals(TranslationPriority.HIGH, jobs.latestForChapter(chapter(1))!!.priority)
        assertEquals(TranslationPriority.LOW, jobs.latestForChapter(chapter(2))!!.priority)
    }

    @Test fun highRunsBeforeNormalAndNormalBeforeLowWhateverTheQueueOrder() {
        enqueue(1, TranslationPriority.LOW)
        enqueue(2, TranslationPriority.NORMAL)
        enqueue(3, TranslationPriority.HIGH)
        assertEquals(listOf("c3", "c2", "c1"), runOrder())
    }

    @Test fun samePriorityKeepsFirstInFirstOut() {
        enqueue(3, TranslationPriority.HIGH)
        enqueue(1, TranslationPriority.HIGH)
        enqueue(2, TranslationPriority.HIGH)
        assertEquals(listOf("c3", "c1", "c2"), runOrder())
    }

    @Test fun defaultPriorityJobsKeepFirstInFirstOut() {
        enqueue(2)
        enqueue(1)
        enqueue(3)
        assertEquals(listOf("c2", "c1", "c3"), runOrder())
    }

    @Test fun mixedPrioritiesAreOrderedByPriorityThenFifo() {
        enqueue(1, TranslationPriority.LOW)
        enqueue(2, TranslationPriority.HIGH)
        enqueue(3, TranslationPriority.NORMAL)
        enqueue(4, TranslationPriority.HIGH)
        enqueue(5, TranslationPriority.LOW)
        assertEquals(listOf("c2", "c4", "c3", "c1", "c5"), runOrder())
    }

    @Test fun fifoDoesNotDependOnTheClock() {
        var now = 100L
        val backwards = newQueue { now-- } // each job gets an earlier createdAtMillis than the one before
        enqueue(1, q = backwards)
        enqueue(2, q = backwards)
        enqueue(3, q = backwards)
        assertEquals(listOf("c1", "c2", "c3"), runOrder())
    }

    @Test fun priorityIsKeptWhenProgressIsUpdated() {
        enqueue(1, TranslationPriority.HIGH)
        val job = jobs.latestForChapter(chapter(1))!!
        jobs.updateProgress(job.jobId, TranslationStatus.PROCESSING, 0, 60L)
        jobs.updateProgress(job.jobId, TranslationStatus.PENDING, 0, 70L)
        assertEquals(TranslationPriority.HIGH, jobs.latestForChapter(chapter(1))!!.priority)
    }

    @Test fun startedJobsLeaveTheRunOrder() {
        enqueue(1, TranslationPriority.HIGH)
        enqueue(2, TranslationPriority.NORMAL)
        jobs.updateProgress(jobs.latestForChapter(chapter(1))!!.jobId, TranslationStatus.PROCESSING, 0, 60L)
        assertEquals(listOf("c2"), runOrder())
    }

    @Test fun cancelledJobsAreNotInTheRunOrder() {
        enqueue(1, TranslationPriority.HIGH)
        enqueue(2, TranslationPriority.LOW)
        assertTrue(queue.cancel(chapter(1)))
        assertEquals(listOf("c2"), runOrder())
    }

    @Test fun queuingAChapterAgainKeepsTheFirstPriority() {
        enqueue(1, TranslationPriority.LOW)
        val again = queue.enqueue(chapter(1), "en", priority = TranslationPriority.HIGH)
        assertTrue(again is EnqueueResult.AlreadyQueued)
        assertEquals(TranslationPriority.LOW, jobs.latestForChapter(chapter(1))!!.priority)
        assertEquals(1, runOrder().size)
    }

    @Test fun finishedJobGetsANewPriorityAndANewPlaceInLine() {
        enqueue(1, TranslationPriority.LOW)
        enqueue(2, TranslationPriority.LOW)
        jobs.updateProgress(jobs.latestForChapter(chapter(1))!!.jobId, TranslationStatus.FAILED, 0, 60L)
        scheduler.dropWork(chapter(1))
        enqueue(1, TranslationPriority.LOW)
        assertEquals(listOf("c2", "c1"), runOrder())
    }
}
