package com.mnb.browser.translation.background

import com.mnb.browser.reader.translation.ReaderInstantTranslationController
import com.mnb.browser.reader.translation.ReaderTranslationState
import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.TargetLanguage
import com.mnb.browser.translation.TranslationError
import com.mnb.browser.translation.TranslationProvider
import com.mnb.browser.translation.TranslationResult
import com.mnb.browser.translation.TranslationStatus
import com.mnb.browser.translation.chunking.ChapterChunkRepository
import com.mnb.browser.translation.chunking.ChunkingConfig
import com.mnb.browser.translation.chunking.ChunkingEngine
import com.mnb.browser.translation.db.ChapterRepository
import com.mnb.browser.translation.db.DbTestBase
import com.mnb.browser.translation.db.TranslationJobRepository
import com.mnb.browser.translation.db.TranslationRepository
import com.mnb.browser.translation.memory.MemoryRepository
import com.mnb.browser.translation.usecase.ChapterTranslationLock
import com.mnb.browser.translation.usecase.InstantTranslationFailure
import com.mnb.browser.translation.usecase.InstantTranslationOutcome
import com.mnb.browser.translation.usecase.InstantTranslationPipeline
import com.mnb.browser.translation.usecase.TranslateChapterRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Phase 04-I10-D: instant and background runs of one chapter never execute at the same time. */
class InstantBackgroundConflictTest : DbTestBase() {
    private val c1 = ChapterId("c1")
    private val target = TargetLanguage.MYANMAR.code
    private lateinit var jobs: TranslationJobRepository

    @Before fun setUp() {
        seedChapter(text = CHAPTER_TEXT)
        jobs = TranslationJobRepository(db)
    }

    private fun pipeline(provider: TranslationProvider, lock: ChapterTranslationLock?): InstantTranslationPipeline {
        val chunkRepository = ChapterChunkRepository(db, ChunkingEngine(ChunkingConfig(maxChunkChars = 60)))
        return if (lock == null) {
            InstantTranslationPipeline(provider, ChapterRepository(db), chunkRepository, TranslationRepository(db), MemoryRepository(db))
        } else {
            InstantTranslationPipeline(
                provider = provider,
                chapters = ChapterRepository(db),
                chunkRepository = chunkRepository,
                translations = TranslationRepository(db),
                memory = MemoryRepository(db),
                lock = lock,
            )
        }
    }

    private fun runner(provider: TranslationProvider, lock: ChapterTranslationLock?, maxAttempts: Int = 3) =
        TranslationJobRunner(ChapterRepository(db), jobs, pipeline(provider, lock), { 100L }, maxAttempts)

    private fun request(): TranslateChapterRequest {
        val chapter = ChapterRepository(db).get(c1)!!
        return TranslateChapterRequest(c1, chapter.novelId, chapter.chapterNumber, chapter.originalText, "en", target)
    }

    private fun TranslationJobRunner.go(attempt: Int = 0, stopped: () -> Boolean = { false }) = run(c1, "en", target, attempt, stopped)

    // ---- instant holds the chapter -> background defers -----------------------------------

    @Test fun backgroundDefersWhileInstantHoldsTheChapter() {
        val lock = ChapterTranslationLock()
        assertTrue(lock.tryAcquire(c1)) // an instant translation is running
        val provider = FakeProvider()

        assertEquals(JobRunResult.DEFERRED, runner(provider, lock).go())

        assertTrue(provider.requests.isEmpty())
        assertEquals(TranslationStatus.PENDING, jobs.latestForChapter(c1)!!.status)
        assertTrue(lock.isHeld(c1)) // the background run did not release the instant run's lock
    }

    @Test fun deferringDoesNotUseUpARetryAttempt() {
        val lock = ChapterTranslationLock()
        lock.tryAcquire(c1)
        val strict = runner(FakeProvider(), lock, maxAttempts = 1) // attempt 0 would be the last one
        assertEquals(JobRunResult.DEFERRED, strict.go(attempt = 0))
        assertEquals(TranslationStatus.PENDING, jobs.latestForChapter(c1)!!.status)
    }

    @Test fun deferredJobRunsAfterInstantFinishedAndTranslatesOnlyOnce() {
        val lock = ChapterTranslationLock()
        val instantProvider = FakeProvider()
        val backgroundProvider = FakeProvider()

        lock.tryAcquire(c1)
        assertEquals(JobRunResult.DEFERRED, runner(backgroundProvider, lock).go())
        lock.release(c1)

        val instant = pipeline(instantProvider, lock).translate(request(), 100L)
        assertTrue(instant is InstantTranslationOutcome.Completed)
        assertEquals(JobRunResult.SUCCESS, runner(backgroundProvider, lock).go(attempt = 1))

        assertEquals(3, instantProvider.requests.size)
        assertTrue(backgroundProvider.requests.isEmpty()) // the stored translation was reused
        assertEquals(TranslationStatus.COMPLETED, TranslationRepository(db).getTranslation(c1)!!.status)
    }

