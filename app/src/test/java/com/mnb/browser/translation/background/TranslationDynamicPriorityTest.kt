package com.mnb.browser.translation.background

import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.NovelId
import com.mnb.browser.translation.TargetLanguage
import com.mnb.browser.translation.TranslationPriority
import com.mnb.browser.translation.TranslationStatus
import com.mnb.browser.translation.chunking.ChapterChunkRepository
import com.mnb.browser.translation.chunking.ChunkingConfig
import com.mnb.browser.translation.chunking.ChunkingEngine
import com.mnb.browser.translation.db.ChapterRepository
import com.mnb.browser.translation.db.DbTestBase
import com.mnb.browser.translation.db.PriorityChange
import com.mnb.browser.translation.db.TranslationJobRepository
import com.mnb.browser.translation.db.TranslationRepository
import com.mnb.browser.translation.memory.MemoryRepository
import com.mnb.browser.translation.usecase.InstantTranslationPipeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Dynamic priority and queue position (Phase 04-I9-B2). */
class TranslationDynamicPriorityTest : DbTestBase() {
    private val scheduler = FakeScheduler()
    private lateinit var jobs: TranslationJobRepository
    private lateinit var queue: TranslationQueue
    private lateinit var manager: TranslationPriorityManager

    @Before fun setUp() {
        for (i in 1..8) seedChapter(novelId = "n1", chapterId = "c$i", number = i, text = CHAPTER_TEXT)
        for (i in 1..2) seedChapter(novelId = "n2", chapterId = "x$i", number = i, text = CHAPTER_TEXT)
        jobs = TranslationJobRepository(db)
        queue = TranslationQueue(ChapterRepository(db), TranslationRepository(db), jobs, scheduler, "fake") { 50L }
        manager = TranslationPriorityManager(jobs, lookAhead = 2)
    }

    private fun ch(i: Int) = ChapterId("c$i")
    private fun enqueue(id: String, priority: TranslationPriority = TranslationPriority.NORMAL) {
        assertTrue(queue.enqueue(ChapterId(id), "en", priority = priority) is EnqueueResult.Enqueued)
    }
    private fun enqueueC(i: Int, priority: TranslationPriority = TranslationPriority.NORMAL) = enqueue("c$i", priority)
    private fun priorityOf(id: String) = jobs.latestForChapter(ChapterId(id))!!.priority
    private fun order() = jobs.findPendingInRunOrder().map { it.chapterId.value }
    private fun setStatus(id: String, status: TranslationStatus, completed: Int = 0) {
        val job = jobs.latestForChapter(ChapterId(id))!!
        jobs.updateProgress(job.jobId, status, completed, 60L, null, 3)
    }

    // ---- HIGH / NORMAL / LOW changes -------------------------------------------------------

    @Test fun promoteToHighMovesTheJobToTheFront() {
        enqueueC(1); enqueueC(2); enqueueC(3)
        assertEquals(PriorityChange.UPDATED, manager.promoteToHigh(ch(3)))
        assertEquals(TranslationPriority.HIGH, priorityOf("c3"))
        assertEquals(listOf("c3", "c1", "c2"), order())
    }

    @Test fun demoteToLowMovesTheJobToTheBack() {
        enqueueC(1); enqueueC(2); enqueueC(3)
        assertEquals(PriorityChange.UPDATED, manager.demoteToLow(ch(1)))
        assertEquals(TranslationPriority.LOW, priorityOf("c1"))
        assertEquals(listOf("c2", "c3", "c1"), order())
    }

    @Test fun setNormalLiftsALowJobBackToNormal() {
        enqueueC(1, TranslationPriority.LOW); enqueueC(2, TranslationPriority.HIGH)
        assertEquals(PriorityChange.UPDATED, manager.setNormal(ch(1)))
        assertEquals(TranslationPriority.NORMAL, priorityOf("c1"))
        assertEquals(listOf("c2", "c1"), order())
    }

    @Test fun samePriorityReportsUnchanged() {
        enqueueC(1)
        assertEquals(PriorityChange.UNCHANGED, manager.setNormal(ch(1)))
        assertEquals(TranslationPriority.NORMAL, priorityOf("c1"))
    }

    @Test fun chapterWithoutAJobIsNotFound() {
        assertEquals(PriorityChange.NOT_FOUND, manager.promoteToHigh(ch(1)))
    }

