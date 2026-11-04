package com.mnb.browser.translation.quality

import com.mnb.browser.translation.TranslationResult
import com.mnb.browser.translation.memory.MemoryText

/** Is everything there? Each function returns the issues it found (empty = the check passed). */
internal object CompletenessChecks {

    fun result(input: QualityInput): List<QualityIssue> {
        val r = input.result ?: return emptyList()
        return when (r) {
            is TranslationResult.Failure -> listOf(
                QualityIssue(
                    QualityIssueType.INVALID_TRANSLATION_RESULT,
                    QualitySeverity.CRITICAL,
                    "Provider ${r.providerId} reported a failure (${r.error})" + (r.message?.let { ": $it" } ?: ""),
                    null,
                    null,
                    "Run the translation again; do not use this result",
                ),
            )
            is TranslationResult.Success -> when {
                r.translatedText.isBlank() -> listOf(
                    QualityIssue(
                        QualityIssueType.INVALID_TRANSLATION_RESULT,
                        QualitySeverity.CRITICAL,
                        "Provider ${r.providerId} returned a successful result with blank text",
                        null,
                        null,
                        "Run the translation again; do not use this result",
                    ),
                )
                r.isMock -> listOf(
                    QualityIssue(
                        QualityIssueType.INVALID_TRANSLATION_RESULT,
                        QualitySeverity.MAJOR,
                        "Provider ${r.providerId} returned placeholder (mock) output, not a real translation",
                        null,
                        null,
                        "Translate with a real provider before showing this text as a translation",
                    ),
                )
                else -> emptyList()
            }
        }
    }

    fun emptyTranslation(ctx: CheckContext): List<QualityIssue> =
        if (ctx.translated.none { it.isNotBlank() }) {
            listOf(
                QualityIssue(
                    QualityIssueType.EMPTY_TRANSLATION,
                    QualitySeverity.CRITICAL,
                    "The translation contains no text",
                    ctx.srcLoc(0),
                    null,
                    "Translate the chapter again",
                ),
            )
        } else {
            emptyList()
        }

    fun paragraphCount(ctx: CheckContext): List<QualityIssue> {
        val s = ctx.source.size
        val t = ctx.translated.size
        if (s == t) return emptyList()
        if (t < s) {
            val severity = if (t.toDouble() / s < ctx.config.criticalMissingParagraphShare) {
                QualitySeverity.CRITICAL
            } else {
                QualitySeverity.MAJOR
            }
            return listOf(
                QualityIssue(
                    QualityIssueType.MISSING_PARAGRAPHS,
                    severity,
                    "The source has $s paragraphs but the translation has $t (${s - t} missing). " +
                        "Paragraphs are compared by position, so the missing ones may be anywhere",
                    ctx.srcLoc(t),
                    if (t > 0) ctx.trLoc(t - 1) else null,
                    "Find the untranslated part and translate it, or translate the chapter again",
                ),
            )
        }
        return listOf(
            QualityIssue(
                QualityIssueType.EXTRA_PARAGRAPHS,
                QualitySeverity.MINOR,
                "The translation has $t paragraphs but the source has only $s",
                ctx.srcLoc(s - 1),
                ctx.trLoc(s),
                "Check whether paragraphs were split or text was added",
            ),
        )
    }

    fun emptyParagraphs(ctx: CheckContext): List<QualityIssue> =
        ctx.translated.mapIndexedNotNull { i, p ->
            if (p.isNotBlank()) {
                null
            } else {
                QualityIssue(
                    QualityIssueType.EMPTY_PARAGRAPH,
                    QualitySeverity.MAJOR,
                    "Translated paragraph #${i + 1} is empty",
                    ctx.srcLoc(i),
                    ctx.trLoc(i),
                    "Translate this paragraph again",
                )
            }
        }

    fun contentLength(ctx: CheckContext): List<QualityIssue> {
        val issues = ArrayList<QualityIssue>()
        val srcChars = ctx.source.sumOf { it.length }
        val trChars = ctx.translated.sumOf { it.length }
        if (srcChars > 0 && trChars.toDouble() / srcChars < ctx.config.minChapterLengthRatio) {
            issues += QualityIssue(
                QualityIssueType.POSSIBLE_MISSING_CONTENT,
                QualitySeverity.MAJOR,
                "The translation is very short compared with the source ($trChars vs $srcChars characters); content may be missing",
                null,
                null,
                "Compare the translation with the source",
            )
        }
        if (ctx.aligned) {
            for (i in ctx.source.indices) {
                val s = ctx.source[i]
                val t = ctx.translated[i]
                if (t.isNotBlank() && s.length >= ctx.config.minLengthForRatioCheck &&
                    t.length.toDouble() / s.length < ctx.config.minParagraphLengthRatio
                ) {
                    issues += QualityIssue(
                        QualityIssueType.POSSIBLE_MISSING_CONTENT,
                        QualitySeverity.MINOR,
                        "Translated paragraph #${i + 1} is much shorter than its source; part of it may be missing",
                        ctx.srcLoc(i),
                        ctx.trLoc(i),
                        "Compare this paragraph with the source",
                    )
                }
            }
        }
        return issues
    }

    /** A translated paragraph identical to a source paragraph was most likely never translated. */
    fun untranslated(ctx: CheckContext): List<QualityIssue> {
        val sourceIndex = HashMap<String, Int>()
        ctx.source.forEachIndexed { i, p -> sourceIndex.putIfAbsent(MemoryText.normalize(p), i) }
        val issues = ArrayList<QualityIssue>()
        ctx.translated.forEachIndexed { i, p ->
            val key = MemoryText.normalize(p)
            if (key.length >= ctx.config.minUntranslatedLength && key.any { it.isLetter() }) {
                val si = sourceIndex[key]
                if (si != null) {
                    issues += QualityIssue(
                        QualityIssueType.UNTRANSLATED_PARAGRAPH,
                        QualitySeverity.MAJOR,
                        "Translated paragraph #${i + 1} is identical to source paragraph #${si + 1}; it looks untranslated",
                        ctx.srcLoc(si),
                        ctx.trLoc(i),
                        "Translate this paragraph",
                    )
                }
            }
        }
        return issues
    }

    fun chunkTranslations(ctx: CheckContext): List<QualityIssue> =
        ctx.input.chunks.sortedBy { it.chunkIndex }.mapNotNull { c ->
            if (!c.chunk.translatedText.isNullOrBlank()) {
                null
            } else {
                QualityIssue(
                    QualityIssueType.CHUNK_NOT_TRANSLATED,
                    QualitySeverity.MAJOR,
                    "Chunk #${c.chunkIndex} has no translation (status ${c.chunkStatus.name})",
                    QualityLocation(
                        paragraphIndex = c.startParagraphIndex,
                        chunkIndex = c.chunkIndex,
                        excerpt = QualityText.excerpt(c.originalText, ctx.config.excerptLength),
                    ),
                    QualityLocation(chunkIndex = c.chunkIndex),
                    "Translate this chunk again",
                )
            }
        }
}
