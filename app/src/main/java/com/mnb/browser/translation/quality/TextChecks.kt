package com.mnb.browser.translation.quality

import com.mnb.browser.translation.memory.MemoryText

/**
 * Meaning and naturalness checks that can be done without understanding the text: numbers kept,
 * the right script used, obvious artifacts. They never judge grammar or word choice and never
 * change the text.
 */
internal object TextChecks {

    /** Numbers in the source should also appear in the translation. Only meaningful when paragraphs line up. */
    fun numbers(ctx: CheckContext): List<QualityIssue>? {
        if (!ctx.aligned) return null
        val issues = ArrayList<QualityIssue>()
        for (i in ctx.source.indices) {
            val translatedDigits = QualityText.asciiDigits(ctx.translated[i])
            val missing = QualityText.digitRuns(ctx.source[i], ctx.config.minNumberDigits)
                .filter { !translatedDigits.contains(it) }
            if (missing.isNotEmpty()) {
                issues += QualityIssue(
                    QualityIssueType.NUMBER_MISMATCH,
                    QualitySeverity.INFO,
                    "Number ${missing.joinToString(", ")} of source paragraph #${i + 1} was not found in the translation " +
                        "(it may have been written in words)",
                    ctx.srcLoc(i),
                    ctx.trLoc(i),
                    "Check the number by hand",
                )
            }
        }
        return issues
    }

    /** Flags translated paragraphs that are not in Myanmar script. Returns null when the target is not Myanmar. */
    fun targetScript(ctx: CheckContext): List<QualityIssue>? {
        if (!ctx.config.expectMyanmarScript) return null
        val sourceKeys = ctx.source.map { MemoryText.normalize(it) }.toSet()
        val issues = ArrayList<QualityIssue>()
        ctx.translated.forEachIndexed { i, p ->
            if (p.isBlank() || MemoryText.normalize(p) in sourceKeys) return@forEachIndexed // reported elsewhere
            val stats = QualityText.scriptStats(p)
            if (stats.cjk > 0 && stats.cjkShare > ctx.config.maxSourceScriptShare) {
                issues += QualityIssue(
                    QualityIssueType.SOURCE_SCRIPT_LEFTOVER,
                    QualitySeverity.MINOR,
                    "Translated paragraph #${i + 1} still contains a lot of source-language text",
                    ctx.srcLoc(i),
                    ctx.trLoc(i),
                    "Translate the remaining source-language text",
                )
            } else if (stats.myanmar == 0 && stats.latin >= ctx.config.minLatinLettersForScriptCheck) {
                issues += QualityIssue(
                    QualityIssueType.NO_MYANMAR_TEXT,
                    QualitySeverity.MINOR,
                    "Translated paragraph #${i + 1} contains no Myanmar text",
                    ctx.srcLoc(i),
                    ctx.trLoc(i),
                    "Check whether this paragraph was translated",
                )
            }
        }
        return issues
    }

    /** Obvious formatting damage only: broken characters, control characters, code fences, doubled sentence marks. */
    fun artifacts(ctx: CheckContext): List<QualityIssue> {
        val issues = ArrayList<QualityIssue>()
        ctx.translated.forEachIndexed { i, p ->
            fun add(severity: QualitySeverity, what: String, action: String) {
                issues += QualityIssue(
                    QualityIssueType.TEXT_ARTIFACT,
                    severity,
                    "Translated paragraph #${i + 1} $what",
                    ctx.srcLoc(i),
                    ctx.trLoc(i),
                    action,
                )
            }
            if (p.contains('\uFFFD')) add(QualitySeverity.MAJOR, "contains broken (replacement) characters", "Re-translate or fix the encoding")
            if (p.any { it.isISOControl() && it != '\t' }) add(QualitySeverity.MINOR, "contains control characters", "Remove the control characters by hand")
            if (p.contains("```")) add(QualitySeverity.MINOR, "contains a code fence left by the provider", "Remove the markup by hand")
            if (p.contains("\u104B\u104B")) add(QualitySeverity.INFO, "contains a doubled sentence-ending mark", "Check the punctuation by hand")
        }
        return issues
    }
}