    @Test fun priorityChangeDoesNotTouchProgressFields() {
        enqueueC(1)
        val before = jobs.latestForChapter(ch(1))!!
        manager.promoteToHigh(ch(1))
        val after = jobs.latestForChapter(ch(1))!!
        assertEquals(before.copy(priority = TranslationPriority.HIGH), after)
    }

    // ---- PROCESSING / finished jobs are never touched --------------------------------------

    @Test fun processingJobKeepsItsPriorityAndProgress() {
        enqueueC(1, TranslationPriority.NORMAL)
        setStatus("c1", TranslationStatus.PROCESSING, completed = 1)
        val before = jobs.latestForChapter(ch(1))!!
        assertEquals(PriorityChange.NOT_PENDING, manager.promoteToHigh(ch(1)))
        assertEquals(PriorityChange.NOT_PENDING, manager.demoteToLow(ch(1)))
        assertEquals(before, jobs.latestForChapter(ch(1)))
    }

    @Test fun finishedJobsAreNotChanged() {
        enqueueC(1); enqueueC(2); enqueueC(3)
        setStatus("c1", TranslationStatus.COMPLETED, completed = 3)
        setStatus("c2", TranslationStatus.FAILED)
        setStatus("c3", TranslationStatus.CANCELLED)
        for (i in 1..3) assertEquals(PriorityChange.NOT_PENDING, manager.promoteToHigh(ch(i)))
        for (i in 1..3) assertEquals(TranslationPriority.NORMAL, priorityOf("c$i"))
    }

    @Test fun readerChangeSkipsProcessingJobs() {
        for (i in 1..5) enqueueC(i)
        setStatus("c3", TranslationStatus.PROCESSING, completed = 1)
        val summary = manager.onReaderChapterChanged(NovelId("n1"), 3)
        assertEquals(TranslationPriority.NORMAL, priorityOf("c3"))
        assertEquals(TranslationStatus.PROCESSING, jobs.latestForChapter(ch(3))!!.status)
        assertEquals(1, jobs.latestForChapter(ch(3))!!.completedChunks)
        assertTrue(ch(3) !in summary.changed)
    }

    @Test fun jobThatIsRunningWhenPriorityChangesStillCompletes() {
        enqueueC(1, TranslationPriority.LOW)
        setStatus("c1", TranslationStatus.PROCESSING)
        enqueueC(2, TranslationPriority.HIGH)
        assertEquals(PriorityChange.NOT_PENDING, manager.demoteToLow(ch(1)))
        val provider = FakeProvider()
        val result = runner(provider).run(ch(1), "en", TargetLanguage.MYANMAR.code, 0) { false }
        assertEquals(JobRunResult.SUCCESS, result)
        assertEquals(TranslationStatus.COMPLETED, jobs.latestForChapter(ch(1))!!.status)
        assertEquals(TranslationPriority.LOW, priorityOf("c1"))
    }

    @Test fun deferredJobRunsOnceItIsPromoted() {
        enqueueC(1, TranslationPriority.NORMAL)
        enqueueC(2, TranslationPriority.NORMAL)
        val provider = FakeProvider()
        val r = runner(provider)
        assertEquals(JobRunResult.DEFERRED, r.run(ch(2), "en", TargetLanguage.MYANMAR.code, 0) { false })
        manager.promoteToHigh(ch(2))
        assertEquals(JobRunResult.SUCCESS, r.run(ch(2), "en", TargetLanguage.MYANMAR.code, 0) { false })
        assertEquals(TranslationStatus.COMPLETED, jobs.latestForChapter(ch(2))!!.status)
    }

    // ---- Queue position and ahead count -----------------------------------------------------

    @Test fun positionAndAheadCountFollowRunOrder() {
        enqueueC(1, TranslationPriority.LOW)
        enqueueC(2, TranslationPriority.NORMAL)
        enqueueC(3, TranslationPriority.HIGH)
        enqueueC(4, TranslationPriority.NORMAL)
        // run order: c3, c2, c4, c1
        assertEquals(listOf("c3", "c2", "c4", "c1"), order())
        assertEquals(0, manager.aheadCount(ch(3))); assertEquals(1, manager.queuePosition(ch(3)))
        assertEquals(1, manager.aheadCount(ch(2))); assertEquals(2, manager.queuePosition(ch(2)))
        assertEquals(2, manager.aheadCount(ch(4))); assertEquals(3, manager.queuePosition(ch(4)))
        assertEquals(3, manager.aheadCount(ch(1))); assertEquals(4, manager.queuePosition(ch(1)))
    }

