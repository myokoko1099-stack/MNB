package com.mnb.browser.translation.chunking

import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.db.ChapterEntity
import com.mnb.browser.translation.db.MnbDatabase
import com.mnb.browser.translation.db.toEntity
import com.mnb.browser.translation.db.toModel
import java.util.concurrent.Callable

enum class ChunkSaveResult { CREATED, UNCHANGED, UPDATED }

data class ChunkingOutcome(val result: ChunkSaveResult, val chunks: List<ChapterChunk>)

/**
 * Chunks a stored chapter and saves the chunks in the existing translation_chunks table (no
 * schema change). The paragraph indexes are not stored: the engine is deterministic, so they are
 * recomputed from the chapter text when chunks are loaded. Blocking: call off the main thread.
 */
class ChapterChunkRepository(
    private val db: MnbDatabase,
    private val engine: ChunkingEngine = ChunkingEngine(),
) {
    private val chapterDao = db.chapterDao()
    private val chunkDao = db.translationChunkDao()

    /**
     * Chunks the chapter's current text. A stored chunk whose text is unchanged is left alone
     * (its translation and status are kept); changed chunks are replaced and chunks beyond the
     * new count are removed, all in one transaction. Running it twice gives [ChunkSaveResult.UNCHANGED].
     * Throws IllegalStateException when the chapter does not exist.
     */
    fun chunkChapter(chapterId: ChapterId): ChunkingOutcome = db.runInTransaction(
        Callable<ChunkingOutcome> {
            val chapter = requireChapter(chapterId)
            val fresh = engine.chunk(chapterId, chapter.originalText)
            val stored = chunkDao.listForChapter(chapterId.value).associateBy { it.chunkIndex }
            var written = false
            val result = ArrayList<ChapterChunk>(fresh.size)
            for (c in fresh) {
                val row = stored[c.chunkIndex]
                if (row != null && row.originalText == c.originalText) {
                    result.add(c.copy(chunk = row.toModel()))
                } else {
                    if (row != null) chunkDao.delete(row.chunkId)
                    chunkDao.upsert(c.chunk.toEntity(null))
                    result.add(c)
                    written = true
                }
            }
            for (row in stored.values) {
                if (row.chunkIndex >= fresh.size) {
                    chunkDao.delete(row.chunkId)
                    written = true
                }
            }
            val status = when {
                !written -> ChunkSaveResult.UNCHANGED
                stored.isEmpty() -> ChunkSaveResult.CREATED
                else -> ChunkSaveResult.UPDATED
            }
            ChunkingOutcome(status, result)
        },
    )

    /** Stored chunks in order, with paragraph indexes. Rows that no longer match the engine's output are left out. */
    fun loadChunks(chapterId: ChapterId): List<ChapterChunk> {
        val chapter = chapterDao.get(chapterId.value) ?: return emptyList()
        return align(chapter)
    }

    /** Compares the stored chunks with the chapter text; chunk again when the report is not valid. */
    fun verify(chapterId: ChapterId): ChunkIntegrityReport {
        val chapter = requireChapter(chapterId)
        return ChunkReassembler.check(chapter.originalText, align(chapter))
    }

    private fun requireChapter(chapterId: ChapterId): ChapterEntity =
        checkNotNull(chapterDao.get(chapterId.value)) { "Chapter ${chapterId.value} not found" }

    private fun align(chapter: ChapterEntity): List<ChapterChunk> {
        val id = ChapterId(chapter.chapterId)
        val expected = engine.chunk(id, chapter.originalText).associateBy { it.chunkIndex }
        return chunkDao.listForChapter(chapter.chapterId).mapNotNull { row ->
            val e = expected[row.chunkIndex]
            if (e != null && e.originalText == row.originalText) {
                ChapterChunk(row.toModel(), e.startParagraphIndex, e.endParagraphIndex)
            } else {
                null
            }
        }
    }
}
