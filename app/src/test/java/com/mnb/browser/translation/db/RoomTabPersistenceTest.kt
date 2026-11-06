package com.mnb.browser.translation.db

import com.mnb.browser.tabs.RoomTabPersistence
import com.mnb.browser.tabs.StoredTabs
import com.mnb.browser.tabs.TabInfo
import com.mnb.browser.tabs.TabManager
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 2B tab storage against a real in-memory Room database (the Phase 2A browser_tabs table). */
class RoomTabPersistenceTest : DbTestBase() {

    private fun persistence() = RoomTabPersistence(BrowserDataRepository(db))

    private fun manager(counterStart: Int = 0): TabManager {
        var counter = counterStart
        var time = 1000L
        return TabManager(
            persistence = persistence(),
            ioExecutor = Executor { task -> task.run() },
            mainPoster = { task -> task.run() },
            clock = { time++ },
            newId = { "tab-${++counter}" },
            homeUrl = "https://home.test/",
        )
    }

    private fun info(id: String, url: String, at: Long) = TabInfo(id, url, "T-$id", at)

    @Test fun emptyStorageLoadsAsNoTabsAndNoActiveTab() {
        val stored = persistence().load()
        assertTrue(stored.tabs.isEmpty())
        assertNull(stored.activeTabId)
    }

    @Test fun savedTabsComeBackInOrderWithTheirActiveFlag() {
        val p = persistence()
        p.save(listOf(info("b", "https://b.test/", 5), info("a", "https://a.test/", 9), info("c", "https://c.test/", 1)), "a")
        val stored = p.load()
        assertEquals(listOf("b", "a", "c"), stored.tabs.map { it.id })
        assertEquals("a", stored.activeTabId)
        assertEquals("https://a.test/", stored.tabs[1].url)
        assertEquals("T-a", stored.tabs[1].title)
        assertEquals(9L, stored.tabs[1].lastAccessedAtMillis)
    }

    @Test fun savingAgainReplacesTheWholeList() {
        val p = persistence()
        p.save(listOf(info("a", "https://a.test/", 1), info("b", "https://b.test/", 2)), "a")
        p.save(listOf(info("b", "https://b2.test/", 3), info("c", "https://c.test/", 4)), "c")
        val stored = p.load()
        assertEquals(listOf("b", "c"), stored.tabs.map { it.id })
        assertEquals("https://b2.test/", stored.tabs[0].url)
        assertEquals("c", stored.activeTabId)
        assertEquals(2, BrowserDataRepository(db).getTabCount())
    }

    @Test fun exactlyOneStoredTabIsActive() {
        val p = persistence()
        p.save(listOf(info("a", "https://a.test/", 1), info("b", "https://b.test/", 2)), "a")
        p.save(listOf(info("a", "https://a.test/", 1), info("b", "https://b.test/", 2)), "b")
        assertEquals(1, BrowserDataRepository(db).getTabs().count { it.isActive })
        assertEquals("b", p.load().activeTabId)
    }

    @Test fun tabsSurviveAnAppRestart() {
        val first = manager()
        first.restore()
        val second = first.newTab()!!
        first.updatePage(second.id, "https://news.test/story", "A story")
        val third = first.newTab()!!
        first.switchTo(second.id)

        // "Restart": a brand new manager reads the same database.
        val after = manager(counterStart = 100)
        after.restore()
        assertEquals(first.tabs.map { it.id }, after.tabs.map { it.id })
        assertEquals(second.id, after.activeTabId)
        assertEquals("https://news.test/story", after.tabs.first { it.id == second.id }.url)
        assertEquals("A story", after.tabs.first { it.id == second.id }.title)
        assertEquals(3, after.tabs.size)
        assertTrue(after.tabs.any { it.id == third.id })
    }

    @Test fun theLastActiveTabIsTheOneOpenedAgain() {
        val first = manager()
        first.restore()
        val a = first.tabs[0]
        first.newTab()
        first.switchTo(a.id)
        val after = manager(counterStart = 100)
        after.restore()
        assertEquals(a.id, after.activeTabId)
    }

    @Test fun closingTheLastTabLeavesOneNewHomeTabStored() {
        val m = manager()
        m.restore()
        val only = m.tabs.single()
        m.close(only.id)
        val rows = BrowserDataRepository(db).getTabs()
        assertEquals(1, rows.size)
        assertTrue(rows[0].tabId != only.id)
        assertEquals("https://home.test/", rows[0].url)
        assertTrue(rows[0].isActive)
    }

    @Test fun storedDataStaysCompatibleWithThePhase2aRepositoryApi() {
        val repo = BrowserDataRepository(db)
        repo.saveTab(BrowserTabEntity("x", "https://x.test/", "X", 0, 5L, true))
        val stored = RoomTabPersistence(repo).load()
        assertEquals(StoredTabs(listOf(TabInfo("x", "https://x.test/", "X", 5L)), "x"), stored)
    }
}
