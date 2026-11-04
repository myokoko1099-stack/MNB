package com.mnb.browser.translation.background

import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.TargetLanguage
import com.mnb.browser.translation.TranslationPriority
import com.mnb.browser.translation.TranslationProvider
import com.mnb.browser.translation.TranslationStatus
import com.mnb.browser.translation.chunking.ChapterChunkRepository
import com.mnb.browser.translation.chunking.ChunkingConfig
import com.mnb.browser.translation.chunking.ChunkingEngine
import com.mnb.browser.translation.db.ChapterRepository
import com.mnb.browser.translation.db.DbTestBase
import com.mnb.browser.translation.db.TranslationJobRepository
import com.mnb.browser.translation.db.TranslationRepository
import com.mnb.browser.translation.memory.MemoryRepository
import com.mnb.browser.translation.usecase.InstantTranslationPipeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The runner starts a waiting job only when no other waiting job outranks it (Phase 04-I9-B1). */
class TranslationJobRunnerPriorityTest : DbTestBase() {
    private val c1 = ChapterId("c1")
    private val c2 = ChapterId("c2")
    private val target = TargetLanguage.MYANMAR.code
    private lateinit var jobs: TranslationJobRepository

    @Before fun setUp() {
        seedChapter(chapterId = "c1", number = 1, text = CHAPTER_TEXT)
        seedChapter(chapterId = "c2", number = 2, text = CHAPTER_TEXT)
        jobs = TranslationJobRepository(db)
    }

    /** Same wiring as buildRunner, but never waits for an outranking job: it is deferred at once. */
    private fun runner(provider: TranslationProvider): TranslationJobRunner {
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

    private fun TranslationJobRunner.go(chapter: ChapterId) = run(chapter, "en", target, 0) { false }

    private fun status(chapter: ChapterId) = jobs.latestForChapter(chapter)!!.status

    @Test fun lowerPriorityJobWaitsWhileAHigherOneIsPending() {
        jobs.create(c1, 0, 1L, TranslationPriority.LOW)
        jobs.create(c2, 0, 2L, TranslationPriority.HIGH)
        val provider = FakeProvider()
        assertEquals(JobRunResult.DEFERRED, runner(provider).go(c1))
        assertEquals(TranslationStatus.PENDING, status(c1))
        assertTrue(provider.requests.isEmpty())
    }

    @Test fun higherPriorityJobIsNeverHeldBackByALowerOne() {
        jobs.create(c1, 0, 1L, TranslationPriority.LOW)
        jobs.create(c2, 0, 2L, TranslationPriority.HIGH)
        assertEquals(JobRunResult.SUCCESS, runner(FakeProvider()).go(c2))
        assertEquals(TranslationStatus.COMPLETED, status(c2))
    }

    @Test fun lowerPriorityJobRunsOnceTheHigherOneHasStarted() {
        jobs.create(c1, 0, 1L, TranslationPriority.LOW)
        val high = jobs.create(c2, 0, 2L, TranslationPriority.HIGH)
        val provider = FakeProvider()
        val runner = runner(provider)
        assertEquals(JobRunResult.DEFERRED, runner.go(c1))
        jobs.updateProgress(high.jobId, TranslationStatus.PROCESSING, 0, 3L)
        assertEquals(JobRunResult.SUCCESS, runner.go(c1))
        assertEquals(TranslationStatus.COMPLETED, status(c1))
        assertEquals(3, provider.requests.size)
    }

    @Test fun samePriorityJobQueuedLaterWaitsForTheEarlierOne() {
        jobs.create(c1, 0, 1L)
        jobs.create(c2, 0, 2L)
        val runner = runner(FakeProvider())
        assertEquals(JobRunResult.DEFERRED, runner.go(c2)) // c1 was queued first
        assertEquals(JobRunResult.SUCCESS, runner.go(c1))
        assertEquals(JobRunResult.SUCCESS, runner.go(c2))
        assertEquals(TranslationStatus.COMPLETED, status(c2))
    }

    @Test fun fifoIsKeptEvenWhenTheEarlierJobHasALaterTimestamp() {
        jobs.create(c1, 0, 500L) // queued first, but its clock value is later
        jobs.create(c2, 0, 100L)
        assertEquals(JobRunResult.DEFERRED, runner(FakeProvider()).go(c2))
    }

    @Test fun jobThatIsAlreadyProcessingIsNotHeldBack() {
        val low = jobs.create(c1, 0, 1L, TranslationPriority.LOW)
        jobs.create(c2, 0, 2L, TranslationPriority.HIGH)
        jobs.updateProgress(low.jobId, TranslationStatus.PROCESSING, 0, 3L) // resumed after a restart
        assertEquals(JobRunResult.SUCCESS, runner(FakeProvider()).go(c1))
    }

    @Test fun finishedHigherPriorityJobDoesNotBlock() {
        jobs.create(c1, 0, 1L, TranslationPriority.LOW)
        val high = jobs.create(c2, 0, 2L, TranslationPriority.HIGH)
        jobs.updateProgress(high.jobId, TranslationStatus.FAILED, 0, 3L)
        assertEquals(JobRunResult.SUCCESS, runner(FakeProvider()).go(c1))
    }

    @Test fun stoppedWorkIsDeferredWithoutWaiting() {
        jobs.create(c1, 0, 1L, TranslationPriority.LOW)
        jobs.create(c2, 0, 2L, TranslationPriority.HIGH)
        val chapters = ChapterRepository(db)
        val pipeline = InstantTranslationPipeline(
            provider = FakeProvider(),
            chapters = chapters,
            chunkRepository = ChapterChunkRepository(db, ChunkingEngine(ChunkingConfig(maxChunkChars = 60))),
            translations = TranslationRepository(db),
            memory = MemoryRepository(db),
        )
        val patient = TranslationJobRunner(chapters, jobs, pipeline, { 100L }, 3, 60_000L)
        assertEquals(JobRunResult.DEFERRED, patient.run(c1, "en", target, 0) { true })
    }
}
