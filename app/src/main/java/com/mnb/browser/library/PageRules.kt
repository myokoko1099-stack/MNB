package com.mnb.browser.library

import com.mnb.browser.navigation.UrlPolicy

/**
 * Which pages may be stored as a bookmark or as a history visit (pure Kotlin, unit-testable).
 *
 * Only complete https pages with a host qualify. The blank/new-tab page and anything else the
 * browser never really loads (javascript:, about:, file:, http:) is rejected.
 */
object PageRules {
    private const val HTTPS_PREFIX_LENGTH = 8 // "https://"
    const val MAX_URL_LENGTH = 4096

    /** True for the empty address and for the new-tab (home) address. */
    fun isNewTab(url: String?): Boolean {
        if (url.isNullOrBlank()) return true
        return url.trim().trimEnd('/').equals(UrlPolicy.HOME_URL.trimEnd('/'), ignoreCase = true)
    }

    /** True when [url] is a real page worth a history entry or a bookmark. */
    fun isRecordable(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        if (url.length > MAX_URL_LENGTH) return false
        if (UrlPolicy.checkNavigation(url) != UrlPolicy.Decision.Allow) return false
        if (!hasHost(url)) return false
        return !isNewTab(url)
    }

    /** Host check without java.net.URI, which rejects many valid page addresses (for example "|"). */
    private fun hasHost(url: String): Boolean {
        val authority = url.substring(HTTPS_PREFIX_LENGTH).takeWhile { it != '/' && it != '?' && it != '#' }
        if (authority.isBlank() || authority.any { it.isWhitespace() }) return false
        val host = authority.substringAfterLast('@').substringBefore(':')
        return host.isNotBlank()
    }
}

/**
 * Stops one page visit from being stored several times. A page can finish loading more than once
 * (redirects, scripts that re-render, repeated callbacks), so the same address in the same tab is
 * only stored again after [windowMillis]. Main thread only; it keeps no page contents, only the last
 * address per tab.
 */
class VisitDeduplicator(private val windowMillis: Long = DEFAULT_WINDOW_MILLIS) {
    private class Last(val key: String, val atMillis: Long)

    private val lastByTab = HashMap<String, Last>()

    /** True when this visit should be stored. It is then remembered as the tab's latest visit. */
    fun accept(tabId: String, url: String, nowMillis: Long): Boolean {
        val key = url.substringBefore('#')
        val last = lastByTab[tabId]
        if (last != null && last.key == key && nowMillis - last.atMillis in 0 until windowMillis) return false
        lastByTab[tabId] = Last(key, nowMillis)
        return true
    }

    /** Forgets a closed tab. */
    fun forget(tabId: String) {
        lastByTab.remove(tabId)
    }

    companion object {
        const val DEFAULT_WINDOW_MILLIS = 30_000L
    }
}
