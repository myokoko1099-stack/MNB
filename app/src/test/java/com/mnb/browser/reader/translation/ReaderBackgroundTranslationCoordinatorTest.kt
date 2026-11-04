package com.mnb.browser.reader.translation

import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.MockTranslationProvider
import com.mnb.browser.translation.SourceLanguage
import com.mnb.browser.translation.TargetLanguage
import com.mnb.browser.translation.TranslationPriority
import com.mnb.browser.translation.TranslationProvider
import com.mnb.browser.translation.TranslationStatus
import com.mnb.browser.translation.background.CHAPTER_TEXT
import com.mnb.browser.translation.background.EnqueueResult
import com.mnb.browser.translation.background.FakeProvider
import com.mnb.browser.translation.background.FakeScheduler
import com.mnb.browser.translation.background.JobRunResult
import com.mnb.browser.translation.background.TranslationPriorityManager
import com.mnb.browser.translation.background.TranslationQueue
import com.mnb.browser.translation.background.TranslationWorkScheduler
import com.mnb.browser.translation.background.WorkManagerTranslationScheduler
import com.mnb.browser.translation.background.buildRunner
import com.mnb.browser.translation.db.ChapterRepository
import com.mnb.browser.translation.db.DbTestBase
import com.mnb.browser.translation.db.NovelRepository
import com.mnb.browser.translation.db.TranslationJobRepository
import com.mnb.browser.translation.db.TranslationRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.RuntimeEnvironment

/** Background translation follows reader navigation (Phase 04-I10-C2). */
class ReaderBackgroundTranslationCoordinatorTest : DbTestBase() {
    private val novel = C1_NOVEL.value
    private val zh = SourceLanguage.CHINESE.code

    private val fakeScheduler = FakeScheduler()
    private var scheduler: TranslationWorkScheduler = fakeScheduler
    private val executor = C1ManualExecutor()
    private var provider: TranslationProvider? = C1FakeProvider()
    private var workerProvider: TranslationProvider? = C1FakeProvider()
    private var binding: NovelBinding? = NovelBinding(C1_NOVEL_URL, C1_NOVEL, "Novel 7", SourceLanguage.CHINESE.code)
    private var currentId: ChapterId? = null
    private var portsRequests = 0

    private lateinit var jobs: TranslationJobRepository
    private val ports: BackgroundTranslationPorts by lazy {
        val chapters = ChapterRepository(db)
        BackgroundTranslationPorts(
            chapters = chapters,
            jobs = jobs,
            queue = TranslationQueue(chapters, TranslationRepository(db), jobs, scheduler, "fake-real") { 50L },
            priorities = TranslationPriorityManager(jobs, LOOK_AHEAD),
            novels = RoomNovelRegistrar(NovelRepository(db)),
            lookAhead = LOOK_AHEAD,
        )
    }

    private fun newCoordinator(current: () -> ChapterId?) = ReaderBackgroundTranslationCoordinator(
        currentChapterId = current,
        bindingFor = { binding },
        providerSource = { provider },
        workerProviderSource = { workerProvider },
        portsSource = { portsRequests++; ports },
        executor = executor,
        clock = { 50L },
    )

    private val coordinator by lazy { newCoordinator { currentId } }

    @Before fun setUp() {
        jobs = TranslationJobRepository(db)
        NovelRepository(db).save(novelInfo(novel))
    }

    // ---- helpers ----------------------------------------------------------------------------

    private fun seed(count: Int) {
        for (i in 1..count) seedChapter(novelId = novel, chapterId = "c$i", number = i, text = CHAPTER_TEXT)
    }

    private fun ch(i: Int) = ChapterId("c$i")
    private fun url(i: Int) = "$C1_NOVEL_URL/ch$i"
    private fun identity(i: Int) = ReaderChapterIdentity(C1_NOVEL, ch(i), i, url(i), zh)
    private fun priorityOf(i: Int) = jobs.latestForChapter(ch(i))!!.priority
    private fun ids(list: List<ChapterId>) = list.map { it.value }

