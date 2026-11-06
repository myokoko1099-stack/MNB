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
