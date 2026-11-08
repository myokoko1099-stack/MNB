package com.mnb.browser.reader.translation

/**
 * Reads a chapter number from a chapter title, only when it is unambiguous (Phase 04-I10-C1).
 * Accepted: "Chapter 12", "第12章", "제12화", "第12話" (ASCII digits, at the very start of the title).
 * Anything else returns null: other wording, Chinese numerals, ranges ("Chapter 12-13"), decimals
 * ("Chapter 12.5"), suffixes ("Chapter 12a"), a prefix before the pattern ("Vol 2 Chapter 12"),
 * or several chapter patterns with different numbers (the same number repeated,
 * e.g. "Chapter 1: Chapter 1 - Title", is accepted).
 */
object ChapterTitleParser {
    private const val NOT_RANGE = """(?!\s*[-–—~&,/]\s*\d)"""
    private val patterns = listOf(
        Regex("""(?i)(?<![\p{L}\p{N}])chapter\s*(\d{1,6})(?![\p{L}\p{N}]|[.,]\d)$NOT_RANGE"""),
        Regex("""第\s*(\d{1,6})\s*章$NOT_RANGE"""),
        Regex("""제\s*(\d{1,6})\s*화$NOT_RANGE"""),
        Regex("""第\s*(\d{1,6})\s*話$NOT_RANGE"""),
    )

    fun parse(title: String?): Int? {
        val text = title?.trim().orEmpty()
        if (text.isEmpty()) return null
        val matches = patterns.flatMap { it.findAll(text).toList() }
        if (matches.isEmpty()) return null
        val numbers = matches.map { it.groupValues[1].toIntOrNull() }.toSet()
        if (numbers.size != 1) return null
        if (matches.minOf { it.range.first } != 0) return null
        return numbers.single()
    }
}
