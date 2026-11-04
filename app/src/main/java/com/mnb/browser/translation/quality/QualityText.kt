package com.mnb.browser.translation.quality

import com.mnb.browser.translation.memory.MemoryText

/** Small text helpers shared by the checks. They only read text. */
internal object QualityText {
    private val DIGITS = Regex("[0-9]+")

    fun excerpt(text: String, max: Int): String = if (text.length <= max) text else text.take(max) + "\u2026"

    /** Comparison key: Unicode NFC, lower case, collapsed spacing and Myanmar digits turned into ASCII digits. */
    fun key(text: String): String = asciiDigits(MemoryText.normalize(text))

    /** Myanmar digits (U+1040..U+1049) become 0-9 so numbers can be compared across scripts. */
    fun asciiDigits(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) sb.append(if (c in '\u1040'..'\u1049') '0' + (c - '\u1040') else c)
        return sb.toString()
    }

    fun digitRuns(text: String, minLength: Int): List<String> =
        DIGITS.findAll(asciiDigits(text)).map { it.value }.filter { it.length >= minLength }.distinct().toList()

    fun isMyanmar(c: Char): Boolean =
        c in '\u1000'..'\u109F' || c in '\uAA60'..'\uAA7F' || c in '\uA9E0'..'\uA9FF'

    fun isCjk(c: Char): Boolean =
        c in '\u4E00'..'\u9FFF' || c in '\u3400'..'\u4DBF' || c in '\u3040'..'\u30FF' || c in '\uAC00'..'\uD7AF'

    fun isAsciiLetter(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z'

    class ScriptStats(val myanmar: Int, val cjk: Int, val latin: Int) {
        private val total: Int get() = myanmar + cjk + latin
        val cjkShare: Double get() = if (total == 0) 0.0 else cjk.toDouble() / total
    }

    fun scriptStats(text: String): ScriptStats {
        var myanmar = 0
        var cjk = 0
        var latin = 0
        for (c in text) {
            when {
                isMyanmar(c) -> myanmar++
                isCjk(c) -> cjk++
                isAsciiLetter(c) -> latin++
            }
        }
        return ScriptStats(myanmar, cjk, latin)
    }
}

/** What the checks share: the input, the thresholds and ways to point at a paragraph. */
internal class CheckContext(val input: QualityInput, val config: QualityConfig) {
    val source: List<String> get() = input.sourceParagraphs
    val translated: List<String> get() = input.translatedParagraphs

    /** True when the source and the translation have the same number of paragraphs, so position comparison is meaningful. */
    val aligned: Boolean get() = source.size == translated.size

    fun srcLoc(index: Int): QualityLocation? =
        source.getOrNull(index)?.let { QualityLocation(paragraphIndex = index, excerpt = QualityText.excerpt(it, config.excerptLength)) }

    fun trLoc(index: Int): QualityLocation? =
        translated.getOrNull(index)?.let { QualityLocation(paragraphIndex = index, excerpt = QualityText.excerpt(it, config.excerptLength)) }
}
