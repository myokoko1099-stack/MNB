package com.mnb.browser.translation.chunking

/**
 * Last-resort splitting of a paragraph that is longer than the chunk limit. Pieces are cut at
 * sentence ends; only a single sentence longer than the limit is cut further (at a comma or
 * space if one is near, otherwise at the limit). Pieces concatenate back to the paragraph exactly.
 */
internal object SentenceSplitter {
    private const val STRONG_TERMINATORS = "\u3002\uFF01\uFF1F!?\u2026\u2025\u104B"
    private const val CLOSERS = "\u201D\u2019\u300D\u300F)\uFF09]\u3011\u300B\u3009\"'"
    private const val SOFT_BREAKS = "\uFF0C,\u3001\uFF1B;\uFF1A:\uFF09)\u201D\u300D\u300F"

    fun split(paragraph: String, maxChars: Int): List<String> {
        require(maxChars > 0) { "maxChars must be positive" }
        if (paragraph.length <= maxChars) return listOf(paragraph)
        val pieces = ArrayList<String>()
        var pieceStart = 0
        var pieceEnd = 0
        forEachSentence(paragraph) { s, e ->
            if (e - s > maxChars) {
                if (pieceEnd > pieceStart) pieces.add(paragraph.substring(pieceStart, pieceEnd))
                hardSplit(paragraph, s, e, maxChars, pieces)
                pieceStart = e
                pieceEnd = e
            } else {
                if (e - pieceStart > maxChars) {
                    pieces.add(paragraph.substring(pieceStart, pieceEnd))
                    pieceStart = s
                }
                pieceEnd = e
            }
        }
        if (pieceEnd > pieceStart) pieces.add(paragraph.substring(pieceStart, pieceEnd))
        return pieces
    }

    private inline fun forEachSentence(text: String, action: (Int, Int) -> Unit) {
        val n = text.length
        var start = 0
        var i = 0
        while (i < n) {
            if (endsSentence(text, i)) {
                var j = i + 1
                while (j < n && (isTerminator(text[j]) || text[j] in CLOSERS)) j++
                while (j < n && ParagraphSplitter.isIgnorable(text[j])) j++
                action(start, j)
                start = j
                i = j
            } else {
                i++
            }
        }
        if (start < n) action(start, n)
    }

    private fun isTerminator(c: Char) = c == '.' || c in STRONG_TERMINATORS

    /** An ASCII '.' ends a sentence only before whitespace, a closing quote or the end (so 3.14 stays whole). */
    private fun endsSentence(text: String, i: Int): Boolean {
        val c = text[i]
        if (c == '.') {
            val next = text.getOrNull(i + 1)
            return next == null || next.isWhitespace() || next in CLOSERS
        }
        return c in STRONG_TERMINATORS
    }

    private fun hardSplit(text: String, start: Int, end: Int, maxChars: Int, out: MutableList<String>) {
        var pos = start
        while (pos < end) {
            if (out.isNotEmpty() && isBlankRange(text, pos, end)) {
                // Never emit a whitespace-only piece: keep the tail with the previous piece.
                out[out.lastIndex] = out[out.lastIndex] + text.substring(pos, end)
                return
            }
            val cut = if (end - pos <= maxChars) end else bestCut(text, pos, pos + maxChars)
            out.add(text.substring(pos, cut))
            pos = cut
        }
    }

    private fun bestCut(text: String, pos: Int, limit: Int): Int {
        val floor = pos + (limit - pos) / 2
        var k = limit
        while (k > floor) {
            val c = text[k - 1]
            if (ParagraphSplitter.isIgnorable(c) || c in SOFT_BREAKS) return k
            k--
        }
        return if (Character.isHighSurrogate(text[limit - 1]) && limit - 1 > pos) limit - 1 else limit
    }

    private fun isBlankRange(text: String, s: Int, e: Int): Boolean {
        for (k in s until e) if (!ParagraphSplitter.isIgnorable(text[k])) return false
        return true
    }
}