    /** The reader opens chapter [i]; the sync runs right away on this thread. */
    private fun sync(i: Int): BackgroundSyncReport {
        currentId = ch(i)
        return coordinator.sync(identity(i), url(i), CHAPTER_TEXT)
    }

    private fun setStatus(i: Int, status: TranslationStatus, completed: Int = 0) {
        val job = jobs.latestForChapter(ch(i))!!
        jobs.updateProgress(job.jobId, status, completed, 60L, null, 3)
    }

    // ---- 2. HIGH / NORMAL / LOW ---------------------------------------------------------------

    @Test fun firstSyncGivesHighNormalLowAndLeavesEarlierChaptersAlone() {
        seed(8)
        val report = sync(2)
        assertEquals(BackgroundSyncStatus.SYNCED, report.status)
        assertEquals(listOf("c2", "c3", "c4", "c5", "c6", "c7", "c8"), ids(report.enqueued))
        assertEquals(TranslationPriority.HIGH, priorityOf(2))
        for (i in 3..5) assertEquals(TranslationPriority.NORMAL, priorityOf(i))
        for (i in 6..8) assertEquals(TranslationPriority.LOW, priorityOf(i))
        assertNull(jobs.latestForChapter(ch(1))) // before the current chapter: not queued
        assertTrue(report.reprioritized.isEmpty()) // new jobs were created with the right priority already
    }

    @Test fun priorityRuleAgreesWithThePriorityManager() {
        seed(8)
        sync(3)
        val again = ports.priorities.onReaderChapterChanged(C1_NOVEL, 3)
        assertEquals(0, again.changedCount)
    }

    // ---- 1. Re-calculation when the chapter changes -------------------------------------------

    @Test fun movingToAnotherChapterRecalculatesWaitingJobs() {
        seed(8)
        sync(1) // c1 HIGH, c2-c4 NORMAL, c5-c8 LOW
        val report = sync(5)
        assertEquals(BackgroundSyncStatus.SYNCED, report.status)
        assertEquals(listOf("c5", "c6", "c7", "c8"), ids(report.reprioritized))
        assertEquals(TranslationPriority.HIGH, priorityOf(5))
        for (i in 6..8) assertEquals(TranslationPriority.NORMAL, priorityOf(i))
        // chapters before the new position keep what they had
        assertEquals(TranslationPriority.HIGH, priorityOf(1))
        for (i in 2..4) assertEquals(TranslationPriority.NORMAL, priorityOf(i))
    }

    @Test fun aJobThatIsAlreadyRunningKeepsItsPriority() {
        seed(8)
        sync(1)
        setStatus(6, TranslationStatus.PROCESSING, completed = 1)
        val report = sync(4)
        assertEquals(TranslationPriority.LOW, priorityOf(6))
        assertEquals(TranslationStatus.PROCESSING, jobs.latestForChapter(ch(6))!!.status)
        assertTrue(ch(6) !in report.reprioritized)
        assertEquals(TranslationPriority.NORMAL, priorityOf(7)) // 7 - 4 = 3: inside the window
    }

    // ---- 3. No duplicate jobs -----------------------------------------------------------------

    @Test fun syncingTheSameChapterAgainCreatesNoSecondJobOrWork() {
        seed(8)
        sync(1)
        val again = sync(1)
        assertTrue(again.enqueued.isEmpty())
        assertEquals(8, again.alreadyQueued.size)
        assertEquals(8, fakeScheduler.scheduled.size)
        assertEquals(8, jobs.findUnfinished().size)
    }

    @Test fun navigatingBackAndForthDoesNotMultiplyJobs() {
        seed(6)
        sync(1); sync(3); sync(1); sync(2)
        assertEquals(6, fakeScheduler.scheduled.size)
        assertEquals(6, jobs.findUnfinished().size)
    }

    // ---- 4. Completed chapters are not queued again -------------------------------------------

