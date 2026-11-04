package com.mnb.browser.translation.background

import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.MockTranslationProvider
import com.mnb.browser.translation.SourceLanguage
import com.mnb.browser.translation.TargetLanguage
import com.mnb.browser.translation.TranslationError
import com.mnb.browser.translation.TranslationProvider
import com.mnb.browser.translation.TranslationRequest
import com.mnb.browser.translation.TranslationResult
import com.mnb.browser.translation.TranslationStatus
import com.mnb.browser.translation.db.DbTestBase
import com.mnb.browser.translation.db.TranslationJobRepository
import com.mnb.browser.translation.db.TranslationRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.RuntimeEnvironment

/** Phase 04-I10-D: the worker itself refuses mock / missing / mismatched / unsupported providers. */
class TranslationWorkerProviderGuardTest : DbTestBase() {
    private val c1 = ChapterId("c1")
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var jobs: TranslationJobRepository

    private class MockLikeProvider : TranslationProvider {
        override val id = "mock-like"
        override val displayName = "Mock like"
        override val isMock = true
        override val supportedSourceLanguages: Set<SourceLanguage> = SourceLanguage.values().toSet()
        override fun translate(request: TranslationRequest): TranslationResult =
            throw IllegalStateException("a mock provider must never be called")
    }

    @Before fun setUp() {
        seedChapter(text = CHAPTER_TEXT)
        jobs = TranslationJobRepository(db)
        TranslationWorkEnvironment.jobsFactory = { TranslationJobRepository(db) }
    }

    @After fun resetEnvironment() {
        TranslationWorkEnvironment.reset()
    }

    private fun worker(lang: String? = "en"): TranslationWorker {
        val data = Data.Builder().apply {
            putString(WorkManagerTranslationScheduler.KEY_CHAPTER_ID, "c1")
            if (lang != null) putString(WorkManagerTranslationScheduler.KEY_SOURCE_LANGUAGE, lang)
            putString(WorkManagerTranslationScheduler.KEY_TARGET_LANGUAGE, TargetLanguage.MYANMAR.code)
        }.build()
        return TestListenableWorkerBuilder.from(context, TranslationWorker::class.java).setInputData(data).build()
    }

    private fun assertNoRealTranslationStored() {
        val stored = TranslationRepository(db).getTranslation(c1)
        assertTrue(stored == null || stored.status != TranslationStatus.COMPLETED)
    }

    // ---- pure guard -------------------------------------------------------------------------

    @Test fun guardRejectsMissingProvider() {
        assertEquals(WorkerRefusal.NO_PROVIDER, TranslationWorkerGuard.check(null, "fake", "en"))
        assertEquals(WorkerRefusal.NO_PROVIDER, TranslationWorkerGuard.check(FakeProvider(), null, "en"))
    }

    @Test fun guardRejectsMockProviderEvenWithMatchingId() {
        assertEquals(WorkerRefusal.MOCK_PROVIDER, TranslationWorkerGuard.check(MockLikeProvider(), "mock-like", "en"))
        val real = MockTranslationProvider()
        assertEquals(WorkerRefusal.MOCK_PROVIDER, TranslationWorkerGuard.check(real, real.id, "en"))
    }

    @Test fun guardRejectsProviderIdMismatch() {
        assertEquals(WorkerRefusal.PROVIDER_MISMATCH, TranslationWorkerGuard.check(FakeProvider("a"), "b", "en"))
    }

    @Test fun guardRejectsUnsupportedOrMissingSourceLanguage() {
        assertEquals(WorkerRefusal.UNSUPPORTED_LANGUAGE, TranslationWorkerGuard.check(FakeProvider(), "fake", "fr"))
        assertEquals(WorkerRefusal.UNSUPPORTED_LANGUAGE, TranslationWorkerGuard.check(FakeProvider(), "fake", null))
    }

    @Test fun guardAllowsARealMatchingProvider() {
        assertNull(TranslationWorkerGuard.check(FakeProvider(), "fake", "en"))
    }

    // ---- worker run directly (default runner path: nobody injected a runner) -----------------

    @Test fun defaultEnvironmentWorkerRefusesTheMockDefaultAndStoresNothing() {
        // providerFactory still defaults to MockTranslationProvider, no reader provider is bound.
        val created = jobs.create(c1, 0, 10L)
        assertTrue(TranslationWorkEnvironment.usesDefaultRunner())
        assertEquals(ListenableWorker.Result.failure(), worker().doWork())
        assertNoRealTranslationStored()
        assertEquals(TranslationStatus.FAILED, jobs.get(created.jobId)!!.status)
    }

    @Test fun workerRefusesMockProviderEvenWhenTheReaderIdMatches() {
        val mock = MockLikeProvider()
        TranslationWorkEnvironment.providerFactory = { mock }
        TranslationWorkEnvironment.readerProviderIdSource = { mock.id }
        jobs.create(c1, 0, 10L)
        assertEquals(ListenableWorker.Result.failure(), worker().doWork())
        assertNoRealTranslationStored()
    }

    @Test fun workerRefusesWhenWorkerAndReaderProviderIdsDiffer() {
        val provider = FakeProvider("worker-provider")
        TranslationWorkEnvironment.providerFactory = { provider }
        TranslationWorkEnvironment.readerProviderIdSource = { "reader-provider" }
        jobs.create(c1, 0, 10L)
        assertEquals(ListenableWorker.Result.failure(), worker().doWork())
        assertTrue(provider.requests.isEmpty())
        assertNoRealTranslationStored()
    }

    @Test fun workerRefusesWhenNoReaderProviderIsBound() {
        val provider = FakeProvider()
        TranslationWorkEnvironment.providerFactory = { provider }
        jobs.create(c1, 0, 10L)
        assertEquals(ListenableWorker.Result.failure(), worker().doWork())
        assertTrue(provider.requests.isEmpty())
    }

    @Test fun workerRefusesUnsupportedSourceLanguageAndMarksTheJobFailed() {
        val provider = FakeProvider()
        TranslationWorkEnvironment.providerFactory = { provider }
        TranslationWorkEnvironment.readerProviderIdSource = { provider.id }
        val created = jobs.create(c1, 0, 10L)
        assertEquals(ListenableWorker.Result.failure(), worker(lang = "fr").doWork())
        assertTrue(provider.requests.isEmpty())
        assertNoRealTranslationStored()
        val job = jobs.get(created.jobId)!!
        assertEquals(TranslationStatus.FAILED, job.status)
        assertEquals(TranslationError.UNSUPPORTED_SOURCE_LANGUAGE, job.errorCode)
    }

    @Test fun refusalDoesNotTouchAFinishedJob() {
        val created = jobs.create(c1, 0, 10L)
        jobs.updateProgress(created.jobId, TranslationStatus.COMPLETED, 3, 20L, null, 3)
        assertEquals(ListenableWorker.Result.failure(), worker().doWork())
        assertEquals(TranslationStatus.COMPLETED, jobs.get(created.jobId)!!.status)
    }

    @Test fun resetRestoresTheSafeDefaults() {
        TranslationWorkEnvironment.providerFactory = { FakeProvider() }
        TranslationWorkEnvironment.readerProviderIdSource = { "fake" }
        TranslationWorkEnvironment.runnerFactory = { buildRunner(db, FakeProvider()) }
        TranslationWorkEnvironment.reset()
        assertTrue(TranslationWorkEnvironment.usesDefaultRunner())
        assertTrue(TranslationWorkEnvironment.providerFactory().isMock)
        assertNull(TranslationWorkEnvironment.readerProviderIdSource())
    }
}
