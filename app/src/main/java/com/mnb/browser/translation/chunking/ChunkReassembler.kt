package com.mnb.browser.translation.chunking

/** What [ChunkReassembler.check] found. [uncoveredParagraphs] catches a missing first or last chunk too. */
data class ChunkIntegrityReport(
    val missingIndices: List<Int>,
    val duplicateIndices: List<Int>,
    val duplicateChunkIds: List<String>,
    val uncoveredParagraphs: List<Int>,
    val textMatches: Boolean,
) {
    val isValid: Boolean
        get() = missingIndices.isEmpty() && duplicateIndices.isEmpty() && duplicateChunkIds.isEmpty() &&
            uncoveredParagraphs.isEmpty() && textMatches
}

object ChunkReassembler {
    /** Chunks in chunkIndex order, keeping the first of any duplicate index. */
    internal fun ordered(chunks: List<ChapterChunk>): List<ChapterChunk> =
        chunks.sortedBy { it.chunkIndex }.distinctBy { it.chunkIndex }

    /**
     * Joins the chunks back into the chapter text: a paragraph break between chunks, nothing
     * between two pieces of the same split paragraph. The result equals [ParagraphSplitter.normalize]
     * of the original text.
     */
    fun reassemble(chunks: List<ChapterChunk>): String {
        val sb = StringBuilder()
        var prev: ChapterChunk? = null
        for (c in ordered(chunks)) {
            if (prev != null && prev.endParagraphIndex != c.startParagraphIndex) sb.append(ParagraphSplitter.SEPARATOR)
            sb.append(c.originalText)
            prev = c
        }
        return sb.toString()
    }

    fun check(originalText: String, chunks: List<ChapterChunk>): ChunkIntegrityReport {
        val paragraphs = ParagraphSplitter.split(originalText)
        val ordered = ordered(chunks)

        val duplicateIndices = chunks.groupingBy { it.chunkIndex }.eachCount()
            .filterValues { it > 1 }.keys.sorted()
        val duplicateIds = chunks.groupingBy { it.chunkId.value }.eachCount()
            .filterValues { it > 1 }.keys.sorted()

        val missing = if (ordered.isEmpty()) {
            emptyList()
        } else {
            val present = ordered.map { it.chunkIndex }.toHashSet()
            (0..ordered.last().chunkIndex).filter { it !in present }
        }

        val covered = BooleanArray(paragraphs.size)
        for (c in ordered) {
            for (p in c.startParagraphIndex..minOf(c.endParagraphIndex, paragraphs.size - 1)) covered[p] = true
        }
        val uncovered = covered.indices.filter { !covered[it] }

        return ChunkIntegrityReport(
            missingIndices = missing,
            duplicateIndices = duplicateIndices,
            duplicateChunkIds = duplicateIds,
            uncoveredParagraphs = uncovered,
            textMatches = reassemble(ordered) == paragraphs.joinToString(ParagraphSplitter.SEPARATOR),
        )
    }
}
