package com.mnb.browser.tabs

import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests of the tab list logic: no WebView, no Room (a fake storage stands in). */
class TabManagerTest {

    private class FakePersistence(var stored: StoredTabs = StoredTabs(emptyList(), null)) : TabPersistence {
        var saves = 0
        var failLoad = false
        override fun load(): StoredTabs {
            if (failLoad) error("load failed")
            return stored
        }
        override fun save(tabs: List<TabInfo>, activeTabId: String?) {
            saves++
            stored = StoredTabs(tabs, activeTabId)
        }
    }

    private class Fixture(initial: StoredTabs = StoredTabs(emptyList(), null), maxTabs: Int = 20) {
        val persistence = FakePersistence(initial)
        val queue = ArrayDeque<Runnable>()
        var direct = true
        var time = 1000L
        var counter = 0
        val closed = mutableListOf<String>()
        val restoredIds = mutableListOf<List<String>>()
        val manager = TabManager(
            persistence = persistence,
            ioExecutor = Executor { task -> if (direct) task.run() else queue.add(task) },
            mainPoster = { task -> task.run() },
            clock = { time },
            newId = { "id${++counter}" },
            homeUrl = HOME,
            maxTabs = maxTabs,
        )
        init {
            manager.onTabClosed = { closed.add(it) }
            manager.onRestored = { restoredIds.add(it) }
        }
        fun restored(): TabManager {
            manager.restore()
            return manager
        }
    }

    private fun tab(id: String, url: String = "https://$id.test/", at: Long = 1L, title: String = "") =
        TabInfo(id, url, title, at)

    // ---- restore ----

    @Test fun restoreWithNothingStoredOpensOneHomeTab() {
        val f = Fixture()
        val m = f.restored()
        assertTrue(m.isRestored)
        assertEquals(1, m.tabs.size)
        assertEquals(HOME, m.tabs[0].url)
        assertEquals(m.tabs[0].id, m.activeTabId)
        assertEquals(listOf(m.tabs[0].id), f.restoredIds.single())
    }

    @Test fun restoreBringsBackStoredTabsAndTheActiveOne() {
        val f = Fixture(StoredTabs(listOf(tab("a"), tab("b"), tab("c")), "b"))
        val m = f.restored()
        assertEquals(listOf("a", "b", "c"), m.tabs.map { it.id })
        assertEquals("b", m.activeTabId)
        assertEquals("https://b.test/", m.activeTab?.url)
    }

    @Test fun restoreFallsBackToTheMostRecentTabWhenNoneIsMarkedActive() {
        val f = Fixture(StoredTabs(listOf(tab("a", at = 5), tab("b", at = 9), tab("c", at = 7)), null))
        assertEquals("b", f.restored().activeTabId)
    }

    @Test fun restoreIgnoresAnActiveIdThatIsNotStored() {
        val f = Fixture(StoredTabs(listOf(tab("a", at = 1), tab("b", at = 2)), "ghost"))
        assertEquals("b", f.restored().activeTabId)
    }

    @Test fun restoreSurvivesAFailingStorage() {
        val f = Fixture()
        f.persistence.failLoad = true
        val m = f.restored()
        assertTrue(m.isRestored)
        assertEquals(1, m.tabs.size)
    }

    @Test fun restoreCleansUpBadStoredData() {
        val stored = StoredTabs(
            listOf(
                tab("a", url = "http://a.test/page"),
                tab("a", url = "https://dup.test/"),
                tab("b", url = "javascript:alert(1)"),
                tab("", url = "https://blank-id.test/"),
                tab("c", url = ""),
                tab("d", url = "file:///etc/passwd"),
            ),
            "a",
        )
        val m = Fixture(stored).restored()
        assertEquals(listOf("a", "b", "d"), m.tabs.map { it.id })
        assertEquals("https://a.test/page", m.tabs[0].url)
        assertEquals(HOME, m.tabs[1].url)
        assertEquals(HOME, m.tabs[2].url)
    }

    @Test fun restoreKeepsAtMostMaxTabs() {
        val many = (1..5).map { tab("t$it") }
        val m = Fixture(StoredTabs(many, "t1"), maxTabs = 3).restored()
        assertEquals(3, m.tabs.size)
    }

