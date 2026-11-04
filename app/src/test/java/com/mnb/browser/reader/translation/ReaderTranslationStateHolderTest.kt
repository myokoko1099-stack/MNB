package com.mnb.browser.reader.translation

import com.mnb.browser.extraction.ExtractedContent
import com.mnb.browser.extraction.ExtractedParagraph
import com.mnb.browser.reader.ReaderStateManager
import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.NovelId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reader translation state architecture (Phase 04-I10-A). */
class ReaderTranslationStateHolderTest {
    private val a = ChapterId("a")
    private val b = ChapterId("b")

    private fun content(title: String, url: String = "https://x.test/read") =
        ExtractedContent("page", title, url, listOf(ExtractedParagraph("text of $title")))

    /** Chapter title is the identity here; the URL is deliberately ignored. */
    private val resolver = ReaderChapterIdentityResolver { c ->
        c.chapterTitle?.takeIf { it != "unknown" }?.let { ReaderChapterIdentity(NovelId("n"), ChapterId(it), 1, c.url) }
    }

    private fun holder(gateway: ReaderTranslationGateway = UnavailableReaderTranslationGateway) =
        ReaderTranslationStateHolder(resolver, gateway)

    private fun ReaderTranslationStateHolder.state() = current!!.state

    private val completed = ReaderTranslationState.Completed("မြန်မာ", "real")

    // ---- states -------------------------------------------------------------------------

    @Test fun nothingOpenMeansNoState() {
        assertNull(holder().current)
    }

    @Test fun openedChapterStartsAsOriginalOnly() {
        val h = holder()
        h.onChapterOpened(content("a"))
        assertEquals(ReaderTranslationState.OriginalOnly, h.state())
        assertEquals(a, h.current!!.identity!!.chapterId)
    }

    @Test fun pendingState() {
        val h = holder(); h.onChapterOpened(content("a"))
        assertTrue(h.update(a, ReaderTranslationState.Pending(3)))
        assertEquals(ReaderTranslationState.Pending(3), h.state())
        assertTrue(h.state().isActive)
    }

    @Test fun processingState() {
        val h = holder(); h.onChapterOpened(content("a"))
        h.update(a, ReaderTranslationState.Processing(1, 4))
        assertEquals(ReaderTranslationState.Processing(1, 4), h.state())
        assertEquals(0.25f, (h.state() as ReaderTranslationState.Processing).fraction!!, 0.0001f)
        assertTrue(h.state().isActive)
    }

    @Test fun completedState() {
        val h = holder(); h.onChapterOpened(content("a"))
        h.update(a, completed)
        assertEquals(completed, h.state())
        assertTrue(h.state().hasTranslation)
        assertFalse(h.state().isActive)
    }

    @Test fun failedState() {
        val h = holder(); h.onChapterOpened(content("a"))
        val failed = ReaderTranslationState.Failed(ReaderTranslationFailure.PROVIDER, canRetry = true)
        h.update(a, failed)
        assertEquals(failed, h.state())
        assertFalse(h.state().hasTranslation)
    }

    @Test fun unidentifiedChapterIsUnavailableAndCannotBeUpdated() {
        val h = holder()
        h.onChapterOpened(content("unknown"))
        assertNull(h.current!!.identity)
        assertEquals(
            ReaderTranslationState.Unavailable(ReaderTranslationUnavailableReason.NO_CHAPTER_IDENTITY),
            h.state(),
        )
        assertFalse(h.update(ChapterId("unknown"), completed))
        assertFalse(h.state().hasTranslation)
    }

    @Test fun defaultResolverNeverGuessesAnIdentityFromTheUrl() {
        val h = ReaderTranslationStateHolder()
        h.onChapterOpened(content("a", "https://x.test/novel/1/chapter/5"))
        assertNull(h.current!!.identity)
        assertTrue(h.state() is ReaderTranslationState.Unavailable)
    }

    @Test fun invalidStatesAreRejected() {
        assertThrowsIae { ReaderTranslationState.Processing(5, 4) }
        assertThrowsIae { ReaderTranslationState.Processing(-1, 4) }
        assertThrowsIae { ReaderTranslationState.Pending(0) }
        assertThrowsIae { ReaderTranslationState.Completed(" ", "real") }
        assertNull(ReaderTranslationState.Processing(0, 0).fraction)
    }

    private fun assertThrowsIae(block: () -> Unit) {
        try { block(); throw AssertionError("expected IllegalArgumentException") } catch (e: IllegalArgumentException) { }
    }

    // ---- chapter isolation / duplicates ----------------------------------------------------

    @Test fun switchingChapterDoesNotCarryTheOldState() {
        val h = holder()
        h.onChapterOpened(content("a"))
        h.update(a, completed)
        h.onChapterOpened(content("b"))
        assertEquals(b, h.current!!.identity!!.chapterId)
        assertEquals(ReaderTranslationState.OriginalOnly, h.state())
    }

