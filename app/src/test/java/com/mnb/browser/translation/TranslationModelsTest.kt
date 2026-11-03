package com.mnb.browser.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationModelsTest {
    private val novel = NovelId("n1")
    private val chapter = ChapterId("c1")

    // NovelTranslationInfo

    @Test fun novelInfoDefaults() {
        val info = NovelTranslationInfo(novel, "My Novel", SourceLanguage.KOREAN, 1000L)
        assertEquals(TargetLanguage.MYANMAR, info.targetLanguage)
        assertEquals(TranslationStyle.LITERARY, info.style)
        assertEquals(SourceLanguage.KOREAN, info.sourceLanguage)
    }

    @Test fun novelInfoAcceptsEverySourceLanguage() {
        for (lang in SourceLanguage.values()) {
            assertEquals(lang, NovelTranslationInfo(novel, "T", lang, 0L).sourceLanguage)
        }
    }

    @Test fun novelInfoRejectsBadData() {
        assertThrows(IllegalArgumentException::class.java) {
            NovelTranslationInfo(novel, " ", SourceLanguage.ENGLISH, 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NovelTranslationInfo(novel, "T", SourceLanguage.ENGLISH, -1L)
        }
    }

    // ChapterTranslation

    @Test fun chapterDefaults() {
        val c = ChapterTranslation(chapter, novel, 1, "Original", 5L)
        assertNull(c.translatedText)
        assertEquals(TranslationStatus.PENDING, c.status)
        assertEquals(ContentHash.of("Original"), c.contentHash)
        assertEquals(5L, c.updatedAtMillis)
    }

    @Test fun chapterMatchesContentDetectsChanges() {
        val c = ChapterTranslation(chapter, novel, 1, "Original", 5L)
        assertTrue(c.matchesContent("Original"))
        assertFalse(c.matchesContent("Original edited"))
    }

    @Test fun chapterRejectsBadData() {
        assertThrows(IllegalArgumentException::class.java) {
            ChapterTranslation(chapter, novel, -1, "Original", 5L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChapterTranslation(chapter, novel, 1, "  ", 5L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChapterTranslation(chapter, novel, 1, "Original", -5L)
        }
    }

    @Test fun completedChapterNeedsTranslatedText() {
        assertThrows(IllegalArgumentException::class.java) {
            ChapterTranslation(chapter, novel, 1, "Original", 5L, status = TranslationStatus.COMPLETED)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChapterTranslation(chapter, novel, 1, "Original", 5L, translatedText = " ", status = TranslationStatus.COMPLETED)
        }
        val ok = ChapterTranslation(chapter, novel, 1, "Original", 5L, translatedText = "Done", status = TranslationStatus.COMPLETED)
        assertEquals("Done", ok.translatedText)
    }

    // TranslationChunk

    @Test fun chunkDefaults() {
        val k = TranslationChunk(ChunkId("k1"), chapter, 0, "Text")
        assertNull(k.translatedText)
        assertEquals(TranslationStatus.PENDING, k.status)
    }

    @Test fun chunkRejectsBadData() {
        assertThrows(IllegalArgumentException::class.java) { TranslationChunk(ChunkId("k"), chapter, -1, "Text") }
        assertThrows(IllegalArgumentException::class.java) { TranslationChunk(ChunkId("k"), chapter, 0, "") }
        assertThrows(IllegalArgumentException::class.java) {
            TranslationChunk(ChunkId("k"), chapter, 0, "Text", status = TranslationStatus.COMPLETED)
        }
    }
}
