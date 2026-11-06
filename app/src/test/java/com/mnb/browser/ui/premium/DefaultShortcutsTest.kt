package com.mnb.browser.ui.premium

import com.mnb.browser.navigation.UrlPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultShortcutsTest {

    private val sites = DefaultShortcuts.sites

    @Test fun hasBundledSites() = assertTrue(sites.size >= 4)

    @Test fun namesAndUrlsAreUnique() {
        assertEquals(sites.size, sites.map { it.name }.toSet().size)
        assertEquals(sites.size, sites.map { DefaultShortcuts.hostOf(it.url) }.toSet().size)
    }

    @Test fun everyShortcutHasNameMonogramAndColour() {
        sites.forEach {
            assertTrue(it.name, it.name.isNotBlank())
            assertTrue(it.name, it.monogram.length in 1..2)
            // Opaque colour (alpha 0xFF) so white text on the tile is readable.
            assertEquals(it.name, 0xFFL, (it.badgeColor shr 24) and 0xFFL)
        }
    }

    @Test fun everyUrlIsHttpsWithAHostAndOpensItself() {
        sites.forEach { site ->
            assertTrue(site.url, site.url.startsWith("https://"))
            val resolved = UrlPolicy.resolve(site.url)
            assertTrue(site.url, resolved is UrlPolicy.Input.Ok)
            resolved as UrlPolicy.Input.Ok
            // Opening the tile goes to exactly this site and is an address, not a search.
            assertEquals(site.url, resolved.url)
            assertFalse(site.url, resolved.isSearch)
            assertEquals(UrlPolicy.Decision.Allow, UrlPolicy.checkNavigation(resolved.url))
        }
    }

    @Test fun eachTileOpensTheExpectedWebsite() {
        val expected = mapOf(
            "Wattpad" to "www.wattpad.com",
            "Royal Road" to "www.royalroad.com",
            "Wuxiaworld" to "www.wuxiaworld.com",
            "Project Gutenberg" to "www.gutenberg.org",
            "Standard Ebooks" to "standardebooks.org",
        )
        assertEquals(expected.keys, sites.map { it.name }.toSet())
        sites.forEach { site ->
            val url = (UrlPolicy.resolve(site.url) as UrlPolicy.Input.Ok).url
            assertEquals(site.name, expected.getValue(site.name), java.net.URI(url).host)
        }
    }

    @Test fun shortcutsMirrorSites() {
        assertEquals(sites.map { it.name }, DefaultShortcuts.shortcuts.map { it.name })
        assertEquals(sites.map { it.url }, DefaultShortcuts.shortcuts.map { it.url })
    }

    @Test fun styleLookupIgnoresSchemeWwwAndPath() {
        assertNotNull(DefaultShortcuts.styleFor("https://www.wattpad.com/"))
        assertNotNull(DefaultShortcuts.styleFor("https://wattpad.com/stories/new"))
        assertEquals("Wattpad", DefaultShortcuts.styleFor("http://WWW.Wattpad.com")?.name)
        assertEquals("Royal Road", DefaultShortcuts.styleFor("https://royalroad.com")?.name)
    }

    @Test fun styleLookupDoesNotMatchOtherSites() {
        assertNull(DefaultShortcuts.styleFor("https://example.com"))
        assertNull(DefaultShortcuts.styleFor("https://notwattpad.com"))
        assertNull(DefaultShortcuts.styleFor(""))
    }

    @Test fun legacyDefaultsAreRecognisedOnlyWhenUntouched() {
        val legacy = listOf(
            Shortcut("DuckDuckGo", "https://duckduckgo.com"),
            Shortcut("Wikipedia", "https://www.wikipedia.org"),
            Shortcut("GitHub", "https://github.com"),
        )
        assertTrue(DefaultShortcuts.isLegacyDefault(legacy))
        assertFalse(DefaultShortcuts.isLegacyDefault(legacy + Shortcut("Mine", "https://example.com")))
        assertFalse(DefaultShortcuts.isLegacyDefault(legacy.drop(1)))
        assertFalse(DefaultShortcuts.isLegacyDefault(DefaultShortcuts.shortcuts))
        assertFalse(DefaultShortcuts.isLegacyDefault(emptyList()))
    }
}