    @Test fun positionUpdatesAfterPriorityChange() {
        enqueueC(1); enqueueC(2); enqueueC(3)
        assertEquals(3, manager.queuePosition(ch(3)))
        manager.promoteToHigh(ch(3))
        assertEquals(1, manager.queuePosition(ch(3)))
        assertEquals(0, manager.aheadCount(ch(3)))
        assertEquals(2, manager.queuePosition(ch(1)))
        assertEquals(3, manager.queuePosition(ch(2)))
        manager.demoteToLow(ch(3))
        assertEquals(3, manager.queuePosition(ch(3)))
    }

    @Test fun positionIsNullWithoutAPendingJob() {
        assertNull(manager.queuePosition(ch(1)))
        assertNull(manager.aheadCount(ch(1)))
        enqueueC(1); enqueueC(2); enqueueC(3)
        setStatus("c1", TranslationStatus.PROCESSING)
        setStatus("c2", TranslationStatus.COMPLETED, 3)
        assertNull(manager.queuePosition(ch(1)))
        assertNull(manager.aheadCount(ch(2)))
    }

    @Test fun processingJobDoesNotCountAsAhead() {
        enqueueC(1); enqueueC(2)
        setStatus("c1", TranslationStatus.PROCESSING)
        assertEquals(0, manager.aheadCount(ch(2)))
        assertEquals(1, manager.queuePosition(ch(2)))
    }

    @Test fun finishedJobLeavesTheLineAndOthersMoveUp() {
        enqueueC(1); enqueueC(2); enqueueC(3)
        assertEquals(2, manager.aheadCount(ch(3)))
        setStatus("c1", TranslationStatus.COMPLETED, 3)
        assertEquals(1, manager.aheadCount(ch(3)))
    }

    @Test fun otherNovelsJobsCountInTheSameQueue() {
        enqueue("x1"); enqueueC(1)
        assertEquals(1, manager.aheadCount(ch(1)))
    }

    // ---- FIFO -------------------------------------------------------------------------------

    @Test fun reprioritisedJobsKeepFifoWithinTheirNewPriority() {
        enqueueC(1, TranslationPriority.LOW)
        enqueueC(2, TranslationPriority.NORMAL)
        enqueueC(3, TranslationPriority.LOW)
        enqueueC(4, TranslationPriority.NORMAL)
        manager.setNormal(ch(3))
        manager.setNormal(ch(1))
        // all NORMAL now: original queue order c1, c2, c3, c4
        assertEquals(listOf("c1", "c2", "c3", "c4"), order())
    }

    @Test fun promotedJobsStayInQueueOrderAmongThemselves() {
        for (i in 1..4) enqueueC(i)
        manager.promoteToHigh(ch(4))
        manager.promoteToHigh(ch(2))
        assertEquals(listOf("c2", "c4", "c1", "c3"), order())
    }

    @Test fun repeatedChangesNeverBreakFifo() {
        for (i in 1..4) enqueueC(i)
        repeat(3) {
            for (i in 1..4) manager.demoteToLow(ch(i))
            for (i in 1..4) manager.setNormal(ch(i))
        }
        assertEquals(listOf("c1", "c2", "c3", "c4"), order())
    }

    // ---- Reader moved to another chapter -----------------------------------------------------

    @Test fun readerChangeGivesHighNormalLowAndLeavesEarlierChapters() {
        for (i in 1..8) enqueueC(i)
        val summary = manager.onReaderChapterChanged(NovelId("n1"), 3)
        assertEquals(TranslationPriority.NORMAL, priorityOf("c1")) // before current: untouched
        assertEquals(TranslationPriority.NORMAL, priorityOf("c2"))
        assertEquals(TranslationPriority.HIGH, priorityOf("c3"))
        assertEquals(TranslationPriority.NORMAL, priorityOf("c4"))
        assertEquals(TranslationPriority.NORMAL, priorityOf("c5"))
        for (i in 6..8) assertEquals(TranslationPriority.LOW, priorityOf("c$i"))
        assertEquals(listOf(ch(3), ch(6), ch(7), ch(8)), summary.changed)
        assertEquals(4, summary.changedCount)
        assertEquals(listOf("c3", "c1", "c2", "c4", "c5", "c6", "c7", "c8"), order())
    }

