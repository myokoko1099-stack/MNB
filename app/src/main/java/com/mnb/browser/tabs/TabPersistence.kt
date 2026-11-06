package com.mnb.browser.tabs

import com.mnb.browser.translation.db.BrowserDataRepository
import com.mnb.browser.translation.db.BrowserTabEntity

/** Blocking storage for the tab list. Call from a background thread only. */
interface TabPersistence {
    fun load(): StoredTabs
    fun save(tabs: List<TabInfo>, activeTabId: String?)
}

/** Phase 2A Room storage (browser_tabs table, database version 4). No schema change in Phase 2B. */
class RoomTabPersistence(private val repository: BrowserDataRepository) : TabPersistence {

    override fun load(): StoredTabs {
        val rows = repository.getTabs()
        val active = rows.filter { it.isActive }.maxByOrNull { it.lastAccessedAtMillis }?.tabId
        return StoredTabs(
            tabs = rows.map { TabInfo(it.tabId, it.url, it.title, it.lastAccessedAtMillis) },
            activeTabId = active,
        )
    }

    override fun save(tabs: List<TabInfo>, activeTabId: String?) {
        repository.replaceTabs(
            tabs.mapIndexed { index, tab ->
                BrowserTabEntity(
                    tabId = tab.id,
                    url = tab.url,
                    title = tab.title,
                    position = index,
                    lastAccessedAtMillis = tab.lastAccessedAtMillis,
                    isActive = tab.id == activeTabId,
                )
            },
        )
    }
}

/** Opens the real storage on first use, which is always on the background thread. */
class LazyTabPersistence(factory: () -> TabPersistence) : TabPersistence {
    private val delegate by lazy(factory)

    override fun load(): StoredTabs = delegate.load()

    override fun save(tabs: List<TabInfo>, activeTabId: String?) = delegate.save(tabs, activeTabId)
}
