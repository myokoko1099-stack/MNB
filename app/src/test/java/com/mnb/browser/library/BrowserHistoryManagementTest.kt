package com.mnb.browser.library

import com.mnb.browser.translation.db.BrowserDataRepository
import com.mnb.browser.translation.db.DbTestBase
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Phase 2G: history pruning and history search, on the real Room database (Robolectric). */
class BrowserHistoryManagementTest : DbTestBase() {
    private lateinit var repo: BrowserDataRepository
    private var now = 1_000L

    @Before fun setUpRepository() {
        repo = BrowserDataRepository(db)
    }

    private fun newLibrary() = BrowserLibrary(
        repositoryFactory = { repo },
        ioExecutor = Executor { it.run() },
        mainPoster = { it.run() },
        clock = { now },
    )

    private fun visit(url: String, title: String, at: Long) = repo.recordVisit(url, title, at)

    // ---- pruning (repository) ----

    @Test fun visitsOlderThanTheMaximumAgeAreRemoved() {
        visit("https://a.test/", "A", 0)
        visit("https://b.test/", "B", 500)
        visit("https://c.test/", "C", 1_000)
        val removed = repo.pruneHistory(now = 1_500, maxAgeMillis = 1_000, maxEntries = 100)
        assertEquals(1, removed)
        // The visit exactly at the cut-off (500) is still recent enough.
        assertEquals(listOf("https://c.test/", "https://b.test/"), repo.getRecentHistory().map { it.url })
    }

    @Test fun onlyTheNewestEntriesAreKeptWhenThereAreTooMany() {
        for (i in 1..10) visit("https://e.test/$i", "P$i", i.toLong())
        val removed = repo.pruneHistory(now = 10, maxAgeMillis = 1_000_000, maxEntries = 3)
        assertEquals(7, removed)
        assertEquals(listOf(10L, 9L, 8L), repo.getRecentHistory().map { it.visitedAtMillis })
    }

    @Test fun visitsWithTheSameTimeKeepTheLaterOnes() {
        val ids = (1..5).map { visit("https://s.test/$it", "S$it", 5) }
        repo.pruneHistory(now = 5, maxAgeMillis = 1_000, maxEntries = 2)
        assertEquals(setOf(ids[3], ids[4]), repo.getRecentHistory().map { it.historyId }.toSet())
    }

    @Test fun pruningWithNothingToRemoveChangesNothing() {
        visit("https://a.test/", "A", 900)
        visit("https://b.test/", "B", 950)
        assertEquals(0, repo.pruneHistory(now = 1_000, maxAgeMillis = 1_000, maxEntries = 10))
        assertEquals(2, repo.getRecentHistory().size)
    }

    @Test fun pruningAnEmptyHistoryIsHarmless() {
        assertEquals(0, repo.pruneHistory(now = 1_000))
        assertTrue(repo.getRecentHistory().isEmpty())
    }

    @Test fun pruningNeverTouchesBookmarks() {
        repo.saveBookmark("https://old.test/", "Old", 0)
        visit("https://old.test/", "Old", 0)
        repo.pruneHistory(now = BrowserDataRepository.HISTORY_MAX_AGE_MILLIS * 2)
        assertTrue(repo.getRecentHistory().isEmpty())
        assertEquals(1, repo.getBookmarks().size)
    }

