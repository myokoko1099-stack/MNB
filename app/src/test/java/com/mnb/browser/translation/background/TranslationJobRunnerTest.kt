package com.mnb.browser.translation.background

import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.TargetLanguage
import com.mnb.browser.translation.TranslationError
import com.mnb.browser.translation.TranslationResult
import com.mnb.browser.translation.TranslationStatus
import com.mnb.browser.translation.db.DbTestBase
import com.mnb.browser.translation.db.TranslationJobRepository
import com.mnb.browser.translation.db.TranslationRepository
import com.mnb.browser.translation.usecase.InstantTranslationFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TranslationJobRunnerTest : DbTestBase() {
    private val c1 = ChapterId("c1")
    private val target = TargetLanguage.MYANMAR.code
    private lateinit var jobs: TranslationJobRepository

    @Before fun setUp() {
        seedChapter(text = CHAPTER_TEXT)
        jobs = TranslationJobRepository(db)
    }

    private fun TranslationJobRunner.go(attempt: Int = 0, lang: String? = "en", stopped: () -> Boolean = { false }) =
        run(c1, lang, target, attempt, stopped)

    private fun job() = jobs.latestForChapter(c1)!!
    private fun failing() = FakeProvider { TranslationResult.Failure(TranslationError.PROVIDER_FAILURE, "fake", "offline") }

    @Test fun successCompletesJobAndTranslation() {
        val provider = FakeProvider()
        assertEquals(JobRunResult.SUCCESS, buildRunner(db, provider).go())
        assertEquals(TranslationStatus.COMPLETED, job().status)
        assertEquals(3, job().totalChunks)
        assertEquals(3, job().completedChunks)
        assertEquals(TranslationStatus.COMPLETED, TranslationRepository(db).getTranslation(c1)!!.status)
        assertEquals(3, provider.requests.size)
    }

    @Test fun finishedJobIsNotRunAgain() {
        val provider = FakeProvider()
        val runner = buildRunner(db, provider)
        runner.go()
        assertEquals(JobRunResult.SUCCESS, runner.go())
        assertEquals(3, provider.requests.size)
    }

    @Test fun existingCompletedTranslationIsReusedWithoutCallingTheProvider() {
        val provider = FakeProvider()
        buildRunner(db, provider).go()
        jobs.updateProgress(job().jobId, TranslationStatus.FAILED, 0, 120L) // old job ended; a new one is needed
        assertEquals(JobRunResult.FAILURE, buildRunner(db, provider).go()) // FAILED latest job stops the worker
        // New job for a chapter that already has a finished translation:
        val again = jobs.create(c1, 0, 130L)
        assertEquals(TranslationStatus.PENDING, again.status)
        assertEquals(JobRunResult.SUCCESS, buildRunner(db, provider, clock = { 200L }).go())
        assertEquals(3, provider.requests.size)
        assertEquals(TranslationStatus.COMPLETED, job().status)
    }

    @Test fun providerFailureRetriesThenFailsAtTheAttemptLimit() {
        val runner = buildRunner(db, failing(), maxAttempts = 3)
        assertEquals(JobRunResult.RETRY, runner.go(attempt = 0))
        assertEquals(TranslationStatus.PENDING, job().status)
        assertEquals(TranslationError.PROVIDER_FAILURE, job().errorCode)
        assertEquals(JobRunResult.RETRY, runner.go(attempt = 1))
        assertEquals(JobRunResult.FAILURE, runner.go(attempt = 2))
        assertEquals(TranslationStatus.FAILED, job().status)
    }

    @Test fun retryReusesChunksThatAlreadyFinished() {
        var broken = true
        val provider = FakeProvider { req ->
            if (broken && req.text == PARAGRAPHS[1]) TranslationResult.Failure(TranslationError.PROVIDER_FAILURE, "fake", "timeout")
            else FakeProvider.ok(req)
        }
        val runner = buildRunner(db, provider)
        assertEquals(JobRunResult.RETRY, runner.go(attempt = 0))
        assertEquals(2, job().completedChunks)
        broken = false
        provider.requests.clear()
        assertEquals(JobRunResult.SUCCESS, runner.go(attempt = 1))
        assertEquals(listOf(PARAGRAPHS[1]), provider.requests.map { it.text })
        assertEquals(TranslationStatus.COMPLETED, job().status)
    }

    @Test fun permanentFailureIsNotRetried() {
        val provider = FakeProvider()
        assertEquals(JobRunResult.FAILURE, buildRunner(db, provider).go(lang = "fr"))
        assertEquals(TranslationStatus.FAILED, job().status)
        assertEquals(TranslationError.UNSUPPORTED_SOURCE_LANGUAGE, job().errorCode)
        assertTrue(provider.requests.isEmpty())
    }

    @Test fun retryableAndPermanentReasonsAreSeparated() {
        assertFalse(TranslationJobRunner.isRetryable(InstantTranslationFailure.EMPTY_INPUT))
        assertFalse(TranslationJobRunner.isRetryable(InstantTranslationFailure.UNSUPPORTED_LANGUAGE))
        assertFalse(TranslationJobRunner.isRetryable(InstantTranslationFailure.INVALID_CHUNK))
        assertTrue(TranslationJobRunner.isRetryable(InstantTranslationFailure.PROVIDER_FAILURE))
        assertTrue(TranslationJobRunner.isRetryable(InstantTranslationFailure.PARTIAL_FAILURE))
        assertTrue(TranslationJobRunner.isRetryable(InstantTranslationFailure.DATABASE_FAILURE))
    }

    @Test fun stopKeepsJobPendingAndRestartResumesFromFinishedChunks() {
        val provider = FakeProvider()
        // First process: stopped after one chunk.
        assertEquals(JobRunResult.RETRY, buildRunner(db, provider).go { provider.requests.size >= 1 })
        assertEquals(TranslationStatus.PENDING, job().status)
        assertEquals(1, job().completedChunks)
        assertEquals(3, job().totalChunks)
        assertEquals(1, jobs.findUnfinished().size)
        // "App restart": new runner and pipeline over the same database finish the rest.
        assertEquals(JobRunResult.SUCCESS, buildRunner(db, provider).go(attempt = 1))
        assertEquals(3, provider.requests.size)
        assertEquals(TranslationStatus.COMPLETED, job().status)
        assertTrue(jobs.findUnfinished().isEmpty())
    }

    @Test fun stopDoesNotOverwriteACancelledJob() {
        val created = jobs.create(c1, 0, 10L)
        val provider = FakeProvider()
        val result = buildRunner(db, provider).go {
            jobs.updateProgress(created.jobId, TranslationStatus.CANCELLED, 0, 50L)
            true
        }
        assertEquals(JobRunResult.RETRY, result)
        assertEquals(TranslationStatus.CANCELLED, job().status)
        assertTrue(provider.requests.isEmpty())
    }

    @Test fun cancelledJobIsNotRun() {
        val created = jobs.create(c1, 0, 10L)
        jobs.updateProgress(created.jobId, TranslationStatus.CANCELLED, 0, 20L)
        val provider = FakeProvider()
        assertEquals(JobRunResult.SUCCESS, buildRunner(db, provider).go())
        assertTrue(provider.requests.isEmpty())
        assertEquals(TranslationStatus.CANCELLED, job().status)
    }

    @Test fun missingChapterIsAPermanentFailure() {
        val runner = buildRunner(db, FakeProvider())
        assertEquals(JobRunResult.FAILURE, runner.run(ChapterId("zzz"), "en", target, 0) { false })
    }
}
