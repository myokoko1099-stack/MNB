package com.mnb.browser.translation.chunking

import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.ChunkId
import com.mnb.browser.translation.TranslationChunk
import com.mnb.browser.translation.TranslationStatus

/**
 * A [TranslationChunk] plus where it sits in the chapter. Paragraph indexes count the chapter's
 * non-blank paragraphs from 0 (see [ParagraphSplitter]). When one long paragraph is split over
 * several chunks, neighbouring chunks share that paragraph index.
 */
data class ChapterChunk(
    val chunk: TranslationChunk,
    val startParagraphIndex: Int,
    val endParagraphIndex: Int,
) {
    init {
        require(startParagraphIndex >= 0) { "startParagraphIndex must not be negative" }
        require(endParagraphIndex >= startParagraphIndex) { "endParagraphIndex must not be before startParagraphIndex" }
    }

    val chunkId: ChunkId get() = chunk.chunkId
    val chapterId: ChapterId get() = chunk.chapterId
    val chunkIndex: Int get() = chunk.chunkIndex
    val originalText: String get() = chunk.originalText
    val characterCount: Int get() = chunk.originalText.length
    val chunkStatus: TranslationStatus get() = chunk.status
}

/** Text just before and after a chunk, for a later translation step that wants surrounding context. */
data class ChunkNeighborContext(val previousText: String?, val nextText: String?)

object ChunkNeighbors {
    const val DEFAULT_CONTEXT_CHARS = 300

    /** The tail of the previous chunk and the head of the next one (null at the chapter edges). */
    fun around(
        chunks: List<ChapterChunk>,
        chunkIndex: Int,
        maxChars: Int = DEFAULT_CONTEXT_CHARS,
    ): ChunkNeighborContext {
        require(maxChars > 0) { "maxChars must be positive" }
        val ordered = ChunkReassembler.ordered(chunks)
        val pos = ordered.indexOfFirst { it.chunkIndex == chunkIndex }
        require(pos >= 0) { "No chunk with index $chunkIndex" }
        return ChunkNeighborContext(
            previousText = ordered.getOrNull(pos - 1)?.originalText?.takeLast(maxChars),
            nextText = ordered.getOrNull(pos + 1)?.originalText?.take(maxChars),
        )
    }
}
