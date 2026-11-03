package com.mnb.browser.translation.chunking

import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.ChunkId
import com.mnb.browser.translation.TranslationChunk

/**
 * Splits chapter text into chunks for translation. Pure and deterministic: the same text and
 * [config] always give the same chunks. Does not touch the database.
 *
 * 1. Paragraphs come from [ParagraphSplitter]. A paragraph that fits the limit is never split;
 *    a longer one is split at sentence ends ([SentenceSplitter]).
 * 2. Pieces are packed greedily, in order, into chunks of at most [ChunkingConfig.maxChunkChars].
 * 3. When a chunk fills up in the middle of a run of dialogue paragraphs, the cut moves back to
 *    the start of that run (if the remaining chunk is still long enough), so an exchange stays whole.
 * 4. A too-short last chunk is merged into the previous one, or paragraphs are moved to it.
 */
class ChunkingEngine(val config: ChunkingConfig = ChunkingConfig()) {

    private class Piece(
        val paragraphIndex: Int,
        val text: String,
        val startsParagraph: Boolean,
        val dialogue: Boolean,
    ) {
        val length: Int get() = text.length
    }

    fun chunk(chapterId: ChapterId, text: String): List<ChapterChunk> {
        val paragraphs = ParagraphSplitter.split(text)
        if (paragraphs.isEmpty()) return emptyList()
        val pieces = toPieces(paragraphs)
        val groups = pack(pieces)
        balanceShortTail(groups, pieces)
        return groups.mapIndexed { index, g -> build(chapterId, index, pieces, g[0], g[1]) }
    }

    private fun toPieces(paragraphs: List<String>): List<Piece> {
        val out = ArrayList<Piece>(paragraphs.size)
        paragraphs.forEachIndexed { i, p ->
            val dialogue = ParagraphSplitter.isDialogue(p)
            if (p.length <= config.maxChunkChars) {
                out.add(Piece(i, p, true, dialogue))
            } else {
                SentenceSplitter.split(p, config.maxChunkChars)
                    .forEachIndexed { k, s -> out.add(Piece(i, s, k == 0, dialogue)) }
            }
        }
        return out
    }

    /** Groups as [from, to) piece ranges. */
    private fun pack(pieces: List<Piece>): MutableList<IntArray> {
        val groups = ArrayList<IntArray>()
        var from = 0
        while (from < pieces.size) {
            var to = from
            var size = 0
            while (to < pieces.size) {
                val add = pieces[to].length + if (to > from && pieces[to].startsParagraph) 1 else 0
                if (size + add > config.maxChunkChars) break
                size += add
                to++
            }
            if (to == from) to = from + 1
            if (to < pieces.size) to = keepDialogueTogether(pieces, from, to)
            groups.add(intArrayOf(from, to))
            from = to
        }
        return groups
    }

    private fun keepDialogueTogether(pieces: List<Piece>, from: Int, cut: Int): Int {
        if (cut - from < 2) return cut
        val last = pieces[cut - 1]
        val next = pieces[cut]
        if (!(last.dialogue && next.dialogue && next.startsParagraph)) return cut
        var j = cut - 1
        while (j > from && pieces[j - 1].dialogue) j--
        return if (j > from && sizeOf(pieces, from, j) >= config.minChunkChars) j else cut
    }

    private fun balanceShortTail(groups: MutableList<IntArray>, pieces: List<Piece>) {
        if (groups.size < 2) return
        val lastIdx = groups.size - 1
        val last = groups[lastIdx]
        val prev = groups[lastIdx - 1]
        if (sizeOf(pieces, last[0], last[1]) >= config.minChunkChars) return
        if (sizeOf(pieces, prev[0], last[1]) <= config.maxChunkChars) {
            groups.removeAt(lastIdx)
            groups[lastIdx - 1] = intArrayOf(prev[0], last[1])
            return
        }
        var cut = prev[1] - 1
        while (cut > prev[0]) {
            val insideDialogue = pieces[cut].dialogue && pieces[cut - 1].dialogue
            if (pieces[cut].startsParagraph && !insideDialogue) {
                val tail = sizeOf(pieces, cut, last[1])
                if (tail > config.maxChunkChars) return
                if (tail >= config.minChunkChars) {
                    if (sizeOf(pieces, prev[0], cut) >= config.minChunkChars) {
                        prev[1] = cut
                        last[0] = cut
                    }
                    return
                }
            }
            cut--
        }
    }

    private fun sizeOf(pieces: List<Piece>, from: Int, to: Int): Int {
        var size = 0
        for (k in from until to) {
            size += pieces[k].length
            if (k > from && pieces[k].startsParagraph) size += ParagraphSplitter.SEPARATOR.length
        }
        return size
    }

    private fun build(chapterId: ChapterId, index: Int, pieces: List<Piece>, from: Int, to: Int): ChapterChunk {
        val text = if (to - from == 1) {
            pieces[from].text
        } else {
            val sb = StringBuilder(sizeOf(pieces, from, to))
            for (k in from until to) {
                if (k > from && pieces[k].startsParagraph) sb.append(ParagraphSplitter.SEPARATOR)
                sb.append(pieces[k].text)
            }
            sb.toString()
        }
        return ChapterChunk(
            chunk = TranslationChunk(chunkIdFor(chapterId, index), chapterId, index, text),
            startParagraphIndex = pieces[from].paragraphIndex,
            endParagraphIndex = pieces[to - 1].paragraphIndex,
        )
    }

    companion object {
        /** Stable id, so chunking the same chapter again produces the same ids. */
        fun chunkIdFor(chapterId: ChapterId, index: Int) = ChunkId("${chapterId.value}-chunk-$index")
    }
}