    @Test fun aChapterWithACompletedTranslationIsNotQueuedAgain() {
        seed(4)
        assertTrue(ports.queue.enqueue(ch(2), zh) is EnqueueResult.Enqueued)
        val completer = FakeProvider(id = "fake-real") { FakeProvider.ok(it, "fake-real") }
        val result = buildRunner(db, completer).run(ch(2), zh, TargetLanguage.MYANMAR.code, 0) { false }
        assertEquals(JobRunResult.SUCCESS, result)
        val scheduledBefore = fakeScheduler.scheduled.count { it == ch(2) }

        val report = sync(1)

        assertEquals(listOf("c2"), ids(report.alreadyTranslated))
        assertEquals(listOf("c1", "c3", "c4"), ids(report.enqueued))
        assertEquals(scheduledBefore, fakeScheduler.scheduled.count { it == ch(2) })
        assertEquals(TranslationStatus.COMPLETED, jobs.latestForChapter(ch(2))!!.status)
    }

    @Test fun aFailedOrCancelledLaterChapterIsNotRetriedOnEveryNavigation() {
        seed(4)
        sync(1)
        setStatus(3, TranslationStatus.FAILED)
        setStatus(4, TranslationStatus.CANCELLED)
        val report = sync(1)
        assertEquals(listOf("c3", "c4"), ids(report.skipped))
        assertEquals(TranslationStatus.FAILED, jobs.latestForChapter(ch(3))!!.status)
        assertEquals(TranslationStatus.CANCELLED, jobs.latestForChapter(ch(4))!!.status)
    }

    // ---- 5. / 6. Provider safety ----------------------------------------------------------------

    @Test fun noProviderMeansNotConfiguredAndNothingStarts() {
        seed(3)
        provider = null
        val report = sync(1)
        assertEquals(BackgroundSyncStatus.NOT_CONFIGURED, report.status)
        assertNothingStarted()
    }

    @Test fun noProviderOpenedThroughTheReaderPathStaysNotConfigured() {
        seed(3)
        provider = null
        currentId = ch(1)
        coordinator.onChapterOpened(identity(1), url(1), CHAPTER_TEXT)
        executor.runAll()
        assertNothingStarted()
        assertEquals(TranslationStatus.PENDING, ChapterRepository(db).get(ch(1))!!.status) // untouched
    }

    @Test fun mockProviderNeverStartsBackgroundTranslation() {
        seed(3)
        provider = MockTranslationProvider()
        val report = sync(1)
        assertEquals(BackgroundSyncStatus.MOCK_ONLY, report.status)
        assertNothingStarted()
        assertNull(TranslationRepository(db).getTranslation(ch(1))) // no mock output stored as a translation
    }

    @Test fun workerThatFallsBackToTheMockProviderIsRefused() {
        seed(3)
        workerProvider = MockTranslationProvider()
        assertEquals(BackgroundSyncStatus.WORKER_PROVIDER_MISMATCH, sync(1).status)
        assertNothingStarted()
    }

    @Test fun workerWithAnotherProviderIsRefused() {
        seed(3)
        workerProvider = C1FakeProvider(id = "other")
        assertEquals(BackgroundSyncStatus.WORKER_PROVIDER_MISMATCH, sync(1).status)
        assertNothingStarted()
    }

    @Test fun providerThatDoesNotSupportTheLanguageStartsNothing() {
        seed(3)
        val others = SourceLanguage.values().filter { it != SourceLanguage.CHINESE }.toSet()
        provider = C1FakeProvider(supportedSourceLanguages = others)
        assertEquals(BackgroundSyncStatus.UNSUPPORTED_LANGUAGE, sync(1).status)
        assertNothingStarted()
    }

    @Test fun missingBindingMeansNoIdentityAndNothingStarts() {
        seed(3)
        binding = null
        assertEquals(BackgroundSyncStatus.NO_IDENTITY, sync(1).status)
        assertNothingStarted()
    }

    private fun assertNothingStarted() {
        assertEquals(0, portsRequests) // database and queue were never opened
        assertTrue(fakeScheduler.scheduled.isEmpty())
        assertTrue(jobs.findUnfinished().isEmpty())
    }

    // ---- Thread safety --------------------------------------------------------------------------

    @Test fun chapterOpenedOnlyQueuesWorkAndRunsNothingOnTheCallerThread() {
        seed(3)
        currentId = ch(1)
        coordinator.onChapterOpened(identity(1), url(1), CHAPTER_TEXT)
        assertEquals(1, executor.pending)
        assertTrue(jobs.findUnfinished().isEmpty())
        assertEquals(0, portsRequests)
        executor.runAll()
        assertEquals(3, jobs.findUnfinished().size)
    }

