package com.mnb.browser.translation.chunking

/**
 * Splits chapter text into paragraphs: one paragraph per non-blank line. Blank lines are dropped
 * and each paragraph is trimmed at both ends (including full-width spaces); text inside a
 * paragraph is never changed. Paragraphs are rejoined with [SEPARATOR].
 */
object ParagraphSplitter {
    const val SEPARATOR = "\n"

    private const val DIALOGUE_OPENERS = "\"\u201C\u300C\u300E\u2018"

    fun split(text: String): List<String> {
        val out = ArrayList<String>()
        val n = text.length
        var lineStart = 0
        var i = 0
        while (i <= n) {
            if (i == n || isLineBreak(text[i])) {
                addTrimmed(text, lineStart, i, out)
                if (i < n - 1 && text[i] == '\r' && text[i + 1] == '\n') i++
                lineStart = i + 1
            }
            i++
        }
        return out
    }

    /** The text as the chunks must reproduce it: trimmed non-blank paragraphs joined by [SEPARATOR]. */
    fun normalize(text: String): String = split(text).joinToString(SEPARATOR)

    fun isDialogue(paragraph: String): Boolean = paragraph.isNotEmpty() && paragraph[0] in DIALOGUE_OPENERS

    internal fun isIgnorable(c: Char): Boolean = c.isWhitespace() || c == '\uFEFF' || c == '\u200B'

    private fun isLineBreak(c: Char) = c == '\n' || c == '\r' || c == '\u2028' || c == '\u2029'

    private fun addTrimmed(text: String, start: Int, end: Int, out: MutableList<String>) {
        var s = start
        var e = end
        while (s < e && isIgnorable(text[s])) s++
        while (e > s && isIgnorable(text[e - 1])) e--
        if (s < e) out.add(text.substring(s, e))
    }
}
