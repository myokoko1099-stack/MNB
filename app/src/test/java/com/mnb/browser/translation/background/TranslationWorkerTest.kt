package com.mnb.browser.translation.background

import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.TargetLanguage
import com.mnb.browser.translation.TranslationError
import com.mnb.browser.translation.TranslationResult
import com.mnb.browser.translation.TranslationStatus
import com.mnb.browser.translation.db.ChapterRepository
import com.mnb.browser.translation.db.DbTestBase
import com.mnb.browser.translation.db.TranslationJobRepository
import com.mnb.browser.translation.db.TranslationRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.RuntimeEnvironment

class TranslationWorkerTest : DbTestBase() {
    private val c1 = ChapterId("c1")
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun setUp() {
        seedChapter(text = CHAPTER_TEXT)
    }

    @After fun resetEnvironment() {
        TranslationWorkEnvironment.reset()
    }

    private fun worker(chapter: String?, attempt: Int = 0): TranslationWorker {
        val data = Data.Builder().apply {
            if (chapter != null) putString(WorkManagerTranslationScheduler.KEY_CHAPTER_ID, chapter)
            putString(WorkManagerTranslationScheduler.KEY_SOURCE_LANGUAGE, "en")
            putString(WorkManagerTranslationScheduler.KEY_TARGET_LANGUAGE, TargetLanguage.MYANMAR.code)
        }.build()
        return TestListenableWorkerBuilder.from(context, TranslationWorker::class.java)
            .setInputData(data)
            .setRunAttemptCount(attempt)
            .build()
    }

    @Test fun workerSuccessReturnsSuccessAndStoresTranslation() {
        TranslationWorkEnvironment.runnerFactory = { buildRunner(db, FakeProvider()) }
        assertEquals(ListenableWorker.Result.success(), worker("c1").doWork())
        assertEquals(TranslationStatus.COMPLETED, TranslationRepository(db).getTranslation(c1)!!.status)
        assertEquals(TranslationStatus.COMPLETED, TranslationJobRepository(db).latestForChapter(c1)!!.status)
    }

    @Test fun workerProviderFailureAsksForRetry() {
        val failing = FakeProvider { TranslationResult.Failure(TranslationError.PROVIDER_FAILURE, "fake", "offline") }
        TranslationWorkEnvironment.runnerFactory = { buildRunner(db, failing) }
        assertEquals(ListenableWorker.Result.retry(), worker("c1", attempt = 0).doWork())
        assertEquals(TranslationStatus.PENDING, TranslationJobRepository(db).latestForChapter(c1)!!.status)
    }

    @Test fun workerGivesUpAtTheAttemptLimit() {
        val failing = FakeProvider { TranslationResult.Failure(TranslationError.PROVIDER_FAILURE, "fake", "offline") }
        TranslationWorkEnvironment.runnerFactory = { buildRunner(db, failing, maxAttempts = 2) }
        assertEquals(ListenableWorker.Result.failure(), worker("c1", attempt = 1).doWork())
        assertEquals(TranslationStatus.FAILED, TranslationJobRepository(db).latestForChapter(c1)!!.status)
    }

    @Test fun workerWithoutChapterIdFails() {
        TranslationWorkEnvironment.runnerFactory = { buildRunner(db, FakeProvider()) }
        assertEquals(ListenableWorker.Result.failure(), worker(null).doWork())
    }

    @Test fun requestRequiresNetworkAndCarriesInput() {
        val request = WorkManagerTranslationScheduler.buildRequest(c1, "en", "my")
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, request.workSpec.backoffPolicy)
        assertEquals("c1", request.workSpec.input.getString(WorkManagerTranslationScheduler.KEY_CHAPTER_ID))
        assertTrue(WorkManagerTranslationScheduler.TAG in request.tags)
    }

    @Test fun workWaitsForNetworkThenRunsAndSchedulingTwiceKeepsOneWork() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        val wm = WorkManager.getInstance(context)
        val provider = FakeProvider()
        TranslationWorkEnvironment.runnerFactory = { buildRunner(db, provider) }
        val scheduler = WorkManagerTranslationScheduler(wm)
        val queue = TranslationQueue(
            ChapterRepository(db), TranslationRepository(db), TranslationJobRepository(db), scheduler, "fake",
        )

        assertTrue(queue.enqueue(c1, "en") is EnqueueResult.Enqueued)
        assertTrue(queue.enqueue(c1, "en") is EnqueueResult.AlreadyQueued)

        val infos = wm.getWorkInfosForUniqueWork(WorkManagerTranslationScheduler.uniqueName(c1)).get()
        assertEquals(1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos[0].state) // offline: constraint not met, nothing ran
        assertTrue(provider.requests.isEmpty())
        assertTrue(scheduler.isScheduled(c1))

        val driver = WorkManagerTestInitHelper.getTestDriver(context)
        assertNotNull(driver)
        driver!!.setAllConstraintsMet(infos[0].id) // network returns
        assertEquals(WorkInfo.State.SUCCEEDED, wm.getWorkInfoById(infos[0].id).get().state)
        assertEquals(3, provider.requests.size)
        assertEquals(TranslationStatus.COMPLETED, TranslationJobRepository(db).latestForChapter(c1)!!.status)
        assertFalse(scheduler.isScheduled(c1))
    }
}
