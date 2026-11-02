package com.mnb.browser.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlPolicyTest {
private fun ok(s: String) = (UrlPolicy.resolve(s) as UrlPolicy.Input.Ok).url

@Test fun bareDomainGetsHttps() = assertEquals("https://example.com", ok("example.com"))
@Test fun httpIsUpgraded() = assertEquals("https://example.com/a", ok("http://example.com/a"))
@Test fun httpsKept() = assertEquals("https://example.com/", ok("https://example.com/"))
@Test fun hostWithPort() = assertEquals("https://localhost:8080", ok("localhost:8080"))
@Test fun textBecomesSearch() = assertTrue(ok("hello world").startsWith("https://duckduckgo.com/?q=hello"))
@Test fun emptyIsEmpty() = assertEquals(UrlPolicy.Input.Empty, UrlPolicy.resolve("   "))

@Test fun dangerousSchemesBlocked() {
    listOf("javascript:alert(1)", "file:///sdcard/x", "data:text/html,hi", "intent://x", "ftp://a.com", "mailto:a@b.c")
        .forEach { assertTrue(it, UrlPolicy.resolve(it) is UrlPolicy.Input.Blocked) }
}

@Test fun navigationDecisions() {
    assertEquals(UrlPolicy.Decision.Allow, UrlPolicy.checkNavigation("https://a.com"))
    assertEquals(UrlPolicy.Decision.Upgrade("https://a.com"), UrlPolicy.checkNavigation("http://a.com"))
    assertEquals(UrlPolicy.Decision.Block, UrlPolicy.checkNavigation("intent://x"))
}

}
