package com.mnb.browser.tabs

import com.mnb.browser.navigation.UrlPolicy
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 2G: tabs on the app's own new-tab page, links opened in new tabs, and migration of old home tabs. */
class TabManagerNewTabTest {

    private class MemoryPersistence(var stored: StoredTabs = StoredTabs(emptyList(), null)) : TabPersistence {
        override fun load(): StoredTabs = stored
        override fun save(tabs: List<TabInfo>, activeTabId: String?) {
            stored = StoredTabs(tabs, activeTabId)
        }
    }

    private fun manager(
        stored: StoredTabs = StoredTabs(emptyList(), null),
        maxTabs: Int = 20,
        defaultHome: Boolean = true,
    ): TabManager {
        var counter = 0
        val direct = Executor { it.run() }
        val m = if (defaultHome) {
            TabManager(
                persistence = MemoryPersistence(stored),
                ioExecutor = direct,
                mainPoster = { it.run() },
                newId = { "id${++counter}" },
                maxTabs = maxTabs,
            )
        } else {
            TabManager(
                persistence = MemoryPersistence(stored),
                ioExecutor = direct,
                mainPoster = { it.run() },
                newId = { "id${++counter}" },
                homeUrl = "https://home.test/",
                maxTabs = maxTabs,
            )
        }
        m.restore()
        return m
    }

    @Test fun byDefaultANewTabIsTheAppsOwnNewTabPage() {
        val m = manager()
        assertEquals(UrlPolicy.NEW_TAB_URL, m.tabs.single().url)
        val second = m.newTab()!!
        assertEquals(UrlPolicy.NEW_TAB_URL, second.url)
        assertTrue(m.tabs.none { it.url.contains("duckduckgo", ignoreCase = true) })
        assertEquals("New tab", TabDisplay.label(second))
    }

    @Test fun aLongPressedLinkOpensInANewActiveTab() {
        val m = manager()
        val first = m.activeTabId
        val link = m.newTab("https://a.test/page")
        assertNotNull(link)
        assertEquals("https://a.test/page", link!!.url)
        assertEquals(link.id, m.activeTabId)
        assertNotEquals(first, m.activeTabId)
        assertEquals(2, m.tabs.size)
    }

    @Test fun anHttpLinkIsUpgradedAndAnUnsafeOneFallsBackToTheNewTabPage() {
        val m = manager()
        assertEquals("https://a.test/", m.newTab("http://a.test/")!!.url)
        assertEquals(UrlPolicy.NEW_TAB_URL, m.newTab("javascript:alert(1)")!!.url)
    }

    @Test fun linkTabsRespectTheTabLimit() {
        val m = manager(maxTabs = 2)
        assertNotNull(m.newTab("https://a.test/"))
        assertFalse(m.canOpenNewTab)
        assertNull(m.newTab("https://b.test/"))
        assertEquals(2, m.tabs.size)
    }

    @Test fun aTabCanGoBackToTheNewTabPageAndThenShowsNewTab() {
        val m = manager()
        val id = m.activeTabId!!
        m.updatePage(id, "https://a.test/", "A page")
        m.updatePage(id, UrlPolicy.NEW_TAB_URL, null)
        val tab = m.tabs.single()
        assertEquals(UrlPolicy.NEW_TAB_URL, tab.url)
        // The old title is still remembered, but the tab is shown as a new tab.
        assertEquals("A page", tab.title)
        assertEquals("New tab", TabDisplay.label(tab))
    }

    @Test fun tabsStoredOnTheOldHomePageBecomeFreshNewTabs() {
        val stored = StoredTabs(
            listOf(
                TabInfo("a", "https://duckduckgo.com/", "DuckDuckGo", 5),
                TabInfo("b", "https://b.test/", "B", 9),
            ),
            "a",
        )
        val m = manager(stored, defaultHome = false)
        assertEquals(2, m.tabs.size)
        val migrated = m.tabs[0]
        assertEquals("https://home.test/", migrated.url)
        assertEquals("", migrated.title)
        // A new id, so the saved page state of the old tab (the old home page) is not restored.
        assertNotEquals("a", migrated.id)
        assertEquals(migrated.id, m.activeTabId)
        assertEquals(TabInfo("b", "https://b.test/", "B", 9), m.tabs[1])
    }

    @Test fun anOldHomeTabThatIsNotActiveIsMigratedToo() {
        val stored = StoredTabs(
            listOf(TabInfo("a", "https://b.test/", "B", 1), TabInfo("c", "https://duckduckgo.com", "", 2)),
            "a",
        )
        val m = manager(stored, defaultHome = false)
        assertEquals("a", m.activeTabId)
        assertEquals("https://home.test/", m.tabs[1].url)
        assertNotEquals("c", m.tabs[1].id)
    }

    @Test fun anOldHomeAddressWithASearchIsAnOrdinaryPage() {
        val stored = StoredTabs(listOf(TabInfo("a", "https://duckduckgo.com/?q=kotlin", "", 1)), "a")
        val m = manager(stored, defaultHome = false)
        assertEquals("a", m.tabs.single().id)
        assertEquals("https://duckduckgo.com/?q=kotlin", m.tabs.single().url)
    }
}
