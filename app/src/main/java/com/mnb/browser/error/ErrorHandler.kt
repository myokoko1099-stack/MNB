package com.mnb.browser.error

import android.net.http.SslError
import android.webkit.WebViewClient
import com.mnb.browser.state.BrowserError
import com.mnb.browser.state.ErrorKind

/** Maps low-level WebView failures to user-facing errors. Pure mapping, no UI. */
object ErrorHandler {

fun fromWebError(errorCode: Int, description: String?, url: String?): BrowserError {
    val kind = when (errorCode) {
        WebViewClient.ERROR_HOST_LOOKUP,
        WebViewClient.ERROR_CONNECT,
        WebViewClient.ERROR_TIMEOUT,
        WebViewClient.ERROR_IO -> ErrorKind.NETWORK
        WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> ErrorKind.SSL
        else -> ErrorKind.UNKNOWN
    }
    val title = when (kind) {
        ErrorKind.NETWORK -> "Can't reach this page"
        ErrorKind.SSL -> "Secure connection failed"
        else -> "Page failed to load"
    }
    val message = when (kind) {
        ErrorKind.NETWORK -> "Check your internet connection and the address, then try again."
        else -> description?.takeIf { it.isNotBlank() } ?: "Unknown error ($errorCode)."
    }
    return BrowserError(kind, title, message, url)
}

fun fromHttp(statusCode: Int, reason: String?, url: String?) = BrowserError(
    ErrorKind.HTTP,
    "Server error $statusCode",
    reason?.takeIf { it.isNotBlank() } ?: "The website returned an error.",
    url,
)

/** SSL errors are never bypassed; the page is simply not shown. */
fun fromSsl(error: SslError) = BrowserError(
    ErrorKind.SSL,
    "Connection is not secure",
    "The site's security certificate could not be trusted, so the page was not loaded.",
    error.url,
)

fun blocked(url: String, reason: String = "This link type is not allowed.") = BrowserError(
    ErrorKind.BLOCKED_URL,
    "Link blocked",
    reason,
    failingUrl = null,
)

}
