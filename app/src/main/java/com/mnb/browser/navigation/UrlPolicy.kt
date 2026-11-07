package com.mnb.browser.navigation

import java.net.URI
import java.net.URLEncoder

/**
 * All URL safety / normalisation rules live here (pure Kotlin, unit-testable).
 *
 * Only https is ever loaded; http is upgraded to https. Other schemes are blocked.
 */
object UrlPolicy {
    /**
     * A real website (Google). It is only a fallback address, e.g. for a tab that crashed repeatedly
     * (Phase 2F); it is NOT the new-tab page, so a tab that shows it is a normal Google page.
     */
    const val HOME_URL = "https://www.google.com/"

    /**
     * Address of the app's own new-tab page. Nothing is ever requested from the network for it:
     * the WebView of such a tab is left empty (or shows about:blank) and the app draws the page.
     * ".invalid" can never resolve, so even a leak could not reach a real site.
     */
    const val NEW_TAB_URL = "https://mnb.invalid/new-tab"

    /** What a WebView reports after the new-tab page was "loaded" with [BLANK_PAGE]. */
    const val BLANK_PAGE = "about:blank"

    /** Old default home address, only used to migrate tabs stored by earlier versions. */
    private const val LEGACY_HOME_URL = "https://duckduckgo.com"

    private const val MAX_LINK_LENGTH = 4096

    const val SEARCH_ENGINE_NAME = "Google"
    const val SEARCH_URL = "https://www.google.com/search?q="

    sealed interface Input {
        /** [isSearch] is true when the text was a search query, false when it was an address. */
        data class Ok(val url: String, val isSearch: Boolean = false) : Input
        data class Blocked(val reason: String) : Input
        data object Empty : Input
    }

    sealed interface Decision {
        data object Allow : Decision
        data class Upgrade(val url: String) : Decision
        data object Block : Decision
    }

    private val BLOCKED_SCHEMES = setOf(
        "javascript", "data", "file", "content", "intent", "blob", "ftp",
        "vbscript", "about", "chrome", "view-source", "mailto", "tel", "sms", "market",
    )
    private val SCHEME = Regex("^([a-z][a-z0-9+.-]*):")
    private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

    /** dotted ASCII host whose last label is a TLD of 2+ chars starting with a letter ("3.14" is not one). */
    private val DOMAIN = Regex("^([a-z0-9]([a-z0-9-]*[a-z0-9])?\\.)+[a-z][a-z0-9-]*[a-z0-9]$")

    /** Interprets text typed in the address bar. */
    fun resolve(raw: String): Input {
        val text = raw.trim()
        if (text.isEmpty()) return Input.Empty
        val lower = text.lowercase()

        SCHEME.find(lower)?.groupValues?.get(1)?.let { scheme ->
            if (scheme in BLOCKED_SCHEMES) return Input.Blocked("The \"$scheme:\" scheme is not allowed.")
        }
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            val url = if (lower.startsWith("http://")) "https://" + text.substring(7) else text
            return if (hasHost(url)) Input.Ok(url) else Input.Blocked("That address is not valid.")
        }
        if (lower.contains("://")) return Input.Blocked("Only https:// addresses are supported.")

        if (looksLikeAddress(lower)) {
            val url = "https://$text"
            return if (hasHost(url)) Input.Ok(url) else Input.Blocked("That address is not valid.")
        }
        return Input.Ok(searchUrl(text), isSearch = true)
    }

    /** The search-engine results address for [query]; the query is UTF-8 percent-encoded. */
    fun searchUrl(query: String): String = SEARCH_URL + URLEncoder.encode(query, "UTF-8")

    /** True for no address, about:blank and the new-tab address: pages that are never a real website. */
    fun isNewTabAddress(url: String?): Boolean {
        if (url.isNullOrBlank()) return true
        val text = url.trim()
        return text.equals(BLANK_PAGE, ignoreCase = true) ||
            text.trimEnd('/').equals(NEW_TAB_URL, ignoreCase = true)
    }

    /** The address to tell tab and history code about: about:blank is reported as the new-tab address. */
    fun reportedAddress(url: String?): String? =
        if (url != null && url.trim().equals(BLANK_PAGE, ignoreCase = true)) NEW_TAB_URL else url

    /** True for the home address that earlier versions used for new tabs (stored tabs are migrated). */
    fun isLegacyHomeUrl(url: String?): Boolean =
        url != null && url.trim().trimEnd('/').equals(LEGACY_HOME_URL, ignoreCase = true)

    /**
     * The address to open when the user long-presses a link and chooses "Open in new tab", or null
     * when the link must not be opened: only https (http is upgraded), a real host, no whitespace,
     * no new-tab address, and a sane length.
     */
    fun linkTarget(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text.length > MAX_LINK_LENGTH) return null
        if (text.any { it.isWhitespace() }) return null
        val url = when (val decision = checkNavigation(text)) {
            Decision.Allow -> text
            is Decision.Upgrade -> decision.url
            Decision.Block -> return null
        }
        // Host check without java.net.URI, which rejects many valid page addresses (for example "|").
        val authority = url.substring(8).takeWhile { it != '/' && it != '?' && it != '#' }
        val host = authority.substringAfterLast('@').substringBefore(':')
        if (host.isBlank() || isNewTabAddress(url)) return null
        return url
    }

    /** Decides whether the WebView may follow a link / redirect. */
    fun checkNavigation(url: String): Decision {
        val lower = url.lowercase()
        return when {
            lower.startsWith("https://") -> Decision.Allow
            lower.startsWith("http://") -> Decision.Upgrade("https://" + url.substring(7))
            else -> Decision.Block
        }
    }

    /**
     * True for "example.com", "example.com/a?b=c", "localhost:8080", "192.168.0.1".
     * False for anything with spaces, an e-mail style "user@host", or non-address text such as
     * "3.14", which then becomes a search.
     */
    private fun looksLikeAddress(lower: String): Boolean {
        if (lower.any { it.isWhitespace() }) return false
        val authority = lower.substringBefore('/').substringBefore('?').substringBefore('#')
        if ('@' in authority) return false
        val host: String
        val colon = authority.lastIndexOf(':')
        if (colon >= 0) {
            val port = authority.substring(colon + 1)
            if (port.isEmpty() || !port.all { it in '0'..'9' }) return false
            host = authority.substring(0, colon)
        } else {
            host = authority
        }
        return host == "localhost" || IPV4.matches(host) || DOMAIN.matches(host)
    }

    private fun hasHost(url: String): Boolean = try {
        !URI(url).host.isNullOrBlank()
    } catch (e: Exception) {
        false
    }
}
