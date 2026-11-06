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
     * The blank "new tab" address. The new-tab page is drawn on top of it, so it is deliberately
     * not the search engine: changing it would change how tabs are recognised as "new".
     */
    const val HOME_URL = "https://duckduckgo.com/"

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
