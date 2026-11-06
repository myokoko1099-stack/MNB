package com.mnb.browser.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserStateManagerTest {

    @Test fun switchingTabsShowsTheNewTabsOwnPageState() {
        val m = BrowserStateManager()
        m.onPageStarted("https://one.test/")
        m.onTitle("One")
        m.onHistoryChanged("https://one.test/", canGoBack = true, canGoForward = false)

        m.onTabSwitched("https://two.test/", "Two", canGoBack = false, canGoForward = true, progress = 100)

        val s = m.state
        assertEquals("https://two.test/", s.currentUrl)
        assertEquals("Two", s.title)
        assertFalse(s.canGoBack)
        assertTrue(s.canGoForward)
        assertFalse(s.isLoading)
        assertEquals(100, s.progress)
    }

    @Test fun switchingTabsDropsTheErrorOfThePreviousTab() {
        val m = BrowserStateManager()
        m.onError(BrowserError(ErrorKind.NETWORK, "Offline", "No connection", "https://one.test/"))
        m.onTabSwitched("https://two.test/", "", canGoBack = false, canGoForward = false, progress = 100)
        assertNull(m.state.error)
    }

    @Test fun aTabThatIsStillLoadingShowsAsLoading() {
        val m = BrowserStateManager()
        m.onTabSwitched("https://two.test/", "", canGoBack = false, canGoForward = false, progress = 40)
        assertTrue(m.state.isLoading)
        assertEquals(40, m.state.progress)
    }

    @Test fun theExistingEventsStillWork() {
        val m = BrowserStateManager()
        m.onPageStarted("https://a.test/")
        assertTrue(m.state.isLoading)
        m.onProgress(100)
        assertFalse(m.state.isLoading)
        m.onPageFinished("https://a.test/x")
        assertEquals("https://a.test/x", m.state.currentUrl)
    }
}
