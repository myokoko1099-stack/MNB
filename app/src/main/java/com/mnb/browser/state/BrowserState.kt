package com.mnb.browser.state

enum class ErrorKind { NETWORK, SSL, HTTP, BLOCKED_URL, UNKNOWN }

data class BrowserError(
val kind: ErrorKind,
val title: String,
val message: String,
/** URL that failed; null when there is nothing to retry (e.g. blocked link). */
val failingUrl: String? = null,
)

data class BrowserState(
val currentUrl: String = "",
val title: String = "",
val progress: Int = 0,
val isLoading: Boolean = false,
val canGoBack: Boolean = false,
val canGoForward: Boolean = false,
val error: BrowserError? = null,
)
