package com.mnb.browser.library

import com.mnb.browser.navigation.UrlPolicy
import com.mnb.browser.translation.db.BrowserDataRepository
import com.mnb.browser.translation.db.DbTestBase
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Runs BrowserLibrary on the real Room database (Robolectric) with a direct, synchronous executor. */
class BrowserLibraryTest : DbTestBase() {
    private lateinit var repo: BrowserDataRepository
    private lateinit var library: BrowserLibrary
    private var now = 1_000L

    private val a = "https://a.example.com/page"
    private val b = "https://b.example.com/"
    private val c = "https://c.example.com/x?y=1"

    @Before fun setUpLibrary() {
        repo = BrowserDataRepository(db)
        library = newLibrary()
    }

    private fun newLibrary(dedup: VisitDeduplicator = VisitDeduplicator()) = BrowserLibrary(
        repositoryFactory = { repo },
        ioExecutor = Executor { it.run() },
        mainPoster = { it.run() },
        clock = { now },
        deduplicator = dedup,
    )

    // ---- Bookmarks ----

    @Test fun toggleAddsThenRemovesTheCurrentPage() {
        library.checkBookmarked(a)
        assertFalse(library.currentBookmarked)

        library.toggleBookmark(a, "Page A")
        assertTrue(library.currentBookmarked)
        assertEquals(listOf(a), library.bookmarks.map { it.url })
        assertEquals("Page A", library.bookmarks.single().title)
        assertEquals(LoadState.Ready, library.bookmarksState)
        assertTrue(repo.isBookmarked(a))

        library.toggleBookmark(a, "Page A")
        assertFalse(library.currentBookmarked)
        assertTrue(library.bookmarks.isEmpty())
        assertFalse(repo.isBookmarked(a))
    }

    @Test fun checkBookmarkedReflectsStoredState() {
        repo.saveBookmark(a, "A", now)
        library.checkBookmarked(a)
        assertTrue(library.currentBookmarked)
        library.checkBookmarked(b)
        assertFalse(library.currentBookmarked)
    }

    @Test fun savingTheSameUrlTwiceKeepsOneBookmark() {
        repo.saveBookmark(a, "First", now)
        repo.saveBookmark(a, "Second", now + 1)
        library.refreshBookmarks()
        assertEquals(1, library.bookmarks.size)
        assertEquals(1, repo.getBookmarks().size)
    }

    @Test fun toggleOnAnAlreadySavedUrlRemovesInsteadOfDuplicating() {
        repo.saveBookmark(a, "A", now)
        library.toggleBookmark(a, "A again")
        assertTrue(repo.getBookmarks().isEmpty())
    }

    @Test fun blankTitleIsStoredAsTheAddress() {
        library.toggleBookmark(a, "   ")
        assertEquals(a, library.bookmarks.single().title)
        assertEquals(a, library.bookmarks.single().displayTitle)
        library.toggleBookmark(b, null)
        assertEquals(b, repo.getBookmarks().first { it.url == b }.title)
    }

    @Test fun invalidAddressesAreNeverBookmarked() {
        library.toggleBookmark(null, "t")
        library.toggleBookmark("", "t")
        library.toggleBookmark("   ", "t")
        library.toggleBookmark("about:blank", "t")
        library.toggleBookmark("http://insecure.example.com/", "t")
        library.toggleBookmark(UrlPolicy.NEW_TAB_URL, "New tab")
        assertTrue(repo.getBookmarks().isEmpty())
        library.checkBookmarked(UrlPolicy.NEW_TAB_URL)
        assertFalse(library.currentBookmarked)
    }

    @Test fun bookmarksListNewestFirst() {
        now = 100; library.toggleBookmark(a, "A")
        now = 300; library.toggleBookmark(b, "B")
        now = 200; library.toggleBookmark(c, "C")
        assertEquals(listOf(b, c, a), library.bookmarks.map { it.url })
    }

    @Test fun removeBookmarkDeletesOnlyThatRow() {
        library.toggleBookmark(a, "A")
        library.toggleBookmark(b, "B")
        library.checkBookmarked(a)
        assertTrue(library.currentBookmarked)

        val idOfA = library.bookmarks.first { it.url == a }.id
        library.removeBookmark(idOfA)

        assertEquals(listOf(b), library.bookmarks.map { it.url })
        assertEquals(listOf(b), repo.getBookmarks().map { it.url })
        assertFalse(library.currentBookmarked)
    }