    @Test fun aShutDownExecutorDoesNotThrow() {
        val rejecting = java.util.concurrent.Executor { throw java.util.concurrent.RejectedExecutionException() }
        val c = ReaderBackgroundTranslationCoordinator({ ch(1) }, { binding }, { provider }, { workerProvider }, { ports }, rejecting)
        c.onChapterOpened(identity(1), url(1), CHAPTER_TEXT) // must not throw
    }

    // ---- 7. An old chapter never changes the current reader state ------------------------------

    @Test fun aLateSyncOfAnOldChapterDoesNothing() {
        seed(4)
        currentId = ch(3) // the reader has moved on
        val report = coordinator.sync(identity(1), url(1), CHAPTER_TEXT)
        assertEquals(BackgroundSyncStatus.SUPERSEDED, report.status)
        assertNothingStarted()
    }

    @Test fun noOpenChapterMeansSuperseded() {
        seed(2)
        currentId = null
        assertEquals(BackgroundSyncStatus.SUPERSEDED, coordinator.sync(identity(1), url(1), CHAPTER_TEXT).status)
        assertNothingStarted()
    }

    @Test fun oldChapterWorkNeverTouchesTheReaderState() {
        var holderRef: ReaderTranslationStateHolder? = null
        val c = newCoordinator { holderRef?.current?.identity?.chapterId }
        val holder = ReaderTranslationStateHolder(
            BoundChapterIdentityResolver(c1BoundStore(SourceLanguage.CHINESE.code)),
            chapterListener = { id, content -> c.onChapterOpened(id, content.url, content.mainText) },
        )
        holderRef = holder

        holder.onChapterOpened(c1Chapter(1))
        val id1 = holder.current!!.identity!!.chapterId
        holder.onChapterOpened(c1Chapter(2)) // the reader moves on before the first sync has run
        val id2 = holder.current!!.identity!!.chapterId
        assertEquals(2, executor.pending)
        val before = holder.current

        executor.runAll()

        assertEquals(before, holder.current) // identity and state of chapter 2 unchanged
        assertEquals(ReaderTranslationState.OriginalOnly, holder.current!!.state)
        assertNull(ChapterRepository(db).get(id1)) // chapter 1 was never stored or queued
        assertNull(jobs.latestForChapter(id1))
        assertEquals(TranslationPriority.HIGH, jobs.latestForChapter(id2)!!.priority)
    }

    // ---- 8. WorkManager scheduling and cancellation --------------------------------------------

    @Test fun workWaitsOfflineStaysUniqueAndCancellationIsKept() {
        val context = RuntimeEnvironment.getApplication()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        val wm = WorkManager.getInstance(context)
        scheduler = WorkManagerTranslationScheduler(wm)
        seed(3)

        sync(1); sync(1); sync(2)

        fun infos(i: Int) = wm.getWorkInfosForUniqueWork(WorkManagerTranslationScheduler.uniqueName(ch(i))).get()
        for (i in 1..3) {
            assertEquals(1, infos(i).size) // one unique work per chapter, however often we synced
            assertEquals(WorkInfo.State.ENQUEUED, infos(i)[0].state) // no network constraint met: nothing ran
        }

        assertTrue(ports.queue.cancel(ch(2)))
        assertEquals(WorkInfo.State.CANCELLED, infos(2)[0].state)
        assertEquals(TranslationStatus.CANCELLED, jobs.latestForChapter(ch(2))!!.status)

        val report = sync(1) // a cancelled later chapter is not revived by navigation
        assertEquals(listOf("c2"), ids(report.skipped))
        assertEquals(WorkInfo.State.CANCELLED, infos(2)[0].state)
        assertEquals(WorkInfo.State.ENQUEUED, infos(1)[0].state)
        assertEquals(WorkInfo.State.ENQUEUED, infos(3)[0].state)
    }

    private companion object {
        const val LOOK_AHEAD = 3
    }
}
