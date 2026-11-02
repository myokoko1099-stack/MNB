package com.mnb.browser.navigation

import java.net.URI
import java.net.URLEncoder

/**

All URL safety / normalisation rules live here (pure Kotlin, unit-testable).

Only https is ever loaded; http is upgraded to https. Other schemes are blocked.
*/
object UrlPolicy {
const val HOME_URL = "https://duckduckgo.com/"
private const val SEARCH_URL = "https://duckduckgo.com/?q="

sealed interface Input {
data class Ok(val url: String) : Input
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

 val hostLike = text.none { it.isWhitespace() } &&
     (text.contains('.') || lower.startsWith("localhost"))
 if (hostLike) {
     val url = "https://$text"
     return if (hasHost(url)) Input.Ok(url) else Input.Blocked("That address is not valid.")
 }
 return Input.Ok(SEARCH_URL + URLEncoder.encode(text, "UTF-8"))

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

private fun hasHost(url: String): Boolean = try {
!URI(url).host.isNullOrBlank()
} catch (e: Exception) {
false
}
}
