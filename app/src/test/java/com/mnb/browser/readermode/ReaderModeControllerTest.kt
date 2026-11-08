package com.mnb.browser.readermode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderModeControllerTest {
    private fun session(tabId: String = "tab-a", generation: Long = 1L, token: String = "doc-$generation") =
        PageSession(tabId, generation, "https://example.test/$generation", token)

    @Test
    fun extractionTranslationReadyFlowIsAcceptedForCurrentSession() {
        val controller = ReaderModeController()
        val current = session()
        controller.onPageSessionChanged("tab-a", current)

        val token = controller.beginExtraction("tab-a", current) ?: error("token not created")
        assertNotNull(token)
        assertEquals(ReaderModeState.EXTRACTING, controller.state("tab-a"))
        assertTrue(controller.beginTranslation("tab-a", current, token))
        assertTrue(controller.markReady("tab-a", current, token))
        assertEquals(ReaderModeState.READY, controller.state("tab-a"))
    }

    @Test
    fun staleResultFromOldPageIsRejected() {
        val controller = ReaderModeController()
        val old = session()
        controller.onPageSessionChanged("tab-a", old)
        val token = controller.beginExtraction("tab-a", old)!!

        val fresh = session(generation = 2L)
        controller.onPageSessionChanged("tab-a", fresh)

        assertFalse(controller.accepts("tab-a", old, token))
        assertFalse(controller.markReady("tab-a", old, token))
        assertEquals(ReaderModeState.OFF, controller.state("tab-a"))
    }

    @Test
    fun sameDocumentSpaNavigationKeepsReaderStateAndUpdatesSessionUrl() {
        val controller = ReaderModeController()
        val first = session()
        controller.onPageSessionChanged("tab-a", first)
        val token = controller.beginExtraction("tab-a", first)!!
        controller.beginTranslation("tab-a", first, token)

        val spa = first.copy(normalizedUrl = "https://example.test/part-2")
        controller.onPageSessionChanged("tab-a", spa)

        assertEquals(ReaderModeState.TRANSLATING, controller.state("tab-a"))
        assertTrue(controller.accepts("tab-a", spa, token))
        assertEquals(spa, controller.snapshot("tab-a")?.session)
    }

    @Test
    fun cancellationRejectsLateResults() {
        val controller = ReaderModeController()
        val current = session()
        controller.onPageSessionChanged("tab-a", current)
        val token = controller.beginExtraction("tab-a", current)!!

        assertNotNull(controller.cancel("tab-a"))
        assertEquals(ReaderModeState.CANCELLING, controller.state("tab-a"))
        assertFalse(controller.accepts("tab-a", current, token))
        assertFalse(controller.markError("tab-a", current, token))
        assertTrue(controller.finishCancellation("tab-a", token))
        assertEquals(ReaderModeState.OFF, controller.state("tab-a"))
    }

    @Test
    fun tabClosingClearsOnlyThatTabAndCancelsItsWork() {
        val controller = ReaderModeController()
        val a = session("tab-a")
        val b = session("tab-b")
        controller.onPageSessionChanged("tab-a", a)
        controller.onPageSessionChanged("tab-b", b)
        val tokenA = controller.beginExtraction("tab-a", a)!!
        controller.beginExtraction("tab-b", b)

        controller.closeTab("tab-a")

        assertTrue(tokenA.isCancelled)
        assertNull(controller.snapshot("tab-a"))
        assertEquals(ReaderModeState.EXTRACTING, controller.state("tab-b"))
    }

    @Test
    fun webViewRecreationResetsStateAndInvalidatesToken() {
        val controller = ReaderModeController()
        val old = session()
        controller.onPageSessionChanged("tab-a", old)
        val token = controller.beginExtraction("tab-a", old)!!

        controller.onPageSessionChanged("tab-a", session(generation = 2L))

        assertTrue(token.isCancelled)
        assertEquals(ReaderModeState.OFF, controller.state("tab-a"))
        assertFalse(controller.accepts("tab-a", old, token))
    }
}
