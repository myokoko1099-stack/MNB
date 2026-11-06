package com.mnb.browser.ui.premium

/**
 * A novel website bundled with the app. [badgeColor] is an ARGB value for the tile background
 * (white text on top) and [monogram] the 1-2 letters drawn on it. No network is used: the tile icon
 * is drawn from these two values, so it also works offline.
 */
data class NovelSite(
    val name: String,
    val url: String,
    val badgeColor: Long,
    val monogram: String,
)

/**
 * Build-time bundled default shortcuts for the home page. Fixed in code on purpose: no remote
 * config and no API. They are general reading sites; the user can still edit, delete or add tiles.
 */
object DefaultShortcuts {

    val sites: List<NovelSite> = listOf(
        NovelSite("Wattpad", "https://www.wattpad.com/", 0xFFD9480F, "W"),
        NovelSite("Royal Road", "https://www.royalroad.com/", 0xFF1F4E79, "RR"),
        NovelSite("Wuxiaworld", "https://www.wuxiaworld.com/", 0xFF8E2430, "Wx"),
        NovelSite("Project Gutenberg", "https://www.gutenberg.org/", 0xFF6B4F2A, "PG"),
        NovelSite("Standard Ebooks", "https://standardebooks.org/", 0xFF2F6B57, "SE"),
    )

    val shortcuts: List<Shortcut> = sites.map { Shortcut(it.name, it.url) }

    /** Tile style for [url] if it is one of the bundled sites (compared by host, ignoring "www."). */
    fun styleFor(url: String): NovelSite? {
        val host = hostOf(url) ?: return null
        return sites.firstOrNull { hostOf(it.url) == host }
    }

    /** True when [list] is exactly the three defaults of earlier versions (nobody customised them). */
    fun isLegacyDefault(list: List<Shortcut>): Boolean = list == LEGACY_DEFAULTS

    private val LEGACY_DEFAULTS = listOf(
        Shortcut("DuckDuckGo", "https://duckduckgo.com"),
        Shortcut("Wikipedia", "https://www.wikipedia.org"),
        Shortcut("GitHub", "https://github.com"),
    )

    internal fun hostOf(url: String): String? {
        val withoutScheme = url.trim().lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
        val host = withoutScheme
            .substringBefore('/').substringBefore('?').substringBefore('#')
            .removePrefix("www.")
        return host.ifBlank { null }
    }
}