    // ---- background holds the chapter -> instant is rejected ------------------------------

    @Test fun instantIsRejectedWhileBackgroundHoldsTheChapter() {
        val lock = ChapterTranslationLock()
        assertTrue(lock.tryAcquire(c1)) // a background run is working
        val provider = FakeProvider()

        val outcome = pipeline(provider, lock).translate(request(), 100L)

        assertTrue(outcome is InstantTranslationOutcome.Failed)
        assertEquals(InstantTranslationFailure.DUPLICATE_REQUEST, (outcome as InstantTranslationOutcome.Failed).reason)
        assertTrue(provider.requests.isEmpty())
        assertEquals(null, TranslationRepository(db).getTranslation(c1)) // nothing was written
        assertTrue(lock.isHeld(c1))
    }

    @Test fun readerKeepsShowingProcessingForABusyChapter() {
        val busy = InstantTranslationOutcome.Failed(InstantTranslationFailure.DUPLICATE_REQUEST, "busy", 0, 0, emptyList())
        assertTrue(ReaderInstantTranslationController.mapOutcome(busy) is ReaderTranslationState.Processing)
    }

    // ---- independently built pipelines share the default lock ------------------------------

    @Test fun defaultWiringSharesOneLockAcrossSeparatelyBuiltPipelines() {
        val provider = FakeProvider()
        assertTrue(ChapterTranslationLock.shared.tryAcquire(c1))
        try {
            assertEquals(JobRunResult.DEFERRED, runner(provider, null).go()) // default lock, like the worker
            val outcome = pipeline(provider, null).translate(request(), 100L) // default lock, like the reader
            assertEquals(InstantTranslationFailure.DUPLICATE_REQUEST, (outcome as InstantTranslationOutcome.Failed).reason)
            assertTrue(provider.requests.isEmpty())
        } finally {
            ChapterTranslationLock.shared.release(c1)
        }
        assertFalse(ChapterTranslationLock.shared.isHeld(c1))
    }

    // ---- the lock is released on every exit -------------------------------------------------

    @Test fun lockIsReleasedAfterSuccess() {
        val lock = ChapterTranslationLock()
        assertTrue(pipeline(FakeProvider(), lock).translate(request(), 100L) is InstantTranslationOutcome.Completed)
        assertFalse(lock.isHeld(c1))
    }

    @Test fun lockIsReleasedAfterFailure() {
        val lock = ChapterTranslationLock()
        val failing = FakeProvider { TranslationResult.Failure(TranslationError.PROVIDER_FAILURE, "fake", "offline") }
        assertTrue(pipeline(failing, lock).translate(request(), 100L) is InstantTranslationOutcome.Failed)
        assertFalse(lock.isHeld(c1))
    }

    @Test fun lockIsReleasedAfterCancellation() {
        val lock = ChapterTranslationLock()
        val outcome = pipeline(FakeProvider(), lock).translate(request(), 100L) { true }
        assertTrue(outcome is InstantTranslationOutcome.Cancelled)
        assertFalse(lock.isHeld(c1))
    }

    @Test fun lockIsReleasedWhenTheRunnerIsStoppedAndRetriedLater() {
        val lock = ChapterTranslationLock()
        val provider = FakeProvider()
        assertEquals(JobRunResult.RETRY, runner(provider, lock).go { provider.requests.size >= 1 })
        assertFalse(lock.isHeld(c1))
        assertEquals(JobRunResult.SUCCESS, runner(provider, lock).go(attempt = 1))
        assertFalse(lock.isHeld(c1))
        assertEquals(3, provider.requests.size) // finished chunks were reused, nothing translated twice
    }

    @Test fun lockIsReleasedWhenTheProviderThrows() {
        val lock = ChapterTranslationLock()
        val throwing = FakeProvider { throw IllegalStateException("boom") }
        try {
            pipeline(throwing, lock).translate(request(), 100L)
        } catch (e: Exception) {
            // Whether the pipeline reports or rethrows is not the point here; the lock must be free.
        }
        assertFalse(lock.isHeld(c1))
    }

    // ---- different chapters stay independent ------------------------------------------------

    @Test fun differentChaptersDoNotBlockEachOther() {
        val lock = ChapterTranslationLock()
        val a = ChapterId("a")
        val b = ChapterId("b")
        assertTrue(lock.tryAcquire(a))
        assertTrue(lock.tryAcquire(b))
        assertFalse(lock.tryAcquire(a))
        lock.release(a)
        assertTrue(lock.tryAcquire(a))
        assertTrue(lock.isHeld(b))
    }

    @Test fun aBusyChapterDoesNotBlockTheRunnerOfAnotherChapter() {
        val lock = ChapterTranslationLock()
        lock.tryAcquire(ChapterId("other-chapter"))
        assertEquals(JobRunResult.SUCCESS, runner(FakeProvider(), lock).go())
        assertEquals(TranslationStatus.COMPLETED, TranslationRepository(db).getTranslation(c1)!!.status)
    }
}
