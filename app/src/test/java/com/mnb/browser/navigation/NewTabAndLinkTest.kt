package com.mnb.browser.navigation

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 2G: the new-tab address, Google as the only search/home engine, and links opened in a new tab. */
class NewTabAndLinkTest {

    // ---- new-tab address ----

    @Test fun onlyBlankAboutBlankAndTheNewTabAddressAreNewTabs() {
        listOf(
            null, "", "   ", "about:blank", "ABOUT:BLANK", UrlPolicy.NEW_TAB_URL,
            UrlPolicy.NEW_TAB_URL + "/", UrlPolicy.NEW_TAB_URL.uppercase(), "  " + UrlPolicy.NEW_TAB_URL + " ",
        ).forEach { assertTrue("$it", UrlPolicy.isNewTabAddress(it)) }
        listOf(
            UrlPolicy.HOME_URL, UrlPolicy.SEARCH_URL + "kotlin", "https://example.com/",
            UrlPolicy.NEW_TAB_URL + "?x=1", "about:config",
        ).forEach { assertFalse("$it", UrlPolicy.isNewTabAddress(it)) }
    }

    @Test fun googleIsARealWebsiteAndNeverTheNewTabPage() {
        assertFalse(UrlPolicy.isNewTabAddress("https://www.google.com/"))
        assertFalse(UrlPolicy.isNewTabAddress(UrlPolicy.searchUrl("hello")))
    }

    @Test fun theNewTabAddressCanNeverReachARealSite() {
        assertTrue(URI(UrlPolicy.NEW_TAB_URL).host.endsWith(".invalid"))
        // It passes the URL policy, so tab storage keeps it as it is.
        assertEquals(UrlPolicy.Decision.Allow, UrlPolicy.checkNavigation(UrlPolicy.NEW_TAB_URL))
    }

    @Test fun aBlankPageIsReportedAsTheNewTabAddress() {
        assertEquals(UrlPolicy.NEW_TAB_URL, UrlPolicy.reportedAddress("about:blank"))
        assertEquals(UrlPolicy.NEW_TAB_URL, UrlPolicy.reportedAddress(" About:Blank "))
        assertEquals("https://a.test/", UrlPolicy.reportedAddress("https://a.test/"))
        assertNull(UrlPolicy.reportedAddress(null))
    }

    // ---- Google only ----

    @Test fun homeSearchAndNewTabNeverPointToDuckDuckGo() {
        for (url in listOf(UrlPolicy.HOME_URL, UrlPolicy.SEARCH_URL, UrlPolicy.NEW_TAB_URL, UrlPolicy.searchUrl("x y"))) {
            assertFalse(url, url.contains("duckduckgo", ignoreCase = true))
        }
        assertTrue(UrlPolicy.HOME_URL.startsWith("https://www.google.com/"))
        assertEquals("Google", UrlPolicy.SEARCH_ENGINE_NAME)
    }

    @Test fun everyTypedQueryGoesToGoogle() {
        listOf("hello", "hello world", "ဝတ္ထု", "a&b", "3.14", "someone@example.com").forEach { text ->
            val url = (UrlPolicy.resolve(text) as UrlPolicy.Input.Ok).url
            assertTrue(url, url.startsWith(UrlPolicy.SEARCH_URL))
        }
    }

    @Test fun typingAnAddressStillOpensItDirectly() {
        assertEquals("https://example.com", (UrlPolicy.resolve("example.com") as UrlPolicy.Input.Ok).url)
        // Direct entry is not restricted to Google: any https address, also an old favourite, still opens.
        assertEquals(
            "https://duckduckgo.com/",
            (UrlPolicy.resolve("https://duckduckgo.com/") as UrlPolicy.Input.Ok).url,
        )
    }

    @Test fun oldHomeAddressIsRecognisedOnlyForMigration() {
        assertTrue(UrlPolicy.isLegacyHomeUrl("https://duckduckgo.com/"))
        assertTrue(UrlPolicy.isLegacyHomeUrl("https://duckduckgo.com"))
        assertTrue(UrlPolicy.isLegacyHomeUrl("HTTPS://DuckDuckGo.com/"))
        assertFalse(UrlPolicy.isLegacyHomeUrl("https://duckduckgo.com/?q=kotlin"))
        assertFalse(UrlPolicy.isLegacyHomeUrl(UrlPolicy.HOME_URL))
        assertFalse(UrlPolicy.isLegacyHomeUrl(null))
    }

    // ---- links opened in a new tab ----

    @Test fun httpsLinksAreAcceptedAsTheyAre() {
        assertEquals("https://a.test/x?y=1#z", UrlPolicy.linkTarget("https://a.test/x?y=1#z"))
        assertEquals("https://a.test/x", UrlPolicy.linkTarget("  https://a.test/x \n"))
        assertEquals("https://a.test/a|b", UrlPolicy.linkTarget("https://a.test/a|b"))
    }

    @Test fun httpLinksAreUpgraded() {
        assertEquals("https://a.test/x", UrlPolicy.linkTarget("http://a.test/x"))
        assertEquals("https://A.test/x", UrlPolicy.linkTarget("HTTP://A.test/x"))
    }

    @Test fun unsafeSchemesAreRefused() {
        listOf(
            "javascript:alert(1)", "intent://x#Intent;end", "file:///sdcard/a", "data:text/html,hi",
            "content://x/y", "mailto:a@b.c", "tel:123", "about:blank", "blob:https://a.test/1", "ftp://a.test/",
            "market://details?id=x", "view-source:https://a.test/", "a.test/page",
        ).forEach { assertNull(it, UrlPolicy.linkTarget(it)) }
    }

    @Test fun brokenLinksAreRefused() {
        listOf(null, "", "   ", "https://", "https:///x", "https://:443/", "https://user@/x", "https://exa mple.com/")
            .forEach { assertNull("$it", UrlPolicy.linkTarget(it)) }
    }

    @Test fun theNewTabAddressIsNotALinkTarget() {
        assertNull(UrlPolicy.linkTarget(UrlPolicy.NEW_TAB_URL))
        assertNull(UrlPolicy.linkTarget(UrlPolicy.NEW_TAB_URL.uppercase()))
    }

    @Test fun veryLongLinksAreRefused() {
        val prefix = "https://example.com/"
        assertEquals(4096, UrlPolicy.linkTarget(prefix + "a".repeat(4096 - prefix.length))?.length)
        assertNull(UrlPolicy.linkTarget(prefix + "a".repeat(4097 - prefix.length)))
    }
}
