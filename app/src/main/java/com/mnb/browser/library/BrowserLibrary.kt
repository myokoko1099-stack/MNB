package com.mnb.browser.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mnb.browser.translation.db.BookmarkEntity
import com.mnb.browser.translation.db.BrowserDataRepository
import com.mnb.browser.translation.db.BrowserHistoryEntity
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/**
 * Bookmarks and browsing history on top of the existing [BrowserDataRepository] (no new tables,
 * no new repository, no schema change).
 *
 * Threading: the repository uses blocking Room calls, so every repository call runs on
 * [ioExecutor], which must be ONE thread. Because it is one thread, all reads and writes happen one
 * after another and a toggle can never interleave with another write. Results are applied on the
 * main thread through [mainPoster], in the same order the tasks ran. The public functions are
 * called on the main thread and never block. The repository is created on first use, on the IO
 * thread, so the database is not opened on the main thread.
 *
 * Privacy: only the address and title of a finished page are stored. Page contents are never read
 * and nothing about a visited page is logged.
 */
class BrowserLibrary(
    repositoryFactory: () -> BrowserDataRepository,
    private val ioExecutor: Executor,
    private val mainPoster: (Runnable) -> Unit,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val deduplicator: VisitDeduplicator = VisitDeduplicator(),
) {
    private val repository by lazy(repositoryFactory)

    var bookmarks by mutableStateOf<List<LibraryEntry>>(emptyList())
        private set
    var bookmarksState by mutableStateOf(LoadState.Loading)
        private set
    var history by mutableStateOf<List<LibraryEntry>>(emptyList())
        private set
    var historyState by mutableStateOf(LoadState.Loading)
        private set

    /** True when the page last given to [checkBookmarked] is bookmarked. */
    var currentBookmarked by mutableStateOf(false)
        private set

    // Main thread only.
    private var latestCheckUrl: String? = null
    private var bookmarksLoaded = false
    private var historyLoaded = false
    private var historyQuery = ""

    // IO thread only: visits stored since the history was last pruned (the first visit prunes).
    private var visitsSincePrune = PRUNE_EVERY

    // ---- Bookmarks -------------------------------------------------------------------------

    /** Loads the bookmark list. The loading state only shows the first time; later loads refresh quietly. */
    fun refreshBookmarks() {
        if (!bookmarksLoaded) bookmarksState = LoadState.Loading
        runIo(onFailure = { bookmarksState = LoadState.Error }) {
            val rows = loadBookmarkRows()
            onMain { applyBookmarks(rows) }
        }
    }

    /** Asks (in the background) whether [url] is bookmarked and updates [currentBookmarked]. */
    fun checkBookmarked(url: String?) {
        val checked = url?.takeIf { PageRules.isRecordable(it) }
        latestCheckUrl = checked
        if (checked == null) {
            currentBookmarked = false
            return
        }
        runIo {
            val saved = repository.isBookmarked(checked)
            // A newer check may have started meanwhile; only the latest answer counts.
            onMain { if (latestCheckUrl == checked) currentBookmarked = saved }
        }
    }

    /**
     * Adds the page to the bookmarks, or removes it when it is already there. Blank addresses and
     * the new-tab page are ignored; a blank title is stored as the address. The same address is
     * never stored twice.
     */
    fun toggleBookmark(url: String?, title: String?) {
        val page = url?.takeIf { PageRules.isRecordable(it) } ?: return
        val cleanTitle = LibraryText.cleanTitle(title)
        runIo(onFailure = { bookmarksState = LoadState.Error }) {
            if (repository.isBookmarked(page)) {
                repository.removeBookmark(page)
            } else {
                repository.saveBookmark(page, cleanTitle, clock())
            }
            val rows = loadBookmarkRows()
            onMain { applyBookmarks(rows) }
        }
    }

    /** Deletes one bookmark. The row disappears at once; the stored list is then reloaded. */
    fun removeBookmark(id: Long) {
        bookmarks = bookmarks.filterNot { it.id == id }
        syncCurrentBookmarked()
        runIo(onFailure = { bookmarksState = LoadState.Error }) {
            repository.removeBookmark(id)
            val rows = loadBookmarkRows()
            onMain { applyBookmarks(rows) }
        }
    }

    // ---- History ---------------------------------------------------------------------------

    /**
     * Records a finished page visit. Blank, invalid and new-tab addresses are ignored, and the same
     * page in the same tab is only recorded once per [VisitDeduplicator.DEFAULT_WINDOW_MILLIS].
     * Call on the main thread.
     */
    fun recordVisit(tabId: String, url: String?, title: String?) {
        val page = url?.takeIf { PageRules.isRecordable(it) } ?: return
        val now = clock()
        if (!deduplicator.accept(tabId, page, now)) return
        val cleanTitle = LibraryText.cleanTitle(title)
        runIo {
            repository.recordVisit(page, cleanTitle, now)
            pruneIfDue(now)
        }
    }

    /** A tab was closed: forget its last visit. */
    fun forgetTab(tabId: String) {
        deduplicator.forget(tabId)
    }

    /**
     * Loads the newest [HISTORY_LIMIT] history entries, newest first. A non-blank [query] keeps only the
     * visits whose title or address contains it. Only the answer to the latest query is shown.
     */
    fun refreshHistory(query: String = "") {
        val asked = query.trim()
        historyQuery = asked
        if (!historyLoaded) historyState = LoadState.Loading
        runIo(onFailure = { historyState = LoadState.Error }) {
            val rows = loadHistoryRows(asked)
            onMain { if (historyQuery == asked) applyHistory(rows) }
        }
    }

    /** Deletes one history entry. The row disappears at once; the stored list is then reloaded. */
    fun removeHistoryEntry(id: Long) {
        history = history.filterNot { it.id == id }
        val asked = historyQuery
        runIo(onFailure = { historyState = LoadState.Error }) {
            repository.removeHistoryEntry(id)
            val rows = loadHistoryRows(asked)
            onMain { if (historyQuery == asked) applyHistory(rows) }
        }
    }

    /** Deletes the whole history. Bookmarks and tabs are not touched. */
    fun clearHistory() {
        history = emptyList()
        historyState = LoadState.Ready
        historyLoaded = true
        runIo(onFailure = { historyState = LoadState.Error }) {
            repository.clearHistory()
            // Nothing is left, whatever the search was.
            onMain { applyHistory(emptyList()) }
        }
    }

    // ---- Internals -------------------------------------------------------------------------

    private fun loadBookmarkRows(): List<LibraryEntry> = repository.getBookmarks().map { it.toEntry() }

    private fun loadHistoryRows(query: String): List<LibraryEntry> =
        (if (query.isEmpty()) repository.getRecentHistory(HISTORY_LIMIT) else repository.searchHistory(query, HISTORY_LIMIT))
            .map { it.toEntry() }

    /** IO thread. Runs on the first stored visit, then after every [PRUNE_EVERY] visits (see Phase 2G). */
    private fun pruneIfDue(now: Long) {
        visitsSincePrune++
        if (visitsSincePrune < PRUNE_EVERY) return
        visitsSincePrune = 0
        repository.pruneHistory(now)
    }

    private fun applyBookmarks(rows: List<LibraryEntry>) {
        bookmarks = rows
        bookmarksLoaded = true
        bookmarksState = LoadState.Ready
        syncCurrentBookmarked()
    }

    private fun applyHistory(rows: List<LibraryEntry>) {
        history = rows
        historyLoaded = true
        historyState = LoadState.Ready
    }

    /** Keeps [currentBookmarked] in step with the bookmark list once that list has been loaded. */
    private fun syncCurrentBookmarked() {
        if (!bookmarksLoaded) return
        val page = latestCheckUrl
        currentBookmarked = page != null && bookmarks.any { it.url == page }
    }

    /**
     * Runs [task] on the IO thread. A failure (for example a database error) never crashes the
     * browser: [onFailure] runs on the main thread instead. The exception is not logged, because its
     * message could contain an address.
     */
    private fun runIo(onFailure: () -> Unit = {}, task: () -> Unit) {
        try {
            ioExecutor.execute {
                try {
                    task()
                } catch (e: Exception) {
                    onMain(onFailure)
                }
            }
        } catch (e: RejectedExecutionException) {
            // The app is shutting down; nothing more is stored.
        }
    }

    private fun onMain(block: () -> Unit) {
        mainPoster(Runnable { block() })
    }

    private fun BookmarkEntity.toEntry() = LibraryEntry(bookmarkId, url, title, createdAtMillis)

    private fun BrowserHistoryEntity.toEntry() = LibraryEntry(historyId, url, title, visitedAtMillis)

    companion object {
        /** How many history entries the History screen loads. */
        const val HISTORY_LIMIT = 200

        /** The history is pruned on the first stored visit and then after this many stored visits. */
        const val PRUNE_EVERY = 25
    }
}