    @Test fun emptyBookmarkListIsReadyNotLoading() {
        assertEquals(LoadState.Loading, library.bookmarksState)
        library.refreshBookmarks()
        assertEquals(LoadState.Ready, library.bookmarksState)
        assertTrue(library.bookmarks.isEmpty())
    }

    // ---- History ----

    @Test fun recordedVisitsListNewestFirst() {
        now = 100; library.recordVisit("t1", a, "A")
        now = 200; library.recordVisit("t1", b, "B")
        now = 300; library.recordVisit("t1", c, "C")
        library.refreshHistory()
        assertEquals(listOf(c, b, a), library.history.map { it.url })
        assertEquals(listOf("C", "B", "A"), library.history.map { it.title })
        assertEquals(LoadState.Ready, library.historyState)
    }

    @Test fun sameVisitIsStoredOnceInsideTheWindow() {
        now = 0; library.recordVisit("t1", a, "A")
        now = 5; library.recordVisit("t1", a, "A")
        now = 10; library.recordVisit("t1", a, "A (title callback)")
        assertEquals(1, repo.getRecentHistory().size)
    }

    @Test fun samePageIsStoredAgainAfterTheWindow() {
        now = 0; library.recordVisit("t1", a, "A")
        now = VisitDeduplicator.DEFAULT_WINDOW_MILLIS; library.recordVisit("t1", a, "A")
        assertEquals(2, repo.getRecentHistory().size)
    }

    @Test fun samePageInTwoTabsIsTwoVisits() {
        now = 0
        library.recordVisit("t1", a, "A")
        library.recordVisit("t2", a, "A")
        assertEquals(2, repo.getRecentHistory().size)
    }

    @Test fun closingATabForgetsItsLastVisit() {
        now = 0; library.recordVisit("t1", a, "A")
        library.forgetTab("t1")
        now = 1; library.recordVisit("t1", a, "A")
        assertEquals(2, repo.getRecentHistory().size)
    }

    @Test fun invalidAddressesAreNeverRecorded() {
        listOf(
            null, "", "   ", "about:blank", "javascript:alert(1)", "file:///a", "http://insecure.example.com/",
            "https://", UrlPolicy.NEW_TAB_URL, UrlPolicy.NEW_TAB_URL.uppercase(),
        ).forEachIndexed { i, url ->
            now = i * 100_000L
            library.recordVisit("t1", url, "t")
        }
        assertTrue(repo.getRecentHistory().isEmpty())
    }

    @Test fun blankTitleOfAVisitIsStoredAsTheAddress() {
        library.recordVisit("t1", a, "  ")
        library.refreshHistory()
        assertEquals(a, library.history.single().title)
    }

    @Test fun removeHistoryEntryDeletesOnlyThatRow() {
        now = 1; library.recordVisit("t1", a, "A")
        now = 2; library.recordVisit("t1", b, "B")
        library.refreshHistory()
        val idOfB = library.history.first { it.url == b }.id

        library.removeHistoryEntry(idOfB)

        assertEquals(listOf(a), library.history.map { it.url })
        assertEquals(listOf(a), repo.getRecentHistory().map { it.url })
    }

    @Test fun clearHistoryRemovesEverythingButKeepsBookmarks() {
        library.toggleBookmark(a, "A")
        now = 1; library.recordVisit("t1", a, "A")
        now = 2; library.recordVisit("t1", b, "B")

        library.clearHistory()

        assertTrue(library.history.isEmpty())
        assertEquals(LoadState.Ready, library.historyState)
        assertTrue(repo.getRecentHistory().isEmpty())
        assertEquals(1, repo.getBookmarks().size)
    }

    @Test fun clearHistoryOnEmptyHistoryIsHarmless() {
        library.clearHistory()
        library.refreshHistory()
        assertTrue(library.history.isEmpty())
        assertEquals(LoadState.Ready, library.historyState)
    }