    @Test fun readerMovingForwardRaisesNewWindowAndLowersTheOldOne() {
        for (i in 1..8) enqueueC(i)
        manager.onReaderChapterChanged(NovelId("n1"), 1) // c1 HIGH, c2-c3 NORMAL, c4-c8 LOW
        manager.onReaderChapterChanged(NovelId("n1"), 6) // c6 HIGH, c7-c8 NORMAL, c1-c5 untouched
        assertEquals(TranslationPriority.HIGH, priorityOf("c6"))
        assertEquals(TranslationPriority.NORMAL, priorityOf("c7"))
        assertEquals(TranslationPriority.NORMAL, priorityOf("c8"))
        assertEquals(TranslationPriority.HIGH, priorityOf("c1"))
        assertEquals(TranslationPriority.LOW, priorityOf("c5"))
    }

    @Test fun readerChangeOnlyAffectsThatNovel() {
        for (i in 1..3) enqueueC(i)
        enqueue("x1"); enqueue("x2")
        manager.onReaderChapterChanged(NovelId("n1"), 1)
        assertEquals(TranslationPriority.NORMAL, priorityOf("x1"))
        assertEquals(TranslationPriority.NORMAL, priorityOf("x2"))
    }

    @Test fun readerChangeSkipsChaptersWithoutAWaitingJob() {
        enqueueC(6) // the only queued job; chapters 3-5 have none
        val summary = manager.onReaderChapterChanged(NovelId("n1"), 3)
        assertEquals(listOf(ch(6)), summary.changed) // 6 - 3 = 3 > lookAhead 2 -> LOW
        assertEquals(TranslationPriority.LOW, priorityOf("c6"))
    }

    @Test fun readerChangeIsRepeatable() {
        for (i in 1..4) enqueueC(i)
        manager.onReaderChapterChanged(NovelId("n1"), 2)
        val second = manager.onReaderChapterChanged(NovelId("n1"), 2)
        assertEquals(0, second.changedCount)
        assertEquals(4, second.unchanged.size)
    }

    // ---- Duplicate jobs ----------------------------------------------------------------------

    @Test fun enqueueingAQueuedChapterAgainDoesNotCreateOrResetAJob() {
        enqueueC(1, TranslationPriority.NORMAL)
        manager.promoteToHigh(ch(1))
        assertTrue(queue.enqueue(ch(1), "en", priority = TranslationPriority.LOW) is EnqueueResult.AlreadyQueued)
        assertEquals(TranslationPriority.HIGH, priorityOf("c1"))
        assertEquals(1, jobs.findPendingInRunOrder().size)
        assertEquals(1, manager.queuePosition(ch(1)))
    }

    @Test fun onlyTheLatestJobOfAChapterIsReprioritised() {
        enqueueC(1)
        setStatus("c1", TranslationStatus.FAILED)
        val old = jobs.latestForChapter(ch(1))!!
        enqueueC(1, TranslationPriority.LOW) // a new job for the same chapter after the failed one
        val latest = jobs.latestForChapter(ch(1))!!
        assertTrue(latest.jobId > old.jobId)
        assertEquals(PriorityChange.UPDATED, manager.promoteToHigh(ch(1)))
        assertEquals(TranslationPriority.HIGH, jobs.get(latest.jobId)!!.priority)
        assertEquals(TranslationPriority.NORMAL, jobs.get(old.jobId)!!.priority)
        assertEquals(TranslationStatus.FAILED, jobs.get(old.jobId)!!.status)
        assertEquals(listOf("c1"), order())
        assertEquals(1, manager.queuePosition(ch(1)))
    }

    @Test fun completedOldJobIsNotCountedInPosition() {
        enqueueC(1); enqueueC(2)
        setStatus("c1", TranslationStatus.COMPLETED, 3)
        assertEquals(1, manager.queuePosition(ch(2)))
    }

    private fun runner(provider: FakeProvider): TranslationJobRunner {
        val chapters = ChapterRepository(db)
        val pipeline = InstantTranslationPipeline(
            provider = provider,
            chapters = chapters,
            chunkRepository = ChapterChunkRepository(db, ChunkingEngine(ChunkingConfig(maxChunkChars = 60))),
            translations = TranslationRepository(db),
            memory = MemoryRepository(db),
        )
        return TranslationJobRunner(chapters, jobs, pipeline, { 100L }, 3, 0L)
    }
}
