package com.mnb.browser.readermode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageSessionManagerTest {
    private fun manager(): PageSessionManager {
        var token = 0
        return PageSessionManager { "doc-${++token}" }
    }

    @Test
    fun sameUrlReloadGetsNewDocumentIdentity() {
        val manager = manager()
        val first = manager.onPageStarted("tab-a", "https://example.test/story")
        val second = manager.onReload("tab-a", "https://example.test/story")

        assertEquals(1L, first.navGeneration)
        assertEquals(2L, second.navGeneration)
        assertNotEquals(first.documentToken, second.documentToken)
        assertTrue(!first.sameDocumentAs(second))
    }

    @Test
    fun duplicatePageFinishedDoesNotCreateAnotherDocument() {
        val manager = manager()
        val started = manager.onPageStarted("tab-a", "https://example.test/story")
        val finished = manager.onPageFinished("tab-a", "https://example.test/story")
        val duplicate = manager.onPageFinished("tab-a", "https://example.test/story")

        assertTrue(started.sameDocumentAs(finished))
        assertTrue(finished.sameDocumentAs(duplicate))
        assertEquals(1L, duplicate.navGeneration)
    }

    @Test
    fun spaPushStateAndFragmentNavigationPreserveDocumentIdentity() {
        val manager = manager()
        val first = manager.onPageStarted("tab-a", "https://example.test/story")
        val spa = manager.onHistoryUpdated("tab-a", "https://example.test/story/part-2", false)
        val fragment = manager.onHistoryUpdated("tab-a", "https://example.test/story/part-2#top", false)

        assertTrue(first.sameDocumentAs(spa))
        assertTrue(spa.sameDocumentAs(fragment))
        assertEquals("https://example.test/story/part-2#top", fragment.normalizedUrl)
    }

    @Test
    fun historyCallbackWithReloadCreatesNewIdentity() {
        val manager = manager()
        val first = manager.onPageStarted("tab-a", "https://example.test/story")
        val reloaded = manager.onHistoryUpdated("tab-a", "https://example.test/story", true)

        assertTrue(!first.sameDocumentAs(reloaded))
        assertEquals(2L, reloaded.navGeneration)
    }

    @Test
    fun tabSwitchingPreservesEachTabSession() {
        val manager = manager()
        val a = manager.onPageStarted("tab-a", "https://a.test/")
        val b = manager.onPageStarted("tab-b", "https://b.test/")

        assertTrue(a.sameDocumentAs(manager.current("tab-a")))
        assertTrue(b.sameDocumentAs(manager.current("tab-b")))
    }

    @Test
    fun closingTabClearsSessionAndRestartsGenerationForReusedId() {
        val manager = manager()
        manager.onPageStarted("tab-a", "https://a.test/")
        manager.closeTab("tab-a")
        assertNull(manager.current("tab-a"))

        val reopened = manager.onPageStarted("tab-a", "https://a.test/")
        assertEquals(1L, reopened.navGeneration)
    }

    @Test
    fun webViewRecreationInvalidatesOldSession() {
        val manager = manager()
        val old = manager.onPageStarted("tab-a", "https://a.test/")
        manager.onWebViewUnloaded("tab-a")
        assertNull(manager.current("tab-a"))

        val recreated = manager.onPageStarted("tab-a", "https://a.test/")
        assertEquals(2L, recreated.navGeneration)
        assertTrue(!old.sameDocumentAs(recreated))
    }

    @Test
    fun rendererCrashInvalidatesOldSession() {
        val manager = manager()
        val old = manager.onPageStarted("tab-a", "https://a.test/")
        manager.onRendererCrashed("tab-a")
        val recreated = manager.onPageStarted("tab-a", "https://a.test/")

        assertEquals(2L, recreated.navGeneration)
        assertTrue(!old.sameDocumentAs(recreated))
    }
}
