package com.mnb.browser.reader.translation

import com.mnb.browser.translation.NovelId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelBindingStoreTest {
    private fun binding(prefix: String = C1_NOVEL_URL, id: String = "novel-7", lang: String = "en") =
        NovelBinding(prefix, NovelId(id), "Novel", lang)

    @Test fun savedBindingIsFoundAndSurvivesANewStoreInstance() {
        val kv = C1KeyValueStore()
        assertTrue(NovelBindingStore(kv).save(binding(lang = " KO ")))
        val found = NovelBindingStore(kv).find("$C1_NOVEL_URL/ch1")
        assertEquals(NovelId("novel-7"), found?.novelId)
        assertEquals("ko", found?.sourceLanguageCode)
    }

    @Test fun supportsEnglishChineseKoreanJapanese() {
        val store = NovelBindingStore(C1KeyValueStore())
        listOf("en", "zh", "ko", "ja").forEachIndexed { i, code ->
            assertTrue(store.save(binding(prefix = "https://s$i.example/n", id = "n$i", lang = code)))
        }
        assertEquals(4, store.list().size)
    }

    @Test fun unsupportedLanguageIsRejected() {
        val store = NovelBindingStore(C1KeyValueStore())
        assertFalse(store.save(binding(lang = "fr")))
        assertFalse(store.save(binding(lang = "")))
        assertTrue(store.list().isEmpty())
    }

    @Test fun invalidPrefixOrTitleIsRejected() {
        val store = NovelBindingStore(C1KeyValueStore())
        assertFalse(store.save(binding(prefix = "site.example/novel")))
        assertFalse(store.save(binding(prefix = "https://")))
        assertFalse(store.save(binding(prefix = "https://a.example/with space")))
        assertFalse(store.save(NovelBinding(C1_NOVEL_URL, C1_NOVEL, "  ", "en")))
        assertFalse(store.save(NovelBinding(C1_NOVEL_URL, C1_NOVEL, "bad\ttitle", "en")))
    }

    @Test fun noBindingMeansNoMatch() {
        val store = c1BoundStore()
        assertNull(store.find("https://other.example/novel/7/ch1"))
        assertNull(store.find(""))
    }

    @Test fun prefixMustEndOnAPathBoundary() {
        val store = c1BoundStore()
        assertEquals(C1_NOVEL, store.find(C1_NOVEL_URL)?.novelId)
        assertEquals(C1_NOVEL, store.find("$C1_NOVEL_URL?page=2")?.novelId)
        assertNull(store.find("https://site.example/novel/70/ch1"))
        assertNull(store.find("https://site.example/novel/7x"))
    }

    @Test fun longestPrefixWins() {
        val store = c1BoundStore()
        assertTrue(store.save(binding(prefix = "$C1_NOVEL_URL/special", id = "novel-7b")))
        assertEquals(NovelId("novel-7b"), store.find("$C1_NOVEL_URL/special/ch1")?.novelId)
        assertEquals(C1_NOVEL, store.find("$C1_NOVEL_URL/ch1")?.novelId)
    }

    @Test fun samePrefixIsReplaced() {
        val store = c1BoundStore("en")
        assertTrue(store.save(binding(id = "novel-7", lang = "en")))
        assertEquals(1, store.list().size)
    }

    @Test fun oneNovelCannotHaveTwoLanguages() {
        val store = c1BoundStore("en")
        assertFalse(store.save(binding(prefix = "https://mirror.example/n", id = "novel-7", lang = "ko")))
        assertEquals(1, store.list().size)
    }

    @Test fun removeAndMalformedRecords() {
        val kv = C1KeyValueStore()
        val store = NovelBindingStore(kv)
        store.save(binding())
        kv.map["novelbind:list"] = kv.map["novelbind:list"] + "\ngarbage line\nhttps://x.example/a\t\tT\ten"
        assertEquals(1, store.list().size)
        assertTrue(store.remove(C1_NOVEL_URL))
        assertFalse(store.remove(C1_NOVEL_URL))
        assertTrue(store.list().isEmpty())
    }
}
