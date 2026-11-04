package com.mnb.browser.reader.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BoundChapterIdentityResolverTest {
    private val resolver = BoundChapterIdentityResolver(c1BoundStore("ko"))

    @Test fun validChapterIdentity() {
        val identity = resolver.resolve(c1Chapter(12))
        assertNotNull(identity)
        assertEquals(C1_NOVEL, identity!!.novelId)
        assertEquals(12, identity.chapterNumber)
        assertEquals("ko", identity.sourceLanguageCode)
        assertEquals("$C1_NOVEL_URL/ch12", identity.sourceUrl)
        assertEquals(BoundChapterIdentityResolver.chapterIdFor(C1_NOVEL, 12), identity.chapterId)
    }

    @Test fun chapterIdIsStableAndDiffersPerChapter() {
        val a = resolver.resolve(c1Chapter(1))!!.chapterId
        assertEquals(a, resolver.resolve(c1Chapter(1, url = "$C1_NOVEL_URL/other-url"))!!.chapterId)
        assertNotEquals(a, resolver.resolve(c1Chapter(2))!!.chapterId)
    }

    @Test fun unknownChapterIdentityWhenTitleHasNoNumber() {
        assertNull(resolver.resolve(c1Chapter(null, title = "Prologue")))
        assertNull(resolver.resolve(c1Chapter(null, title = null)))
    }

    @Test fun ambiguousTitleGivesNoIdentity() =
        assertNull(resolver.resolve(c1Chapter(null, title = "Chapter 12-13")))

    @Test fun noBindingGivesNoIdentity() =
        assertNull(resolver.resolve(c1Chapter(5, url = "https://unbound.example/novel/1/ch5")))

    @Test fun unsupportedSourceLanguageGivesNoIdentity() {
        // A store can never save "fr", so plant a raw record the way a corrupted store might.
        val kv = C1KeyValueStore()
        kv.map["novelbind:list"] = "$C1_NOVEL_URL\tnovel-7\tNovel 7\tfr"
        assertNull(BoundChapterIdentityResolver(NovelBindingStore(kv)).resolve(c1Chapter(1)))
    }

    @Test fun chineseJapaneseKoreanTitlesResolve() {
        assertEquals(3, resolver.resolve(c1Chapter(null, title = "第3章 开始"))?.chapterNumber)
        assertEquals(3, resolver.resolve(c1Chapter(null, title = "제3화 시작"))?.chapterNumber)
        assertEquals(3, resolver.resolve(c1Chapter(null, title = "第3話 始まり"))?.chapterNumber)
    }
}
