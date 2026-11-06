package com.mnb.browser.tabs

/**
 * One browser tab as the app remembers it. The WebView itself and its back/forward history are
 * kept by the WebViewController (and in a per-tab session file), not here.
 */
data class TabInfo(
    val id: String,
    val url: String,
    val title: String,
    val lastAccessedAtMillis: Long,
)

/** What was stored the last time the app ran. */
data class StoredTabs(
    val tabs: List<TabInfo>,
    val activeTabId: String?,
)
