package com.mnb.browser.reader.translation

import com.mnb.browser.extraction.ExtractedContent
import com.mnb.browser.translation.TranslationProvider
import com.mnb.browser.translation.TranslationStatus
import com.mnb.browser.translation.usecase.InstantTranslationFailure
import com.mnb.browser.translation.usecase.InstantTranslationOutcome
import com.mnb.browser.translation.usecase.TranslateChapterRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderInstantTranslationControllerTest {

    private class Fixture(
        language: String = "en",
        var provider: TranslationProvider? = C1FakeProvider(),
        var gateway: ReaderTranslationGateway = UnavailableReaderTranslationGateway,
        var novelResult: NovelEnsureResult = NovelEnsureResult.READY,
        var backendAvailable: Boolean = true,
        var runnerBody: (TranslateChapterRequest, () -> Boolean) -> InstantTranslationOutcome = { r, _ -> c1Completed(c1Stored(r.chapterId)) },
    ) {
        val bindings = c1BoundStore(language)
        val holder = ReaderTranslationStateHolder(BoundChapterIdentityResolver(bindings))
        val executor = C1ManualExecutor()
        val requests = ArrayList<TranslateChapterRequest>()
        var ensureCalls = 0
        var runCalls = 0
        var cancelledSeenAtRun: Boolean? = null

        private val backend = TranslationBackend(
            runner = { request, _, isCancelled ->
                runCalls++
                requests += request
                cancelledSeenAtRun = isCancelled()
                runnerBody(request, isCancelled)
            },
            novels = { _, _ -> ensureCalls++; novelResult },
        )

        val controller = ReaderInstantTranslationController(
            holder = holder,
            bindings = bindings,
            gateway = gateway,
            providerSource = { provider },
            backendSource = { if (backendAvailable) backend else null },
            executor = executor,
            clock = { 1_000L },
        )

        fun open(content: ExtractedContent) = holder.onChapterOpened(content)
        val state: ReaderTranslationState? get() = holder.current?.state
    }

    private fun unavailable(r: ReaderTranslationUnavailableReason) = ReaderTranslationState.Unavailable(r)

    // ---- identity ----------------------------------------------------------------------------

    @Test fun validChapterIdentityStartsTranslationWithAllRequestFields() {
        val f = Fixture(language = "ja")
        val content = c1Chapter(5)
        f.open(content)
        assertEquals(ReaderTranslationState.OriginalOnly, f.state)

        assertEquals(StartResult.STARTED, f.controller.start(content))
        assertEquals(ReaderTranslationState.Processing(), f.state)
        assertEquals(0, f.runCalls)               // nothing runs on the caller's thread
        assertEquals(1, f.executor.pending)

        f.executor.runAll()
        val r = f.requests.single()
        assertEquals(BoundChapterIdentityResolver.chapterIdFor(C1_NOVEL, 5), r.chapterId)
        assertEquals(C1_NOVEL, r.novelId)
        assertEquals(5, r.chapterNumber)
        assertEquals("ja", r.sourceLanguageCode)
        assertEquals("my", r.targetLanguageCode)
        assertEquals(content.mainText, r.originalText)
    }

    @Test fun unknownChapterIdentityNeverStarts() {
        val f = Fixture()
        val content = c1Chapter(null, title = "Prologue")
        f.open(content)
        assertEquals(unavailable(ReaderTranslationUnavailableReason.NO_CHAPTER_IDENTITY), f.state)
        assertEquals(StartResult.NO_IDENTITY, f.controller.start(content))
        assertEquals(0, f.executor.pending)
        assertEquals(0, f.runCalls)
    }

    @Test fun missingBindingNeverStarts() {
        val f = Fixture()
        val content = c1Chapter(5, url = "https://unbound.example/n/ch5")
        f.open(content)
        assertEquals(StartResult.NO_IDENTITY, f.controller.start(content))
        assertEquals(0, f.runCalls)
    }

    @Test fun unsupportedSourceLanguageNeverStarts() {
        val f = Fixture()
        val content = c1Chapter(5)
        // An identity carrying an unsupported language (as a stale or corrupted one might).
        val bad = ReaderTranslationStateHolder(
            resolver = ReaderChapterIdentityResolver { c ->
                BoundChapterIdentityResolver(f.bindings).resolve(c)?.copy(sourceLanguageCode = "fr")
            },
        )
        val controller = ReaderInstantTranslationController(
            bad, f.bindings, UnavailableReaderTranslationGateway, { C1FakeProvider() }, { null }, f.executor,
        )
        bad.onChapterOpened(content)
        assertEquals(StartResult.NO_IDENTITY, controller.start(content))
        assertEquals(unavailable(ReaderTranslationUnavailableReason.NO_CHAPTER_IDENTITY), bad.current?.state)
        assertEquals(0, f.runCalls)
    }

    @Test fun contentThatIsNotTheOpenChapterIsRejected() {
        val f = Fixture()
        f.open(c1Chapter(5))
        assertEquals(StartResult.CONTENT_MISMATCH, f.controller.start(c1Chapter(6)))
        assertEquals(ReaderTranslationState.OriginalOnly, f.state)
        assertEquals(StartResult.NO_CHAPTER_OPEN, Fixture().controller.start(c1Chapter(1)))
    }

    // ---- provider safety ---------------------------------------------------------------------

    @Test fun mockProviderIsBlockedAndNothingRuns() {
        val f = Fixture(provider = C1FakeProvider(isMock = true))
        val content = c1Chapter(1)
        f.open(content)
        assertEquals(StartResult.BLOCKED, f.controller.start(content))
        assertEquals(unavailable(ReaderTranslationUnavailableReason.MOCK_ONLY), f.state)
        assertEquals(0, f.executor.pending)
        assertEquals(0, f.runCalls)
    }

    @Test fun missingRealProviderIsNotConfigured() {
        val f = Fixture(provider = null)
        val content = c1Chapter(1)
        f.open(content)
        assertEquals(StartResult.BLOCKED, f.controller.start(content))
        assertEquals(unavailable(ReaderTranslationUnavailableReason.NOT_CONFIGURED), f.state)
        assertEquals(0, f.runCalls)
    }

    @Test fun unavailableBackendIsNotConfigured() {
        val f = Fixture(backendAvailable = false)
        val content = c1Chapter(1)
        f.open(content)
        assertEquals(StartResult.BLOCKED, f.controller.start(content))
        assertEquals(unavailable(ReaderTranslationUnavailableReason.NOT_CONFIGURED), f.state)
    }

    @Test fun providerWithoutTheSourceLanguageFailsWithoutRetry() {
        val f = Fixture(provider = C1FakeProvider(supportedSourceLanguages = setOf(com.mnb.browser.translation.SourceLanguage.CHINESE)))
        val content = c1Chapter(1)
        f.open(content)
        assertEquals(StartResult.BLOCKED, f.controller.start(content))
        assertEquals(ReaderTranslationState.Failed(ReaderTranslationFailure.UNSUPPORTED_LANGUAGE, canRetry = false), f.state)
    }

    @Test fun blockedStateCanBeRetriedOnceProviderExists() {
        val f = Fixture(provider = null)
        val content = c1Chapter(1)
        f.open(content)
        f.controller.start(content)
        f.provider = C1FakeProvider()
        assertEquals(StartResult.STARTED, f.controller.start(content))
    }

    // ---- results -----------------------------------------------------------------------------

    @Test fun successShowsCompletedOnlyAfterTheStoredResult() {
        val f = Fixture()
        val content = c1Chapter(1)
        f.open(content)
        f.controller.start(content)
        assertEquals(ReaderTranslationState.Processing(), f.state) // not Completed before the work finished
        f.executor.runAll()
        assertEquals(ReaderTranslationState.Completed("မြန်မာစာသား", "fake-real"), f.state)
        assertEquals(1, f.ensureCalls)
    }

    @Test fun lifecycleCancellationIsCheckedInsideTheProductionTranslationOperation() {
        var cancelled = false
        val f = Fixture(runnerBody = { request, isCancelled ->
            assertTrue(isCancelled())
            c1Completed(c1Stored(request.chapterId))
        })
        val content = c1Chapter(1)
        f.open(content)

        assertEquals(StartResult.STARTED, f.controller.start(content, isCancelled = { cancelled }))
        cancelled = true
        f.executor.runAll()

        assertEquals(true, f.cancelledSeenAtRun)
        // The pipeline result cannot be published after the originating page operation is cancelled.
        assertEquals(ReaderTranslationState.Processing(), f.state)
    }

    @Test fun mockOutputInAStoredResultIsNeverCompleted() {
        val f = Fixture(runnerBody = { r, _ -> c1Completed(c1Stored(r.chapterId, isMock = true)) })
        val content = c1Chapter(1)
        f.open(content)
        f.controller.start(content)
        f.executor.runAll()
        assertEquals(unavailable(ReaderTranslationUnavailableReason.MOCK_ONLY), f.state)
    }

    @Test fun completedWithoutValidStoredTextIsAStorageFailure() {
        val blank = Fixture(runnerBody = { r, _ -> c1Completed(c1Stored(r.chapterId, text = "  ")) })
        val notDone = Fixture(runnerBody = { r, _ -> c1Completed(c1Stored(r.chapterId, status = TranslationStatus.FAILED)) })
        listOf(blank, notDone).forEach { f ->
            val content = c1Chapter(1)
            f.open(content)
            f.controller.start(content)
            f.executor.runAll()
            assertEquals(ReaderTranslationState.Failed(ReaderTranslationFailure.STORAGE, canRetry = true), f.state)
        }
    }

    @Test fun failureOutcomesMapToTheRightReason() {
        fun reasonFor(reason: InstantTranslationFailure): ReaderTranslationState {
            val f = Fixture(runnerBody = { _, _ -> InstantTranslationOutcome.Failed(reason, "x") })
            val content = c1Chapter(1)
            f.open(content)
            f.controller.start(content)
            f.executor.runAll()
            return f.state!!
        }
        assertEquals(ReaderTranslationState.Failed(ReaderTranslationFailure.PROVIDER, true), reasonFor(InstantTranslationFailure.PROVIDER_FAILURE))
        assertEquals(ReaderTranslationState.Failed(ReaderTranslationFailure.PROVIDER, true), reasonFor(InstantTranslationFailure.PARTIAL_FAILURE))
        assertEquals(ReaderTranslationState.Failed(ReaderTranslationFailure.STORAGE, true), reasonFor(InstantTranslationFailure.DATABASE_FAILURE))
        assertEquals(ReaderTranslationState.Failed(ReaderTranslationFailure.INVALID_CONTENT, false), reasonFor(InstantTranslationFailure.EMPTY_INPUT))
        assertEquals(ReaderTranslationState.Failed(ReaderTranslationFailure.INVALID_CONTENT, false), reasonFor(InstantTranslationFailure.INVALID_CHUNK))
        assertEquals(ReaderTranslationState.Failed(ReaderTranslationFailure.UNSUPPORTED_LANGUAGE, false), reasonFor(InstantTranslationFailure.UNSUPPORTED_LANGUAGE))
        assertEquals(ReaderTranslationState.Processing(), reasonFor(InstantTranslationFailure.DUPLICATE_REQUEST))
    }

    @Test fun originalTextRemainsAvailableOnEveryNonCompletedState() {
        val f = Fixture(runnerBody = { _, _ -> InstantTranslationOutcome.Failed(InstantTranslationFailure.PROVIDER_FAILURE) })
        val content = c1Chapter(1)
        f.open(content)
        val seen = ArrayList<ReaderTranslationState?>()
        seen += f.state
        f.controller.start(content); seen += f.state
        f.executor.runAll(); seen += f.state
        seen.forEach { s ->
            val display = ReaderTranslationDisplay.of(s)
            assertTrue(!display.canShowTranslation)
            assertEquals(ReaderViewMode.ORIGINAL, display.effectiveMode(ReaderViewMode.TRANSLATION))
        }
        assertEquals(2, content.paragraphs.size) // the extracted original is untouched
    }

    @Test fun exceptionsBecomeAFailedStateNotACrash() {
        val f = Fixture(runnerBody = { _, _ -> error("boom") })
        val content = c1Chapter(1)
        f.open(content)
        f.controller.start(content)
        f.executor.runAll()
        assertEquals(ReaderTranslationState.Failed(ReaderTranslationFailure.UNKNOWN, canRetry = true), f.state)
    }

    @Test fun novelProblemsDoNotReachThePipeline() {
        val mismatch = Fixture(novelResult = NovelEnsureResult.SOURCE_LANGUAGE_MISMATCH)
        val failed = Fixture(novelResult = NovelEnsureResult.FAILED)
        val c1 = c1Chapter(1)
        mismatch.open(c1); mismatch.controller.start(c1); mismatch.executor.runAll()
        failed.open(c1); failed.controller.start(c1); failed.executor.runAll()
        assertEquals(unavailable(ReaderTranslationUnavailableReason.NO_CHAPTER_IDENTITY), mismatch.state)
        assertEquals(ReaderTranslationState.Failed(ReaderTranslationFailure.STORAGE, true), failed.state)
        assertEquals(0, mismatch.runCalls + failed.runCalls)
    }

    // ---- request rules -----------------------------------------------------------------------

    @Test fun cannotStartWhileRunningOrAfterCompletion() {
        val f = Fixture()
        val content = c1Chapter(1)
        f.open(content)
        assertEquals(StartResult.STARTED, f.controller.start(content))
        assertEquals(StartResult.NOT_ALLOWED, f.controller.start(content)) // already Processing
        assertEquals(1, f.executor.pending)
        f.executor.runAll()
        assertEquals(StartResult.NOT_ALLOWED, f.controller.start(content)) // already Completed
    }

    @Test fun nonRetryableFailureCannotBeRequestedAgain() {
        val f = Fixture(runnerBody = { _, _ -> InstantTranslationOutcome.Failed(InstantTranslationFailure.EMPTY_INPUT) })
        val content = c1Chapter(1)
        f.open(content)
        f.controller.start(content)
        f.executor.runAll()
        assertEquals(StartResult.NOT_ALLOWED, f.controller.start(content))
    }

    @Test fun requestRulesPerState() {
        assertTrue(ReaderTranslationState.OriginalOnly.canRequestTranslation)
        assertTrue(ReaderTranslationState.Failed(ReaderTranslationFailure.PROVIDER, true).canRequestTranslation)
        assertTrue(!ReaderTranslationState.Failed(ReaderTranslationFailure.INVALID_CONTENT, false).canRequestTranslation)
        assertTrue(!ReaderTranslationState.Processing().canRequestTranslation)
        assertTrue(!ReaderTranslationState.Pending().canRequestTranslation)
        assertTrue(!ReaderTranslationState.Completed("x", "p").canRequestTranslation)
        assertTrue(!unavailable(ReaderTranslationUnavailableReason.NO_CHAPTER_IDENTITY).canRequestTranslation)
        assertTrue(unavailable(ReaderTranslationUnavailableReason.NOT_CONFIGURED).canRequestTranslation)
    }

    // ---- navigation and stale results --------------------------------------------------------

    @Test fun chapterNavigationDuringTranslationCancelsAndNeverLeaksIntoTheNewChapter() {
        val f = Fixture(runnerBody = { r, isCancelled ->
            if (isCancelled()) InstantTranslationOutcome.Cancelled(1, 0) else c1Completed(c1Stored(r.chapterId))
        })
        val ch1 = c1Chapter(1)
        val ch2 = c1Chapter(2)
        f.open(ch1)
        f.controller.start(ch1)
        f.open(ch2)                       // reader moves on while chapter 1 is still queued
        f.executor.runAll()
        assertEquals(true, f.cancelledSeenAtRun)
        assertEquals(BoundChapterIdentityResolver.chapterIdFor(C1_NOVEL, 2), f.holder.current?.identity?.chapterId)
        assertEquals(ReaderTranslationState.OriginalOnly, f.state)
    }

    @Test fun staleResultIsIgnoredEvenIfThePipelineIgnoresCancellation() {
        val f = Fixture() // always returns Completed, ignoring isCancelled
        val ch1 = c1Chapter(1)
        val ch2 = c1Chapter(2)
        f.open(ch1)
        f.controller.start(ch1)
        f.open(ch2)
        f.executor.runAll()
        assertEquals(ReaderTranslationState.OriginalOnly, f.state)
        assertNull(f.holder.current?.state.takeIf { it is ReaderTranslationState.Completed })
    }

    @Test fun lateResultAfterTheReaderClosedIsIgnored() {
        val f = Fixture()
        val ch1 = c1Chapter(1)
        f.open(ch1)
        f.controller.start(ch1)
        f.holder.onChapterClosed()
        f.executor.runAll()
        assertNull(f.holder.current)
    }

    // ---- database result refresh -------------------------------------------------------------

    @Test fun storedTranslationIsPickedUpWhenTheChapterIsShown() {
        val f = Fixture(gateway = StoredTranslationReaderGateway({ c1Stored(it) }, { false }))
        f.open(c1Chapter(3))
        f.controller.onChapterShown()
        assertEquals(ReaderTranslationState.OriginalOnly, f.state) // not applied on the caller's thread
        f.executor.runAll()
        assertEquals(ReaderTranslationState.Completed("မြန်မာစာသား", "fake-real"), f.state)
    }

    @Test fun storedMockTranslationIsNotShownOnRefresh() {
        val f = Fixture(gateway = StoredTranslationReaderGateway({ c1Stored(it, isMock = true) }, { false }))
        f.open(c1Chapter(3))
        f.controller.onChapterShown()
        f.executor.runAll()
        assertEquals(unavailable(ReaderTranslationUnavailableReason.MOCK_ONLY), f.state)
    }

    @Test fun refreshDoesNotOverwriteWorkInProgressOrAnotherChapter() {
        val f = Fixture(gateway = StoredTranslationReaderGateway({ null }, { false }))
        val ch1 = c1Chapter(1)
        f.open(ch1)
        f.controller.start(ch1)           // Processing
        f.controller.onChapterShown()     // gateway says OriginalOnly
        f.open(c1Chapter(2))         // navigation before the queued refresh/work run
        f.executor.runAll()
        assertEquals(ReaderTranslationState.OriginalOnly, f.state)
        assertEquals(BoundChapterIdentityResolver.chapterIdFor(C1_NOVEL, 2), f.holder.current?.identity?.chapterId)
    }

    @Test fun refreshForUnidentifiedChapterDoesNothing() {
        val f = Fixture()
        f.open(c1Chapter(null, title = "Prologue"))
        f.controller.onChapterShown()
        assertEquals(0, f.executor.pending)
        assertNotNull(f.holder.current)
    }
}
