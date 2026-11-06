package com.mnb.browser.tabs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mnb.browser.navigation.UrlPolicy
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The list of open tabs and which one is active. Pure logic: it never touches a WebView.
 *
 * Threading: every public function except [restore]'s background part runs on the main thread.
 * Storage is blocking, so every write goes to [ioExecutor] (one thread, writes are coalesced and
 * always carry the latest state). The result of [restore] is posted back with [mainPoster].
 */
class TabManager(
    private val persistence: TabPersistence,
    private val ioExecutor: Executor,
    private val mainPoster: (Runnable) -> Unit,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val homeUrl: String = UrlPolicy.HOME_URL,
    val maxTabs: Int = MAX_TABS,
) {
    var tabs by mutableStateOf<List<TabInfo>>(emptyList())
        private set
    var activeTabId by mutableStateOf<String?>(null)
        private set
    var isRestored by mutableStateOf(false)
        private set

    /** Called (main thread) just before a tab disappears, so its WebView and session file can go. */
    var onTabClosed: ((String) -> Unit)? = null

    /** Called (main thread) once the stored tabs are loaded, with the ids that are now open. */
    var onRestored: ((List<String>) -> Unit)? = null

    val activeTab: TabInfo? get() = tabs.firstOrNull { it.id == activeTabId }
    val canOpenNewTab: Boolean get() = isRestored && tabs.size < maxTabs

    private var restoreRequested = false

    private class Snapshot(val tabs: List<TabInfo>, val activeTabId: String?)

    @Volatile private var latest: Snapshot? = null
    private val saveScheduled = AtomicBoolean(false)

    /** Loads the stored tabs on the background thread, then applies them on the main thread. */
    fun restore() {
        if (restoreRequested) return
        restoreRequested = true
        try {
            ioExecutor.execute {
                val stored = try {
                    persistence.load()
                } catch (e: Exception) {
                    StoredTabs(emptyList(), null)
                }
                mainPoster(Runnable { applyRestored(stored) })
            }
        } catch (e: RejectedExecutionException) {
            applyRestored(StoredTabs(emptyList(), null))
        }
    }

    private fun applyRestored(stored: StoredTabs) {
        if (isRestored) return
        val cleaned = stored.tabs
            .filter { it.id.isNotBlank() && it.url.isNotBlank() }
            .distinctBy { it.id }
            .take(maxTabs)
            .map { it.copy(url = safeUrl(it.url)) }
        if (cleaned.isEmpty()) {
            val home = createHomeTab()
            tabs = listOf(home)
            activeTabId = home.id
        } else {
            tabs = cleaned
            activeTabId = stored.activeTabId?.takeIf { id -> cleaned.any { it.id == id } }
                ?: cleaned.maxBy { it.lastAccessedAtMillis }.id
        }
        isRestored = true
        persist()
        onRestored?.invoke(tabs.map { it.id })
    }

    /** Opens a tab and makes it active. Returns null when it is not allowed (not restored yet, or at the limit). */
    fun newTab(url: String = homeUrl): TabInfo? {
        if (!canOpenNewTab) return null
        val tab = TabInfo(newId(), safeUrl(url), "", clock())
        tabs = tabs + tab
        activeTabId = tab.id
        persist()
        return tab
    }

    fun switchTo(tabId: String): Boolean {
        if (tabs.none { it.id == tabId }) return false
        if (tabId == activeTabId) return true
        val now = clock()
        tabs = tabs.map { if (it.id == tabId) it.copy(lastAccessedAtMillis = now) else it }
        activeTabId = tabId
        persist()
        return true
    }

    /**
     * Closes a tab. If it was the active one, the tab that takes its place becomes active. Closing the
     * last tab opens a fresh Home tab.
     */
    fun close(tabId: String): Boolean {
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index < 0) return false
        val wasActive = tabId == activeTabId
        val remaining = tabs.filterNot { it.id == tabId }
        onTabClosed?.invoke(tabId)
        if (remaining.isEmpty()) {
            val home = createHomeTab()
            tabs = listOf(home)
            activeTabId = home.id
        } else {
            tabs = remaining
            if (wasActive) activeTabId = remaining[minOf(index, remaining.lastIndex)].id
        }
        persist()
        return true
    }

    /** A page in tab [tabId] changed its address or title. Unsafe or blank addresses are ignored. */
    fun updatePage(tabId: String, url: String?, title: String?) {
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index < 0) return
        val old = tabs[index]
        val newUrl = url
            ?.takeIf { it.isNotBlank() && UrlPolicy.checkNavigation(it) != UrlPolicy.Decision.Block }
            ?: old.url
        val newTitle = title ?: old.title
        if (newUrl == old.url && newTitle == old.title) return
        tabs = tabs.toMutableList().also { it[index] = old.copy(url = newUrl, title = newTitle) }
        persist()
    }

    private fun createHomeTab() = TabInfo(newId(), homeUrl, "", clock())

    private fun safeUrl(url: String): String = when (val decision = UrlPolicy.checkNavigation(url)) {
        UrlPolicy.Decision.Allow -> url
        is UrlPolicy.Decision.Upgrade -> decision.url
        UrlPolicy.Decision.Block -> homeUrl
    }

    private fun persist() {
        latest = Snapshot(tabs, activeTabId)
        if (!saveScheduled.compareAndSet(false, true)) return
        try {
            ioExecutor.execute {
                saveScheduled.set(false)
                val snapshot = latest
                if (snapshot != null) {
                    try {
                        persistence.save(snapshot.tabs, snapshot.activeTabId)
                    } catch (e: Exception) {
                        // Best effort: losing a save must never crash the browser.
                    }
                }
            }
        } catch (e: RejectedExecutionException) {
            saveScheduled.set(false)
        }
    }

    companion object {
        /** Every tab keeps a live WebView, so the number of tabs is capped to protect memory. */
        const val MAX_TABS = 20
    }
}
