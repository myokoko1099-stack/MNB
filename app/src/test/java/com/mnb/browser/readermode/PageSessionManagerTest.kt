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
        manager.onPageFinished("tab-a", "https://example.test/story")
        val reloaded = manager.onHistoryUpdated("tab-a", "https://example.test/story", true)

        assertTrue(!first.sameDocumentAs(reloaded))
        assertEquals(2L, reloaded.navGeneration)
    }

    @Test
    fun webViewReloadCallbackSequenceAdvancesIdentityExactlyOnce() {
        val manager = manager()
        val initial = manager.onPageStarted("tab-a", "https://example.test/story")
        manager.onPageFinished("tab-a", "https://example.test/story")

        val reloadStarted = manager.onPageStarted("tab-a", "https://example.test/story")
        val reloadHistory = manager.onHistoryUpdated("tab-a", "https://example.test/story", true)
        val reloadFinished = manager.onPageFinished("tab-a", "https://example.test/story")

        assertEquals(2L, reloadStarted.navGeneration)
        assertTrue(reloadStarted.sameDocumentAs(reloadHistory))
        assertTrue(reloadStarted.sameDocumentAs(reloadFinished))
        assertEquals(2L, reloadFinished.navGeneration)
        assertTrue(!initial.sameDocumentAs(reloadFinished))
    }

    @Test
    fun reloadThenSameUrlHistoryCallbackKeepsSingleTransition() {
        val manager = manager()
        manager.onPageStarted("tab-a", "https://example.test/story")
        manager.onPageFinished("tab-a", "https://example.test/story")

        val started = manager.onPageStarted("tab-a", "https://example.test/story")
        val history = manager.onHistoryUpdated("tab-a", "https://example.test/story", true)
        val finished = manager.onPageFinished("tab-a", "https://example.test/story")

        assertTrue(started.sameDocumentAs(history))
        assertTrue(started.sameDocumentAs(finished))
        assertEquals(2L, finished.navGeneration)
    }

    @Test
    fun reloadThenRedirectedHistoryCallbackKeepsIdentityAndUpdatesFinalUrl() {
        val manager = manager()
        manager.onPageStarted("tab-a", "https://example.test/start")
        manager.onPageFinished("tab-a", "https://example.test/start")

        val started = manager.onPageStarted("tab-a", "https://example.test/start")
        val redirected = manager.onHistoryUpdated("tab-a", "https://example.test/final", true)
        val finished = manager.onPageFinished("tab-a", "https://example.test/final")

        assertTrue(started.sameDocumentAs(redirected))
        assertTrue(started.sameDocumentAs(finished))
        assertEquals(2L, finished.navGeneration)
        assertEquals("https://example.test/final", finished.normalizedUrl)
    }

    @Test
    fun reloadWithMultipleRedirectCallbacksAdvancesGenerationOnlyOnce() {
        val manager = manager()
        manager.onPageStarted("tab-a", "https://example.test/start")
        manager.onPageFinished("tab-a", "https://example.test/start")

        val started = manager.onPageStarted("tab-a", "https://example.test/start")
        val redirect1 = manager.onHistoryUpdated("tab-a", "https://redirect.test/one", true)
        val redirect2 = manager.onHistoryUpdated("tab-a", "https://redirect.test/two", true)
        val finalUrl = manager.onHistoryUpdated("tab-a", "https://final.test/story", true)
        val finished = manager.onPageFinished("tab-a", "https://final.test/story")

        listOf(redirect1, redirect2, finalUrl, finished).forEach { assertTrue(started.sameDocumentAs(it)) }
        assertEquals(2L, finished.navGeneration)
        assertEquals("https://final.test/story", finished.normalizedUrl)
    }

    @Test
    fun normalNavigationRedirectChainCreatesOneNewDocument() {
        val manager = manager()
        manager.onPageStarted("tab-a", "https://example.test/old")
        manager.onPageFinished("tab-a", "https://example.test/old")

        val started = manager.onPageStarted("tab-a", "https://example.test/start")
        val redirectStart = manager.onPageStarted("tab-a", "https://redirect.test/middle")
        val history = manager.onHistoryUpdated("tab-a", "https://final.test/story", false)
        val finished = manager.onPageFinished("tab-a", "https://final.test/story")

        assertEquals(2L, started.navGeneration)
        assertTrue(started.sameDocumentAs(redirectStart))
        assertTrue(started.sameDocumentAs(history))
        assertTrue(started.sameDocumentAs(finished))
        assertEquals("https://final.test/story", finished.normalizedUrl)
    }

    @Test
    fun reloadHistoryBeforePageStartedStillProducesOneTransitionAcrossRedirect() {
        val manager = manager()
        manager.onPageStarted("tab-a", "https://example.test/story")
        manager.onPageFinished("tab-a", "https://example.test/story")

        val history = manager.onHistoryUpdated("tab-a", "https://redirect.test/start", true)
        val started = manager.onPageStarted("tab-a", "https://final.test/story")
        val finished = manager.onPageFinished("tab-a", "https://final.test/story")

        assertTrue(history.sameDocumentAs(started))
        assertTrue(started.sameDocumentAs(finished))
        assertEquals(2L, finished.navGeneration)
        assertEquals("https://final.test/story", finished.normalizedUrl)
    }

    @Test
    fun repeatedReloadHistoryAndFinishedCallbacksAreIdempotent() {
        val manager = manager()
        val started = manager.onPageStarted("tab-a", "https://example.test/story")
        val history1 = manager.onHistoryUpdated("tab-a", "https://example.test/story", true)
        val history2 = manager.onHistoryUpdated("tab-a", "https://example.test/story", true)
        val finished1 = manager.onPageFinished("tab-a", "https://example.test/story")
        val finished2 = manager.onPageFinished("tab-a", "https://example.test/story")

        assertTrue(started.sameDocumentAs(history1))
        assertTrue(started.sameDocumentAs(history2))
        assertTrue(started.sameDocumentAs(finished1))
        assertTrue(started.sameDocumentAs(finished2))
        assertEquals(finished1, finished2)
        assertEquals(1L, finished2.navGeneration)
    }

    @Test
    fun spaSameDocumentAndFragmentUpdatesPreserveIdentityIndependently() {
        val manager = manager()
        val initial = manager.onPageStarted("tab-a", "https://example.test/story")
        val spa = manager.onHistoryUpdated("tab-a", "https://example.test/story/part-2", false)
        val fragment = manager.onHistoryUpdated("tab-a", "https://example.test/story/part-2#section", false)

        assertTrue(initial.sameDocumentAs(spa))
        assertTrue(spa.sameDocumentAs(fragment))
        assertEquals("https://example.test/story/part-2#section", fragment.normalizedUrl)
        assertEquals(1L, fragment.navGeneration)
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
