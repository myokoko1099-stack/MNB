package com.mnb.browser.translation.memory

import java.text.Normalizer
import java.util.Locale

/** Name/term matching helpers shared by the repository and the retriever. */
internal object MemoryText {
    private val WHITESPACE = Regex("\\s+")
    private val LATIN_TERM = Regex("[a-z0-9 '.\\-]+")

    /** Unicode NFC, trimmed, inner whitespace collapsed, lower case. Used as the uniqueness key. */
    fun normalize(text: String): String =
        WHITESPACE.replace(Normalizer.normalize(text, Normalizer.Form.NFC).trim(), " ").lowercase(Locale.ROOT)

    /**
     * True when [normalizedTerm] occurs in [normalizedText]. Latin terms must stand alone (so "Al"
     * does not match inside "also"); other scripts (Myanmar, Chinese, Japanese, Korean) have no
     * spaces between words, so a plain substring match is used.
     */
    fun containsTerm(normalizedText: String, normalizedTerm: String): Boolean {
        if (normalizedTerm.isEmpty() || normalizedText.isEmpty()) return false
        if (!LATIN_TERM.matches(normalizedTerm)) return normalizedText.contains(normalizedTerm)
        var from = 0
        while (true) {
            val index = normalizedText.indexOf(normalizedTerm, from)
            if (index < 0) return false
            val end = index + normalizedTerm.length
            val beforeOk = index == 0 || !normalizedText[index - 1].isLetterOrDigit()
            val afterOk = end >= normalizedText.length || !normalizedText[end].isLetterOrDigit()
            if (beforeOk && afterOk) return true
            from = index + 1
        }
    }
}

internal inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
    enumValues<E>().firstOrNull { it.name == name }