    @Test fun updateForAChapterThatIsNotOpenIsIgnored() {
        val h = holder()
        h.onChapterOpened(content("a"))
        h.onChapterOpened(content("b"))
        assertFalse(h.update(a, completed))
        assertEquals(ReaderTranslationState.OriginalOnly, h.state())
        assertEquals(b, h.current!!.identity!!.chapterId)
    }

    @Test fun updateAfterCloseIsIgnored() {
        val h = holder()
        h.onChapterOpened(content("a"))
        h.onChapterClosed()
        assertNull(h.current)
        assertFalse(h.update(a, completed))
        assertNull(h.current)
    }

    @Test fun sameUrlWithDifferentChapterIdsKeepsSeparateStates() {
        val h = holder()
        h.onChapterOpened(content("a", "https://x.test/same"))
        h.update(a, completed)
        h.onChapterOpened(content("b", "https://x.test/same"))
        assertEquals(ReaderTranslationState.OriginalOnly, h.state())
        assertFalse(h.update(a, completed))
    }

    @Test fun sameChapterFromAnotherUrlIsTheSameChapter() {
        val h = holder()
        h.onChapterOpened(content("a", "https://x.test/one"))
        assertTrue(h.update(a, ReaderTranslationState.Pending(1)))
        h.onChapterOpened(content("a", "https://m.x.test/two"))
        assertEquals(a, h.current!!.identity!!.chapterId)
        assertTrue(h.update(a, completed))
        assertEquals(completed, h.state())
    }

    @Test fun reopeningTheSameChapterKeepsASingleStateThatStartsFresh() {
        val h = holder()
        h.onChapterOpened(content("a")); h.update(a, completed)
        h.onChapterOpened(content("a"))
        assertEquals(ReaderTranslationState.OriginalOnly, h.state())
        h.update(a, ReaderTranslationState.Pending())
        assertEquals(ReaderTranslationState.Pending(), h.state())
    }

    // ---- refresh through the gateway ---------------------------------------------------------

    @Test fun refreshAppliesTheGatewayState() {
        val h = holder { ReaderTranslationState.Processing(2, 3) }
        h.onChapterOpened(content("a"))
        assertEquals(ReaderTranslationState.Processing(2, 3), h.refresh())
        assertEquals(ReaderTranslationState.Processing(2, 3), h.state())
    }

    @Test fun refreshAsksForTheOpenChapter() {
        val asked = mutableListOf<ChapterId>()
        val h = holder { identity -> asked += identity.chapterId; ReaderTranslationState.OriginalOnly }
        h.onChapterOpened(content("a")); h.refresh()
        h.onChapterOpened(content("b")); h.refresh()
        assertEquals(listOf(a, b), asked)
    }

    @Test fun refreshWithNothingOpenOrUnidentifiedDoesNothing() {
        var calls = 0
        val h = holder { calls++; ReaderTranslationState.OriginalOnly }
        assertNull(h.refresh())
        h.onChapterOpened(content("unknown"))
        assertNull(h.refresh())
        assertEquals(0, calls)
    }

    @Test fun staleRefreshResultFromAChapterTheReaderLeftIsDropped() {
        lateinit var h: ReaderTranslationStateHolder
        h = holder {
            h.onChapterOpened(content("b")) // reader moves on while the gateway is working on "a"
            completed
        }
        h.onChapterOpened(content("a"))
        assertNull(h.refresh())
        assertEquals(b, h.current!!.identity!!.chapterId)
        assertEquals(ReaderTranslationState.OriginalOnly, h.state())
    }

    @Test fun unavailableGatewayReportsNotConfigured() {
        val h = holder()
        h.onChapterOpened(content("a"))
        assertEquals(
            ReaderTranslationState.Unavailable(ReaderTranslationUnavailableReason.NOT_CONFIGURED),
            h.refresh(),
        )
        assertFalse(h.state().hasTranslation)
    }

    // ---- ReaderStateManager integration ------------------------------------------------------

    @Test fun readerStateManagerWorksWithoutTranslation() {
        val m = ReaderStateManager()
        val c = content("a")
        m.open(c); assertEquals(c, m.content)
        m.close(); assertNull(m.content)
        assertNull(m.translation)
    }

    @Test fun readerStateManagerKeepsTranslationStateInStepWithTheOpenChapter() {
        val h = holder()
        val m = ReaderStateManager(h)
        m.open(content("a"))
        assertEquals(a, h.current!!.identity!!.chapterId)
        h.update(a, completed)
        m.open(content("b"))
        assertEquals(b, h.current!!.identity!!.chapterId)
        assertEquals(ReaderTranslationState.OriginalOnly, h.state())
        m.close()
        assertNull(h.current)
        assertNull(m.content)
    }
}