    @Test fun historyScreenLoadsOnlyTheNewestEntries() {
        for (i in 1..(BrowserLibrary.HISTORY_LIMIT + 5)) {
            now = i.toLong()
            library.recordVisit("t$i", "https://e.example.com/$i", "P$i")
        }
        library.refreshHistory()
        assertEquals(BrowserLibrary.HISTORY_LIMIT, library.history.size)
        assertEquals("https://e.example.com/${BrowserLibrary.HISTORY_LIMIT + 5}", library.history.first().url)
    }

    // ---- Threading and failure handling ----

    @Test fun repositoryIsNeverTouchedOnTheCallingThread() {
        val queued = ArrayDeque<Runnable>()
        var factoryCalls = 0
        val lib = BrowserLibrary(
            repositoryFactory = { factoryCalls++; repo },
            ioExecutor = Executor { queued.addLast(it) },
            mainPoster = { it.run() },
            clock = { now },
        )

        lib.recordVisit("t1", a, "A")
        lib.toggleBookmark(b, "B")
        lib.refreshHistory()
        lib.refreshBookmarks()

        assertEquals(0, factoryCalls)
        assertTrue(repo.getRecentHistory().isEmpty())
        assertTrue(repo.getBookmarks().isEmpty())
        assertEquals(4, queued.size)

        while (queued.isNotEmpty()) queued.removeFirst().run()

        assertEquals(1, factoryCalls)
        assertEquals(1, repo.getRecentHistory().size)
        assertEquals(1, repo.getBookmarks().size)
    }

    @Test fun resultsAreAppliedThroughTheMainPoster() {
        val posted = ArrayDeque<Runnable>()
        val lib = BrowserLibrary(
            repositoryFactory = { repo },
            ioExecutor = Executor { it.run() },
            mainPoster = { posted.addLast(it) },
            clock = { now },
        )
        lib.toggleBookmark(a, "A")
        // The database work is done, but the UI state only changes once the main thread runs the post.
        assertEquals(1, repo.getBookmarks().size)
        assertTrue(lib.bookmarks.isEmpty())
        while (posted.isNotEmpty()) posted.removeFirst().run()
        assertEquals(1, lib.bookmarks.size)
    }

    @Test fun staleBookmarkCheckDoesNotOverwriteANewerOne() {
        repo.saveBookmark(a, "A", now)
        val queued = ArrayDeque<Runnable>()
        val lib = BrowserLibrary(
            repositoryFactory = { repo },
            ioExecutor = Executor { queued.addLast(it) },
            mainPoster = { it.run() },
            clock = { now },
        )
        lib.checkBookmarked(a) // would answer "true"
        lib.checkBookmarked(b) // the page changed before the answer came back
        // Worst case: the answer for the old page arrives last.
        while (queued.isNotEmpty()) queued.removeLast().run()
        assertFalse(lib.currentBookmarked)
    }

    @Test fun databaseFailureShowsAnErrorStateInsteadOfCrashing() {
        val lib = BrowserLibrary(
            repositoryFactory = { throw IllegalStateException("database unavailable") },
            ioExecutor = Executor { it.run() },
            mainPoster = { it.run() },
            clock = { now },
        )
        lib.refreshBookmarks()
        lib.refreshHistory()
        lib.recordVisit("t1", a, "A") // must not throw
        lib.toggleBookmark(a, "A") // must not throw
        assertEquals(LoadState.Error, lib.bookmarksState)
        assertEquals(LoadState.Error, lib.historyState)
    }

    @Test fun retryAfterAFailureRecovers() {
        var healthy = false
        val lib = BrowserLibrary(
            repositoryFactory = { if (healthy) repo else throw IllegalStateException("not yet") },
            ioExecutor = Executor { it.run() },
            mainPoster = { it.run() },
            clock = { now },
        )
        lib.refreshHistory()
        assertEquals(LoadState.Error, lib.historyState)
        healthy = true
        lib.refreshHistory()
        assertEquals(LoadState.Ready, lib.historyState)
    }

    @Test fun shuttingDownExecutorDoesNotCrash() {
        val lib = BrowserLibrary(
            repositoryFactory = { repo },
            ioExecutor = Executor { throw RejectedExecutionException("shut down") },
            mainPoster = { it.run() },
            clock = { now },
        )
        lib.recordVisit("t1", a, "A")
        lib.toggleBookmark(a, "A")
        lib.refreshBookmarks()
        lib.clearHistory()
    }
}
