package com.mnb.browser.translation.chunking

import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.NovelId
import com.mnb.browser.translation.TranslationStatus
import com.mnb.browser.translation.db.ChapterRepository
import com.mnb.browser.translation.db.DbTestBase
import com.mnb.browser.translation.db.TranslationRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterChunkRepositoryTest : DbTestBase() {
    private val c1 = ChapterId("c1")
    private val text = (0 until 12).joinToString("\n\n") { "P$it " + "x".repeat(96) }
    private fun repo(max: Int = 350) = ChapterChunkRepository(db, ChunkingEngine(ChunkingConfig(max, 50)))

    // 14
    @Test fun chunksArePersistedAndReassemble() {
        seedChapter(text = text)
        val outcome = repo().chunkChapter(c1)

        assertEquals(ChunkSaveResult.CREATED, outcome.result)
        val rows = TranslationRepository(db).getChunks(c1)
        assertEquals(outcome.chunks.size, rows.size)
        assertEquals(outcome.chunks.map { it.chunk }, rows)
        assertTrue(rows.all { it.status == TranslationStatus.PENDING })
        assertTrue(repo().verify(c1).isValid)
        assertEquals(ParagraphSplitter.normalize(text), ChunkReassembler.reassemble(repo().loadChunks(c1)))
    }

    // 15
    @Test fun chunkingTwiceDoesNotDuplicateOrDiscardTranslations() {
        seedChapter(text = text)
        val first = repo().chunkChapter(c1).chunks
        val done = first[0].chunk.copy(translatedText = "translated", status = TranslationStatus.COMPLETED)
        TranslationRepository(db).saveChunk(done, "mock")

        val again = repo().chunkChapter(c1)

        assertEquals(ChunkSaveResult.UNCHANGED, again.result)
        val rows = TranslationRepository(db).getChunks(c1)
        assertEquals(first.size, rows.size)
        assertEquals(TranslationStatus.COMPLETED, rows[0].status)
        assertEquals("translated", rows[0].translatedText)
    }

    @Test fun changedConfigReplacesChunksWithoutLeftovers() {
        seedChapter(text = text)
        repo(350).chunkChapter(c1)

        assertTrue(!repo(200).verify(c1).isValid)
        val outcome = repo(200).chunkChapter(c1)

        assertEquals(ChunkSaveResult.UPDATED, outcome.result)
        assertEquals(outcome.chunks.size, TranslationRepository(db).getChunks(c1).size)
        assertTrue(repo(200).verify(c1).isValid)
    }

    @Test fun changedChapterTextIsChunkedAgain() {
        seedChapter(text = text)
        repo().chunkChapter(c1)
        ChapterRepository(db).saveOriginal(c1, NovelId("n1"), 1, "New first.\n\nNew second.", 50L)
        assertTrue(TranslationRepository(db).getChunks(c1).isEmpty())

        assertEquals(ChunkSaveResult.CREATED, repo().chunkChapter(c1).result)
        assertTrue(repo().verify(c1).isValid)
    }

    @Test fun missingChapterIsRejected() {
        assertThrows(IllegalStateException::class.java) { repo().chunkChapter(ChapterId("nope")) }
    }
}
