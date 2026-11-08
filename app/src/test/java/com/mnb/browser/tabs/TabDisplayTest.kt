package com.mnb.browser.tabs

import com.mnb.browser.navigation.UrlPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class TabDisplayTest {
    private fun tab(url: String, title: String = "") = TabInfo("t", url, title, 0L)

    @Test fun aTitleIsShownWhenThereIsOne() {
        assertEquals("My page", TabDisplay.label(tab("https://a.test/", "My page")))
    }

    @Test fun theHomePageWithoutATitleIsANewTab() {
        assertEquals("New tab", TabDisplay.label(tab(UrlPolicy.NEW_TAB_URL)))
    }

    @Test fun anAddressIsShownSimplifiedWhenThereIsNoTitle() {
        assertEquals("example.org/a/b", TabDisplay.label(tab("https://www.example.org/a/b")))
    }

    @Test fun theBadgeShowsTheCountAndCapsLargeOnes() {
        assertEquals("1", TabDisplay.countBadge(1))
        assertEquals("99", TabDisplay.countBadge(99))
        assertEquals("99+", TabDisplay.countBadge(100))
    }
}
