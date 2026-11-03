package com.mnb.browser.translation.db

import android.database.sqlite.SQLiteConstraintException
import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.ChunkId
import com.mnb.browser.translation.ContentHash
import com.mnb.browser.translation.NovelId
import com.mnb.browser.translation.TranslationChunk
import com.mnb.browser.translation.TranslationMetadata
import com.mnb.browser.translation.TranslationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationRepositoryTest : DbTestBase() {
    private val repo get() = TranslationRepository(db)
    private val chapters get() = ChapterRepository(db)
    private val c1 = ChapterId("c1")

    private fun chunk(
        id: String,
        index: Int,
        text: String,
        translated: String? = null,
        status: TranslationStatus = TranslationStatus.PENDING,
    ) = TranslationChunk(ChunkId(id), c1, index, text, translated, status)

    // Translation persistence

    @Test fun translationRoundTripsWithMetadata() {
        seedChapter(text = "Hello")
        val meta = TranslationMetadata("openai", 100L, 200L, modelId = "m1", translationVersion = 2, isMock = false)
        repo.saveTranslation(c1, "မင်္ဂလာပါ", TranslationStatus.COMPLETED, meta)

        val stored = repo.getTranslation(c1)!!
        assertEquals("မင်္ဂလာပါ", stored.translatedText)
        assertEquals(TranslationStatus.COMPLETED, stored.status)
        assertEquals(ContentHash.of("Hello"), stored.sourceContentHash)
        assertEquals(meta, stored.metadata)
    }

    @Test fun mockFlagIsPersisted() {
        seedChapter()
        repo.saveTranslation(c1, "[MOCK] x", TranslationStatus.COMPLETED, TranslationMetadata("mock", 1L, isMock = true))
        assertTrue(repo.getTranslation(c1)!!.metadata.isMock)
    }

    @Test fun chapterReadShowsTheStoredTranslation() {
        seedChapter()
        repo.saveTranslation(c1, "Done", TranslationStatus.COMPLETED, TranslationMetadata("p", 500L))
        val chapter = chapters.get(c1)!!
        assertEquals("Done", chapter.translatedText)
        assertEquals(TranslationStatus.COMPLETED, chapter.status)
        assertEquals(500L, chapter.updatedAtMillis)
    }

    @Test fun savingAgainReplacesTheTranslation() {
        seedChapter()
        repo.saveTranslation(c1, "First", TranslationStatus.COMPLETED, TranslationMetadata("p", 1L))
        repo.saveTranslation(c1, "Second", TranslationStatus.COMPLETED, TranslationMetadata("p", 1L, 2L, translationVersion = 2))
        assertEquals("Second", repo.getTranslation(c1)!!.translatedText)
        assertEquals(2, repo.getTranslation(c1)!!.metadata.translationVersion)
    }

    @Test fun invalidTranslationsAreRejected() {
        seedChapter()
        val meta = TranslationMetadata("p", 1L)
        assertThrows(IllegalArgumentException::class.java) {
            repo.saveTranslation(c1, null, TranslationStatus.COMPLETED, meta)
        }
        assertThrows(IllegalStateException::class.java) {
            repo.saveTranslation(ChapterId("missing"), "x", TranslationStatus.COMPLETED, meta)
        }
        assertNull(repo.getTranslation(c1))
    }

    @Test fun staleDetectionFollowsTheContentHash() {
        seedChapter(text = "Old")
        assertFalse(repo.isStale(c1))
        repo.saveTranslation(c1, "Done", TranslationStatus.COMPLETED, TranslationMetadata("p", 1L))
        assertFalse(repo.isStale(c1))

        chapters.saveOriginal(c1, NovelId("n1"), 1, "New", 20L)
        assertTrue(repo.isStale(c1))
        assertEquals(TranslationStatus.PENDING, repo.getTranslation(c1)!!.status)
        assertEquals(ContentHash.of("Old"), repo.getTranslation(c1)!!.sourceContentHash)
    }

    @Test fun staleIsFalseForUnknownChapterOrMissingTranslation() {
        assertFalse(repo.isStale(ChapterId("missing")))
        seedChapter()
        assertFalse(repo.isStale(c1))
    }

    // Chunk persistence

    @Test fun chunksAreStoredAndListedInIndexOrder() {
        seedChapter()
        repo.saveChunk(chunk("k2", 1, "B"))
        repo.saveChunk(chunk("k1", 0, "A", "a", TranslationStatus.COMPLETED), providerId = "p")
        assertEquals(listOf(0, 1), repo.getChunks(c1).map { it.chunkIndex })
        assertEquals("a", repo.getChunks(c1)[0].translatedText)
        assertEquals(1, repo.completedChunkCount(c1))
    }

    @Test fun savingTheSameChunkIdUpdatesIt() {
        seedChapter()
        repo.saveChunk(chunk("k1", 0, "A"))
        repo.saveChunk(chunk("k1", 0, "A", "a", TranslationStatus.COMPLETED), "p")
        assertEquals(1, repo.getChunks(c1).size)
        assertEquals(TranslationStatus.COMPLETED, repo.getChunks(c1)[0].status)
    }

    @Test fun aDifferentChunkAtTheSameIndexReplacesTheOldOne() {
        seedChapter()
        repo.saveChunk(chunk("old", 0, "A"))
        repo.saveChunk(chunk("new", 0, "A2"))
        assertEquals(listOf("new"), repo.getChunks(c1).map { it.chunkId.value })
    }

    @Test fun chunkNeedsAnExistingChapter() {
        assertThrows(SQLiteConstraintException::class.java) { repo.saveChunk(chunk("k1", 0, "A")) }
    }

    @Test fun completedChunkIsReusedWhenTextAndProviderMatch() {
        seedChapter()
        repo.saveChunk(chunk("k1", 0, "A", "a", TranslationStatus.COMPLETED), "openai")
        val reused = repo.findReusableChunk(c1, 0, "A", "openai")
        assertNotNull(reused)
        assertEquals("a", reused!!.translatedText)
    }

    @Test fun chunkIsNotReusedWhenItMustBeTranslatedAgain() {
        seedChapter()
        repo.saveChunk(chunk("k1", 0, "A", "a", TranslationStatus.COMPLETED), "mock")
        repo.saveChunk(chunk("k2", 1, "B"), "openai")
        repo.saveChunk(chunk("k3", 2, "C", null, TranslationStatus.FAILED), "openai")

        assertNull("different provider (mock vs real)", repo.findReusableChunk(c1, 0, "A", "openai"))
        assertNull("changed text", repo.findReusableChunk(c1, 0, "A edited", "mock"))
        assertNull("still pending", repo.findReusableChunk(c1, 1, "B", "openai"))
        assertNull("failed", repo.findReusableChunk(c1, 2, "C", "openai"))
        assertNull("not stored", repo.findReusableChunk(c1, 9, "Z", "openai"))
    }

    @Test fun changedChapterTextDeletesItsChunks() {
        seedChapter(text = "Old")
        repo.saveChunk(chunk("k1", 0, "A", "a", TranslationStatus.COMPLETED), "p")
        chapters.saveOriginal(c1, NovelId("n1"), 1, "New", 30L)
        assertTrue(repo.getChunks(c1).isEmpty())
    }

    @Test fun unchangedChapterTextKeepsItsChunks() {
        seedChapter(text = "Same")
        repo.saveChunk(chunk("k1", 0, "A", "a", TranslationStatus.COMPLETED), "p")
        chapters.saveOriginal(c1, NovelId("n1"), 1, "Same", 30L)
        assertEquals(1, repo.getChunks(c1).size)
    }

    // Cascade

    @Test fun deletingAChapterDeletesTranslationChunksAndJobs() {
        seedChapter()
        repo.saveTranslation(c1, "x", TranslationStatus.COMPLETED, TranslationMetadata("p", 1L))
        repo.saveChunk(chunk("k1", 0, "A"))
        TranslationJobRepository(db).create(c1, 1, 1L)

        chapters.delete(c1)

        assertNull(repo.getTranslation(c1))
        assertTrue(repo.getChunks(c1).isEmpty())
        assertTrue(TranslationJobRepository(db).findUnfinished().isEmpty())
    }
}
