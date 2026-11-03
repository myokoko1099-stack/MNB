package com.mnb.browser.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPositionRepositoryTest {
    private class MapStore : KeyValueStore {
        val map = mutableMapOf<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
    }

    private val url = "https://example.com/novel/chapter-1"

    @Test fun savedPositionIsRestored() {
        val repo = ReaderPositionRepository(MapStore())
        repo.save(url, ReaderPosition(12, 340, 50))
        assertEquals(ReaderPosition(12, 340, 50), repo.load(url, 50))
    }

    @Test fun unknownChapterHasNoPosition() {
        assertNull(ReaderPositionRepository(MapStore()).load(url, 50))
    }

    @Test fun positionsAreKeptPerChapter() {
        val repo = ReaderPositionRepository(MapStore())
        repo.save("https://a/1", ReaderPosition(3, 10, 20))
        repo.save("https://a/2", ReaderPosition(7, 20, 30))
        assertEquals(3, repo.load("https://a/1", 20)?.index)
        assertEquals(7, repo.load("https://a/2", 30)?.index)
    }

    @Test fun changedItemCountClampsIndexAndDropsOffset() {
        val repo = ReaderPositionRepository(MapStore())
        repo.save(url, ReaderPosition(12, 340, 50))
        assertEquals(ReaderPosition(12, 0, 30), repo.load(url, 30))
        assertEquals(ReaderPosition(9, 0, 10), repo.load(url, 10))
    }

    @Test fun corruptValuesAreIgnored() {
        val store = MapStore()
        val repo = ReaderPositionRepository(store)
        for (bad in listOf("garbage", "1,2", "a,b,c", "-1,0,10", "1,-5,10", "1,2,0")) {
            store.map["pos:$url"] = bad
            assertNull(bad, repo.load(url, 10))
        }
    }

    @Test fun oldestChaptersAreEvicted() {
        val store = MapStore()
        val repo = ReaderPositionRepository(store, maxEntries = 2)
        repo.save("a", ReaderPosition(1, 0, 5))
        repo.save("b", ReaderPosition(1, 0, 5))
        repo.save("c", ReaderPosition(1, 0, 5))
        assertNull(repo.load("a", 5))
        assertFalse(store.map.containsKey("pos:a"))
        assertTrue(repo.load("b", 5) != null)
        assertTrue(repo.load("c", 5) != null)
    }

    @Test fun resavingRefreshesRecency() {
        val repo = ReaderPositionRepository(MapStore(), maxEntries = 2)
        repo.save("a", ReaderPosition(1, 0, 5))
        repo.save("b", ReaderPosition(1, 0, 5))
        repo.save("a", ReaderPosition(2, 0, 5))
        repo.save("c", ReaderPosition(1, 0, 5))
        assertNull(repo.load("b", 5))
        assertEquals(2, repo.load("a", 5)?.index)
    }

    @Test fun invalidInputsAreIgnored() {
        val store = MapStore()
        val repo = ReaderPositionRepository(store)
        repo.save("", ReaderPosition(1, 0, 5))
        repo.save("a\nb", ReaderPosition(1, 0, 5))
        repo.save(url, ReaderPosition(1, 0, 0))
        assertTrue(store.map.isEmpty())
        assertNull(repo.load(url, 0))
    }
}
