package com.mnb.browser.translation.db

/**
 * Blocking browser data operations. Call from a background thread, not the main thread.
 * WebView instances and WebView session state are intentionally not stored in Room.
 */
class BrowserDataRepository(private val db: MnbDatabase) {
    private val bookmarks = db.bookmarkDao()
    private val history = db.browserHistoryDao()
    private val tabs = db.browserTabDao()

    fun saveBookmark(url: String, title: String, now: Long) {
        require(url.isNotBlank())
        bookmarks.save(
            BookmarkEntity(
                url = url,
                title = title.ifBlank { url },
                createdAtMillis = now,
            ),
        )
    }

    fun isBookmarked(url: String): Boolean = bookmarks.findByUrl(url) != null
    fun getBookmarks(): List<BookmarkEntity> = bookmarks.listAll()
    fun removeBookmark(id: Long): Boolean = bookmarks.delete(id) > 0
    fun removeBookmark(url: String): Boolean = bookmarks.deleteByUrl(url) > 0

    fun recordVisit(url: String, title: String, now: Long): Long {
        require(url.isNotBlank())
        return history.add(
            BrowserHistoryEntity(
                url = url,
                title = title.ifBlank { url },
                visitedAtMillis = now,
            ),
        )
    }

    fun getRecentHistory(limit: Int = 100): List<BrowserHistoryEntity> {
        require(limit > 0)
        return history.recent(limit)
    }

    fun removeHistoryEntry(id: Long): Boolean = history.delete(id) > 0
    fun clearHistory() { history.clear() }

    /**
     * Visits whose title or address contains [query] (plain text, not a pattern), newest first. A blank
     * [query] returns the newest visits, like [getRecentHistory].
     */
    fun searchHistory(query: String, limit: Int = 100): List<BrowserHistoryEntity> {
        require(limit > 0)
        val text = query.trim()
        if (text.isEmpty()) return history.recent(limit)
        return history.search(likePattern(text), limit)
    }

    /**
     * Keeps the history from growing without limit (Phase 2G): removes visits older than [maxAgeMillis],
     * then, if more than [maxEntries] remain, the oldest ones. The newest [maxEntries] visits that are
     * not too old are always kept. One transaction. Returns how many rows were removed.
     */
    fun pruneHistory(
        now: Long,
        maxAgeMillis: Long = HISTORY_MAX_AGE_MILLIS,
        maxEntries: Int = HISTORY_MAX_ENTRIES,
    ): Int {
        require(maxAgeMillis > 0)
        require(maxEntries > 0)
        var removed = 0
        db.runInTransaction(
            Runnable {
                removed += history.deleteOlderThan(now - maxAgeMillis)
                removed += history.trimToNewest(maxEntries)
            },
        )
        return removed
    }

    companion object {
        /** At most this many visits are kept. */
        const val HISTORY_MAX_ENTRIES = 2000

        /** Visits older than this (90 days) are removed. */
        const val HISTORY_MAX_AGE_MILLIS = 90L * 24 * 60 * 60 * 1000

        /** LIKE pattern matching [query] as plain text: percent, underscore and backslash are escaped. */
        fun likePattern(query: String): String {
            val escaped = StringBuilder()
            for (c in query) {
                if (c == '\\' || c == '%' || c == '_') escaped.append('\\')
                escaped.append(c)
            }
            return "%$escaped%"
        }
    }

    fun saveTab(tab: BrowserTabEntity) {
        require(tab.tabId.isNotBlank())
        require(tab.url.isNotBlank())
        tabs.save(tab)
    }

    fun getTabs(): List<BrowserTabEntity> = tabs.listAll()
    fun getTab(tabId: String): BrowserTabEntity? = tabs.get(tabId)
    fun getTabCount(): Int = tabs.count()
    fun closeTab(tabId: String): Boolean = tabs.delete(tabId) > 0

    fun activateTab(tabId: String, now: Long): Boolean {
        var activated = false
        db.runInTransaction {
            tabs.clearActive()
            activated = tabs.activate(tabId, now) > 0
        }
        return activated
    }

    /**
     * Phase 2B: makes the stored tab list exactly [rows] in one transaction. Tabs that are not in
     * [rows] are deleted; the rows carry their own position and active flag.
     */
    fun replaceTabs(rows: List<BrowserTabEntity>) {
        db.runInTransaction(
            Runnable {
                val keep = rows.map { it.tabId }.toSet()
                for (old in tabs.listAll()) {
                    if (old.tabId !in keep) tabs.delete(old.tabId)
                }
                for (row in rows) tabs.save(row)
            },
        )
    }
}