    @Test fun restoreRunsOnlyOnce() {
        val f = Fixture(StoredTabs(listOf(tab("a")), "a"))
        f.manager.restore()
        f.manager.restore()
        assertEquals(1, f.restoredIds.size)
    }

    // ---- create / switch / close ----

    @Test fun newTabIsAddedAtTheEndAndBecomesActive() {
        val f = Fixture(StoredTabs(listOf(tab("a")), "a"))
        val m = f.restored()
        val created = m.newTab()
        assertNotNull(created)
        assertEquals(listOf("a", created!!.id), m.tabs.map { it.id })
        assertEquals(created.id, m.activeTabId)
        assertEquals(HOME, created.url)
    }

    @Test fun newTabHasItsOwnId() {
        val m = Fixture().restored()
        val a = m.newTab()!!
        val b = m.newTab()!!
        assertNotEquals(a.id, b.id)
        assertEquals(3, m.tabs.size)
    }

    @Test fun newTabIsRefusedBeforeRestoreAndAtTheLimit() {
        val f = Fixture(maxTabs = 2)
        assertNull(f.manager.newTab())
        val m = f.restored()
        assertTrue(m.canOpenNewTab)
        assertNotNull(m.newTab())
        assertFalse(m.canOpenNewTab)
        assertNull(m.newTab())
        assertEquals(2, m.tabs.size)
    }

    @Test fun newTabWithAnUnsafeAddressOpensHomeInstead() {
        val m = Fixture().restored()
        assertEquals(HOME, m.newTab("javascript:alert(1)")!!.url)
        assertEquals("https://x.test/", m.newTab("http://x.test/")!!.url)
    }

    @Test fun switchingChangesTheActiveTabAndItsAccessTime() {
        val f = Fixture(StoredTabs(listOf(tab("a"), tab("b")), "a"))
        val m = f.restored()
        f.time = 5000L
        assertTrue(m.switchTo("b"))
        assertEquals("b", m.activeTabId)
        assertEquals(5000L, m.tabs.first { it.id == "b" }.lastAccessedAtMillis)
        assertEquals(1L, m.tabs.first { it.id == "a" }.lastAccessedAtMillis)
    }

    @Test fun switchingToAnUnknownTabChangesNothing() {
        val m = Fixture(StoredTabs(listOf(tab("a")), "a")).restored()
        assertFalse(m.switchTo("nope"))
        assertEquals("a", m.activeTabId)
    }

    @Test fun closingAnInactiveTabKeepsTheActiveOne() {
        val f = Fixture(StoredTabs(listOf(tab("a"), tab("b"), tab("c")), "b"))
        val m = f.restored()
        assertTrue(m.close("c"))
        assertEquals(listOf("a", "b"), m.tabs.map { it.id })
        assertEquals("b", m.activeTabId)
        assertEquals(listOf("c"), f.closed)
    }

    @Test fun closingTheActiveTabActivatesItsNeighbour() {
        val f = Fixture(StoredTabs(listOf(tab("a"), tab("b"), tab("c")), "b"))
        val m = f.restored()
        m.close("b")
        assertEquals("c", m.activeTabId)
        m.close("c")
        assertEquals("a", m.activeTabId)
        assertEquals(listOf("a"), m.tabs.map { it.id })
    }

    @Test fun closingTheLastTabOpensAFreshHomeTab() {
        val f = Fixture(StoredTabs(listOf(tab("a", url = "https://a.test/x")), "a"))
        val m = f.restored()
        assertTrue(m.close("a"))
        assertEquals(1, m.tabs.size)
        assertNotEquals("a", m.tabs[0].id)
        assertEquals(HOME, m.tabs[0].url)
        assertEquals(m.tabs[0].id, m.activeTabId)
        assertEquals(listOf("a"), f.closed)
    }

    @Test fun closingAnUnknownTabDoesNothing() {
        val f = Fixture(StoredTabs(listOf(tab("a")), "a"))
        val m = f.restored()
        assertFalse(m.close("nope"))
        assertEquals(1, m.tabs.size)
        assertTrue(f.closed.isEmpty())
    }

