package com.mnb.browser.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSettingsRepositoryTest {
    private class MapStore : KeyValueStore {
        val map = mutableMapOf<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
    }

    @Test fun emptyStoreGivesDefaults() {
        assertEquals(ReaderSettings(), ReaderSettingsRepository(MapStore()).load())
    }

    @Test fun defaultsKeepPartOneLook() {
        val d = ReaderSettings()
        assertEquals(18, d.fontSizeSp)
        assertEquals(32, d.lineHeightSp)
        assertEquals(20, d.marginDp)
        assertEquals(16, d.paragraphSpacingDp)
        assertEquals(ReaderTheme.SYSTEM, d.theme)
    }

    @Test fun savedSettingsAreRestoredByANewRepository() {
        val store = MapStore()
        val saved = ReaderSettings(24, 32, 28, ReaderTheme.DARK)
        ReaderSettingsRepository(store).save(saved)
        // A second repository over the same store simulates an app restart.
        assertEquals(saved, ReaderSettingsRepository(store).load())
    }

    @Test fun everyThemeRoundTrips() {
        val store = MapStore()
        val repo = ReaderSettingsRepository(store)
        for (theme in ReaderTheme.values()) {
            repo.save(ReaderSettings(theme = theme))
            assertEquals(theme, repo.load().theme)
        }
    }

    @Test fun outOfRangeValuesAreClampedOnSaveAndLoad() {
        val store = MapStore()
        val repo = ReaderSettingsRepository(store)
        repo.save(ReaderSettings(999, -5, 1000, ReaderTheme.LIGHT))
        assertEquals(ReaderSettings(30, 8, 40, ReaderTheme.LIGHT), repo.load())
        store.map["settings"] = "1,500,0,DARK"
        assertEquals(ReaderSettings(14, 48, 4, ReaderTheme.DARK), repo.load())
    }

    @Test fun corruptValuesFallBackToDefaults() {
        val store = MapStore()
        val repo = ReaderSettingsRepository(store)
        for (bad in listOf("", "garbage", "1,2,3", "1,2,3,DARK,5")) {
            store.map["settings"] = bad
            assertEquals(bad, ReaderSettings(), repo.load())
        }
    }

    @Test fun badFieldsFallBackIndividually() {
        val store = MapStore()
        store.map["settings"] = "x,30,y,PURPLE"
        assertEquals(ReaderSettings(marginDp = 30), ReaderSettingsRepository(store).load())
    }

    @Test fun lineHeightScalesWithFontSize() {
        assertEquals(53, ReaderSettings(fontSizeSp = 30).lineHeightSp)
        assertEquals(25, ReaderSettings(fontSizeSp = 14).lineHeightSp)
    }

    @Test fun settingsAndPositionsDoNotInterfere() {
        val store = MapStore()
        val positions = ReaderPositionRepository(store)
        val settings = ReaderSettingsRepository(store)
        positions.save("https://a/1", ReaderPosition(5, 40, 20))
        settings.save(ReaderSettings(fontSizeSp = 22))
        positions.save("https://a/2", ReaderPosition(2, 0, 10))
        assertEquals(ReaderPosition(5, 40, 20), positions.load("https://a/1", 20))
        assertEquals(22, settings.load().fontSizeSp)
    }

    @Test fun positionEvictionLeavesSettingsAlone() {
        val store = MapStore()
        val positions = ReaderPositionRepository(store, maxEntries = 1)
        val settings = ReaderSettingsRepository(store)
        settings.save(ReaderSettings(fontSizeSp = 26))
        positions.save("a", ReaderPosition(1, 0, 5))
        positions.save("b", ReaderPosition(1, 0, 5))
        assertEquals(26, settings.load().fontSizeSp)
    }
}
