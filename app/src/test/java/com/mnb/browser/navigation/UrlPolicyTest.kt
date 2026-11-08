package com.mnb.browser.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlPolicyTest {
    private fun ok(s: String) = (UrlPolicy.resolve(s) as UrlPolicy.Input.Ok).url
    private fun input(s: String) = UrlPolicy.resolve(s) as UrlPolicy.Input.Ok

    @Test fun bareDomainGetsHttps() = assertEquals("https://example.com", ok("example.com"))
    @Test fun httpIsUpgraded() = assertEquals("https://example.com/a", ok("http://example.com/a"))
    @Test fun httpsKept() = assertEquals("https://example.com/", ok("https://example.com/"))
    @Test fun hostWithPort() = assertEquals("https://localhost:8080", ok("localhost:8080"))
    @Test fun emptyIsEmpty() = assertEquals(UrlPolicy.Input.Empty, UrlPolicy.resolve("   "))

    @Test fun textBecomesGoogleSearch() =
        assertEquals("https://www.google.com/search?q=hello+world", ok("hello world"))

    @Test fun searchEngineIsGoogleNotDuckDuckGo() {
        assertTrue(UrlPolicy.SEARCH_URL.startsWith("https://www.google.com/search?q="))
        assertFalse(ok("hello world").contains("duckduckgo"))
    }

    @Test fun searchIsFlaggedAsSearchAndAddressIsNot() {
        assertTrue(input("hello world").isSearch)
        assertFalse(input("example.com").isSearch)
        assertFalse(input("https://example.com/a").isSearch)
    }

    @Test fun searchQueryIsPercentEncoded() {
        assertEquals("https://www.google.com/search?q=a%26b%3Dc%3Fd%23e", ok("a&b=c?d#e"))
        assertEquals("https://www.google.com/search?q=c%2B%2B+%E2%82%AC", ok("c++ €"))
    }

    @Test fun burmeseQueryIsUtf8Encoded() {
        // "novel" in Burmese
        assertEquals(
            "https://www.google.com/search?q=%E1%80%9D%E1%80%90%E1%80%B9%E1%80%91%E1%80%AF",
            ok("ဝတ္ထု"),
        )
        assertTrue(input("ဝတ္ထု ဖတ်").isSearch)
    }

    @Test fun searchQueryCannotInjectIntoSearchUrl() {
        val url = ok("x&q=evil&hl=en")
        assertEquals("https://www.google.com/search?q=x%26q%3Devil%26hl%3Den", url)
    }

    @Test fun addressWithPathAndQueryIsAnAddress() {
        val result = input("example.com/a%20b")
        assertFalse(result.isSearch)
        assertEquals("https://example.com/a%20b", result.url)
        assertFalse(input("royalroad.com/fictions/best-rated?genre=fantasy").isSearch)
    }

    @Test fun ipAddressIsAnAddress() {
        assertEquals("https://192.168.0.1", ok("192.168.0.1"))
        assertFalse(input("192.168.0.1:8080/x").isSearch)
    }

    @Test fun nonAddressTextWithDotsIsASearch() {
        assertTrue(input("3.14").isSearch)
        assertTrue(input("v1.2.3").isSearch)
        assertTrue(input("e.g.").isSearch)
        assertTrue(input("harry potter.com review").isSearch)
        assertTrue(input("someone@example.com").isSearch)
    }

    @Test fun addressWithUserInfoIsNotOpenedAsAddress() {
        assertTrue(input("good.com@evil.com").isSearch)
    }

    @Test fun uppercaseHttpStillUpgraded() = assertEquals("https://Example.com/A", ok("HTTP://Example.com/A"))

    @Test fun httpsUpgradeNotWeakenedBySearchChange() {
        assertEquals("https://example.com/a", ok("http://example.com/a"))
        assertEquals(UrlPolicy.Decision.Upgrade("https://a.com/x"), UrlPolicy.checkNavigation("http://a.com/x"))
    }

    @Test fun homeUrlIsGoogleAndNewTabsHaveTheirOwnAddress() {
        assertEquals("https://www.google.com/", UrlPolicy.HOME_URL)
        assertFalse(UrlPolicy.isNewTabAddress(UrlPolicy.HOME_URL))
        assertTrue(UrlPolicy.isNewTabAddress(UrlPolicy.NEW_TAB_URL))
    }

    @Test fun dangerousSchemesBlocked() {
        listOf("javascript:alert(1)", "file:///sdcard/x", "data:text/html,hi", "intent://x", "ftp://a.com", "mailto:a@b.c")
            .forEach { assertTrue(it, UrlPolicy.resolve(it) is UrlPolicy.Input.Blocked) }
    }

    @Test fun otherSchemesWithSlashesBlocked() =
        assertTrue(UrlPolicy.resolve("ws://example.com") is UrlPolicy.Input.Blocked)

    @Test fun navigationDecisions() {
        assertEquals(UrlPolicy.Decision.Allow, UrlPolicy.checkNavigation("https://a.com"))
        assertEquals(UrlPolicy.Decision.Upgrade("https://a.com"), UrlPolicy.checkNavigation("http://a.com"))
        assertEquals(UrlPolicy.Decision.Block, UrlPolicy.checkNavigation("intent://x"))
    }

    @Test fun googleResultPageIsAllowedToLoad() =
        assertEquals(UrlPolicy.Decision.Allow, UrlPolicy.checkNavigation(ok("hello world")))
}