    @Test fun theTabCountFollowsOpenAndClose() {
        val m = Fixture().restored()
        assertEquals(1, m.tabs.size)
        val second = m.newTab()!!
        m.newTab()
        assertEquals(3, m.tabs.size)
        m.close(second.id)
        assertEquals(2, m.tabs.size)
    }

    // ---- pages ----

    @Test fun eachTabKeepsItsOwnAddressAndTitle() {
        val m = Fixture(StoredTabs(listOf(tab("a"), tab("b")), "a")).restored()
        m.updatePage("a", "https://one.test/p1", "One")
        m.updatePage("b", "https://two.test/p2", "Two")
        m.updatePage("a", "https://one.test/p3", null)
        val a = m.tabs.first { it.id == "a" }
        val b = m.tabs.first { it.id == "b" }
        assertEquals("https://one.test/p3", a.url)
        assertEquals("One", a.title)
        assertEquals("https://two.test/p2", b.url)
        assertEquals("Two", b.title)
    }

    @Test fun updatePageIgnoresBlankUnsafeAndUnknownInput() {
        val f = Fixture(StoredTabs(listOf(tab("a", url = "https://a.test/")), "a"))
        val m = f.restored()
        m.updatePage("a", "", null)
        m.updatePage("a", "javascript:alert(1)", null)
        m.updatePage("a", "about:blank", null)
        m.updatePage("ghost", "https://g.test/", "G")
        assertEquals("https://a.test/", m.tabs.single().url)
        assertEquals(1, m.tabs.size)
    }

    // ---- persistence ----

    @Test fun everyChangeIsStoredAndMatchesTheLiveState() {
        val f = Fixture()
        val m = f.restored()
        val b = m.newTab()!!
        m.updatePage(b.id, "https://b.test/", "B")
        m.switchTo(m.tabs[0].id)
        m.close(b.id)
        assertEquals(m.tabs, f.persistence.stored.tabs)
        assertEquals(m.activeTabId, f.persistence.stored.activeTabId)
    }

    @Test fun aNewManagerOnTheSameStorageGetsTheSameTabsBack() {
        val first = Fixture()
        val m1 = first.restored()
        val b = m1.newTab()!!
        m1.updatePage(b.id, "https://b.test/page", "B page")
        m1.newTab()
        m1.switchTo(b.id)

        val second = Fixture(first.persistence.stored)
        val m2 = second.restored()
        assertEquals(m1.tabs.map { it.id }, m2.tabs.map { it.id })
        assertEquals(m1.tabs.map { it.url }, m2.tabs.map { it.url })
        assertEquals(b.id, m2.activeTabId)
        assertEquals("B page", m2.tabs.first { it.id == b.id }.title)
    }

    @Test fun manyQuickChangesAreCoalescedIntoOneWriteWithTheLatestState() {
        val f = Fixture(StoredTabs(listOf(tab("a")), "a"))
        val m = f.restored()
        f.persistence.saves = 0
        f.direct = false
        m.updatePage("a", "https://a.test/1", "1")
        m.updatePage("a", "https://a.test/2", "2")
        m.updatePage("a", "https://a.test/3", "3")
        m.newTab()
        assertEquals(1, f.queue.size)
        f.queue.removeFirst().run()
        assertEquals(1, f.persistence.saves)
        assertEquals(m.tabs, f.persistence.stored.tabs)
        assertEquals("https://a.test/3", f.persistence.stored.tabs[0].url)
    }

    @Test fun aWriteThatHappensAfterTheQueuedSaveIsNotLost() {
        val f = Fixture(StoredTabs(listOf(tab("a")), "a"))
        val m = f.restored()
        f.direct = false
        m.updatePage("a", "https://a.test/1", "1")
        f.queue.removeFirst().run()
        m.updatePage("a", "https://a.test/2", "2")
        assertEquals(1, f.queue.size)
        f.queue.removeFirst().run()
        assertEquals("https://a.test/2", f.persistence.stored.tabs[0].url)
    }

    private companion object {
        const val HOME = "https://home.test/"
    }
}