    @Test fun badPruningLimitsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { repo.pruneHistory(now = 1, maxEntries = 0) }
        assertThrows(IllegalArgumentException::class.java) { repo.pruneHistory(now = 1, maxAgeMillis = 0) }
    }

    @Test fun defaultsKeepNinetyDaysAndTwoThousandVisits() {
        assertEquals(2_000, BrowserDataRepository.HISTORY_MAX_ENTRIES)
        assertEquals(90L * 24 * 60 * 60 * 1000, BrowserDataRepository.HISTORY_MAX_AGE_MILLIS)
    }

    // ---- pruning (library) ----

    @Test fun theFirstStoredVisitPrunesExpiredHistory() {
        visit("https://old.test/", "Old", 0)
        now = BrowserDataRepository.HISTORY_MAX_AGE_MILLIS + 1_000
        newLibrary().recordVisit("t1", "https://new.test/", "New")
        assertEquals(listOf("https://new.test/"), repo.getRecentHistory().map { it.url })
    }

    @Test fun laterPruningRunsOnlyEveryPruneEveryVisits() {
        now = BrowserDataRepository.HISTORY_MAX_AGE_MILLIS + 1_000
        val library = newLibrary()
        library.recordVisit("t1", "https://first.test/", "First") // prunes, counter restarts
        visit("https://expired.test/", "Expired", 0)
        for (i in 1 until BrowserLibrary.PRUNE_EVERY) library.recordVisit("t1", "https://p.test/$i", "P$i")
        assertTrue(repo.getRecentHistory(500).any { it.url == "https://expired.test/" })
        library.recordVisit("t1", "https://last.test/", "Last") // PRUNE_EVERY-th visit since the prune
        assertTrue(repo.getRecentHistory(500).none { it.url == "https://expired.test/" })
    }

    // ---- search (repository) ----

    @Test fun searchFindsTitleAndAddressNewestFirst() {
        visit("https://kotlin.test/a", "Docs", 1)
        visit("https://other.test/", "Learning Kotlin", 2)
        visit("https://nothing.test/", "Cooking", 3)
        assertEquals(
            listOf("https://other.test/", "https://kotlin.test/a"),
            repo.searchHistory("kotlin").map { it.url },
        )
    }

    @Test fun searchIgnoresCaseOfAsciiLetters() {
        visit("https://a.test/", "Hello World", 1)
        assertEquals(1, repo.searchHistory("hELLO").size)
    }

    @Test fun percentAndUnderscoreAreSearchedAsPlainText() {
        visit("https://a.test/1", "100% done", 1)
        visit("https://a.test/2", "100 percent", 2)
        visit("https://a.test/3", "a_b", 3)
        visit("https://a.test/4", "axb", 4)
        assertEquals(listOf("https://a.test/1"), repo.searchHistory("%").map { it.url })
        assertEquals(listOf("https://a.test/3"), repo.searchHistory("a_b").map { it.url })
    }

    @Test fun aBackslashIsSearchedAsPlainText() {
        visit("https://a.test/1", "C:\\temp", 1)
        visit("https://a.test/2", "plain", 2)
        assertEquals(listOf("https://a.test/1"), repo.searchHistory("\\").map { it.url })
    }

    @Test fun burmeseTextCanBeSearched() {
        visit("https://a.test/1", "ဝတ္ထု ဖတ်ရန်", 1)
        visit("https://a.test/2", "other", 2)
        assertEquals(listOf("https://a.test/1"), repo.searchHistory("ဝတ္ထု").map { it.url })
    }

    @Test fun blankSearchReturnsTheNewestVisitsAndLimitIsRespected() {
        for (i in 1..5) visit("https://e.test/$i", "P$i", i.toLong())
        assertEquals(5, repo.searchHistory("   ").size)
        assertEquals(2, repo.searchHistory("e.test", limit = 2).size)
        assertTrue(repo.searchHistory("no-such-page").isEmpty())
    }

    @Test fun likePatternEscapesTheSpecialCharacters() {
        assertEquals("%a\\%b\\_c\\\\%", BrowserDataRepository.likePattern("a%b_c\\"))
        assertEquals("%plain%", BrowserDataRepository.likePattern("plain"))
    }

    // ---- search (library) ----

    @Test fun libraryFiltersHistoryByTheQueryAndRestoresItWhenCleared() {
        visit("https://a.test/", "Apple pie", 1)
        visit("https://b.test/", "Berry tart", 2)
        val library = newLibrary()
        library.refreshHistory("apple")
        assertEquals(listOf("https://a.test/"), library.history.map { it.url })
        library.refreshHistory("")
        assertEquals(2, library.history.size)
        library.refreshHistory("zzz")
        assertTrue(library.history.isEmpty())
        assertEquals(LoadState.Ready, library.historyState)
    }

    @Test fun anOlderSearchAnswerNeverReplacesTheLatestOne() {
        visit("https://a.test/", "Apple pie", 1)
        visit("https://b.test/", "Berry tart", 2)
        val queued = ArrayDeque<Runnable>()
        val library = BrowserLibrary(
            repositoryFactory = { repo },
            ioExecutor = Executor { queued.addLast(it) },
            mainPoster = { it.run() },
            clock = { now },
        )
        library.refreshHistory("apple")
        library.refreshHistory("berry")
        // The answer for "apple" arrives after the answer for "berry".
        while (queued.isNotEmpty()) queued.removeLast().run()
        assertEquals(listOf("https://b.test/"), library.history.map { it.url })
    }

    @Test fun removingAnEntryKeepsTheCurrentSearch() {
        visit("https://a.test/1", "Apple one", 1)
        visit("https://a.test/2", "Apple two", 2)
        visit("https://b.test/", "Berry", 3)
        val library = newLibrary()
        library.refreshHistory("apple")
        library.removeHistoryEntry(library.history.first().id)
        assertEquals(listOf("https://a.test/1"), library.history.map { it.url })
    }

    @Test fun clearingHistoryWhileSearchingLeavesNothing() {
        visit("https://a.test/", "Apple", 1)
        val library = newLibrary()
        library.refreshHistory("apple")
        library.clearHistory()
        assertTrue(library.history.isEmpty())
        assertTrue(repo.getRecentHistory().isEmpty())
    }
}
