package com.mnb.browser.library

import com.mnb.browser.navigation.UrlPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageRulesTest {
    @Test fun normalHttpsPagesAreRecordable() {
        assertTrue(PageRules.isRecordable("https://example.com/a?b=c#d"))
        assertTrue(PageRules.isRecordable("https://example.com"))
        assertTrue(PageRules.isRecordable("HTTPS://Example.com/Path"))
        assertTrue(PageRules.isRecordable("https://example.com:8443/x"))
        // A character java.net.URI would reject must not hide a real page.
        assertTrue(PageRules.isRecordable("https://example.com/a|b"))
    }

    @Test fun blankAndNullAreNotRecordable() {
        assertFalse(PageRules.isRecordable(null))
        assertFalse(PageRules.isRecordable(""))
        assertFalse(PageRules.isRecordable("   "))
    }

    @Test fun nonHttpsSchemesAreNotRecordable() {
        assertFalse(PageRules.isRecordable("http://example.com/"))
        assertFalse(PageRules.isRecordable("about:blank"))
        assertFalse(PageRules.isRecordable("javascript:alert(1)"))
        assertFalse(PageRules.isRecordable("file:///sdcard/a.html"))
        assertFalse(PageRules.isRecordable("data:text/html,hi"))
        assertFalse(PageRules.isRecordable("example.com"))
    }

    @Test fun addressWithoutHostIsNotRecordable() {
        assertFalse(PageRules.isRecordable("https://"))
        assertFalse(PageRules.isRecordable("https:///path"))
        assertFalse(PageRules.isRecordable("https://:443/"))
        assertFalse(PageRules.isRecordable("https://exa mple.com/"))
        assertFalse(PageRules.isRecordable("https://user@/x"))
    }

    @Test fun newTabPageIsNotRecordable() {
        assertFalse(PageRules.isRecordable(UrlPolicy.NEW_TAB_URL))
        assertFalse(PageRules.isRecordable(UrlPolicy.NEW_TAB_URL + "/"))
        assertFalse(PageRules.isRecordable(UrlPolicy.NEW_TAB_URL.uppercase()))
        // The same address with a query is not the new-tab page.
        assertTrue(PageRules.isRecordable(UrlPolicy.NEW_TAB_URL + "?q=kotlin"))
        // Google itself is a real website, not the new-tab page.
        assertTrue(PageRules.isRecordable(UrlPolicy.HOME_URL))
    }

    @Test fun veryLongAddressIsNotRecordable() {
        val longUrl = "https://example.com/" + "a".repeat(PageRules.MAX_URL_LENGTH)
        assertFalse(PageRules.isRecordable(longUrl))
    }

    @Test fun isNewTabRecognisesBlankAndHome() {
        assertTrue(PageRules.isNewTab(null))
        assertTrue(PageRules.isNewTab(""))
        assertTrue(PageRules.isNewTab(UrlPolicy.NEW_TAB_URL))
        assertTrue(PageRules.isNewTab("about:blank"))
        assertFalse(PageRules.isNewTab(UrlPolicy.HOME_URL))
        assertFalse(PageRules.isNewTab("https://example.com/"))
    }
}
