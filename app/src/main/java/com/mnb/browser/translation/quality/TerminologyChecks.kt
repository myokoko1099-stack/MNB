package com.mnb.browser.translation.quality

import com.mnb.browser.translation.memory.CharacterMemory
import com.mnb.browser.translation.memory.ConflictStatus
import com.mnb.browser.translation.memory.GlossaryMemory
import com.mnb.browser.translation.memory.MemoryConflict
import com.mnb.browser.translation.memory.MemoryStatus
import com.mnb.browser.translation.memory.MemoryText
import com.mnb.browser.translation.memory.MemoryType

/**
 * One thing the translation is expected to contain whenever one of [sourceTerms] is in the source.
 * [confirmed] is false for UNCERTAIN memory, which is reported softly and never treated as settled.
 */
internal data class TermExpectation(
    val sourceTerms: List<String>,
    val expected: String,
    val confirmed: Boolean,
    val character: Boolean,
    val variants: List<String>,
) {
    fun appearsIn(normalizedSource: String): Boolean =
        sourceTerms.any { MemoryText.containsTerm(normalizedSource, MemoryText.normalize(it)) }
}

internal object TerminologyChecks {

    /** Characters with a Myanmar name first, then glossary terms not already covered by a character. */
    fun expectations(input: QualityInput): List<TermExpectation> {
        val out = ArrayList<TermExpectation>()
        for (c in input.characters) {
            val name = c.myanmarName ?: continue
            out += TermExpectation(
                sourceTerms = c.allNames,
                expected = name,
                confirmed = c.status == MemoryStatus.CONFIRMED,
                character = true,
                variants = variantsFor(input, c.allNames, name),
            )
        }
        val characterNames = out.map { QualityText.key(it.expected) }.toSet()
        for (g in input.glossary) {
            if (QualityText.key(g.translatedTerm) in characterNames) continue
            out += TermExpectation(
                sourceTerms = listOf(g.sourceTerm),
                expected = g.translatedTerm,
                confirmed = g.status == MemoryStatus.CONFIRMED,
                character = false,
                variants = variantsFor(input, listOf(g.sourceTerm), g.translatedTerm),
            )
        }
        return out
    }

    private fun variantsFor(input: QualityInput, sourceTerms: List<String>, expected: String): List<String> {
        val expectedKey = QualityText.key(expected)
        return sourceTerms
            .flatMap { input.termVariants[MemoryText.normalize(it)].orEmpty() }
            .filter { it.isNotBlank() && QualityText.key(it) != expectedKey }
            .distinct()
    }

    /**
     * Returns null when the input has no expectation of this kind (the check cannot run).
     * When the paragraph counts match every paragraph is checked against its own source
     * paragraph; otherwise the whole translation is searched, because positions cannot be trusted.
     */
    fun check(ctx: CheckContext, characters: Boolean): List<QualityIssue>? {
        val wanted = expectations(ctx.input).filter { it.character == characters }
        if (wanted.isEmpty()) return null

        val sourceKeys = ctx.source.map { MemoryText.normalize(it) }
        val translatedKeys = ctx.translated.map { QualityText.key(it) }
        val wholeTranslation = translatedKeys.joinToString("\n")
        val issues = ArrayList<QualityIssue>()

        for (e in wanted) {
            val expectedKey = QualityText.key(e.expected)
            if (ctx.aligned) {
                for (i in sourceKeys.indices) {
                    if (!e.appearsIn(sourceKeys[i]) || translatedKeys[i].contains(expectedKey)) continue
                    issues += issueFor(ctx, e, i, translatedKeys[i])
                }
            } else {
                val first = sourceKeys.indexOfFirst { e.appearsIn(it) }
                if (first >= 0 && !wholeTranslation.contains(expectedKey)) {
                    issues += issueFor(ctx, e, first, wholeTranslation)
                }
            }
        }
        return issues
    }

    private fun issueFor(ctx: CheckContext, e: TermExpectation, sourceIndex: Int, translatedScope: String): QualityIssue {
        val variantUsed = e.variants.firstOrNull { translatedScope.contains(QualityText.key(it)) }
        val severity = when {
            e.confirmed && variantUsed != null -> QualitySeverity.MAJOR
            e.confirmed -> QualitySeverity.MINOR
            variantUsed != null -> QualitySeverity.MINOR
            else -> QualitySeverity.INFO
        }
        val type = when {
            e.character -> QualityIssueType.CHARACTER_NAME_INCONSISTENT
            variantUsed != null -> QualityIssueType.GLOSSARY_VARIANT_USED
            else -> QualityIssueType.GLOSSARY_TERM_MISSING
        }
        val label = if (e.character) "Character name" else "Term"
        val term = e.sourceTerms.first()
        val status = if (e.confirmed) "confirmed" else "uncertain, not enforced"
        val description = buildString {
            append("$label '$term' should be '${e.expected}' ($status) but ")
            if (variantUsed != null) append("'$variantUsed' is used instead") else append("that translation was not found")
            if (ctx.aligned) append(" in translated paragraph #${sourceIndex + 1}") else append(" in the translation")
        }
        val action = if (e.confirmed) {
            "Use '${e.expected}' for '$term'"
        } else {
            "Confirm '${e.expected}' in memory before relying on it"
        }
        return QualityIssue(
            type,
            severity,
            description,
            ctx.srcLoc(sourceIndex),
            if (ctx.aligned) ctx.trLoc(sourceIndex) else null,
            action,
        )
    }
}

/**
 * Builds [QualityInput.termVariants] from stored memory conflicts: a rejected or still open
 * proposal, or the value an accepted proposal replaced, is a rendering the novel does not use.
 */
object QualityVariants {
    fun fromConflicts(
        glossary: List<GlossaryMemory>,
        characters: List<CharacterMemory>,
        conflicts: List<MemoryConflict>,
    ): Map<String, List<String>> {
        val out = LinkedHashMap<String, MutableList<String>>()
        for (c in conflicts) {
            val variant = if (c.status == ConflictStatus.ACCEPTED) c.existingValue else c.proposedValue
            val terms: List<String> = when (c.type) {
                MemoryType.GLOSSARY ->
                    if (c.fieldName == "translatedTerm") glossary.filter { it.id == c.memoryId }.map { it.sourceTerm } else emptyList()
                MemoryType.CHARACTER ->
                    if (c.fieldName == "myanmarName") characters.filter { it.id == c.memoryId }.flatMap { it.allNames } else emptyList()
                else -> emptyList()
            }
            for (term in terms) {
                val list = out.getOrPut(MemoryText.normalize(term)) { ArrayList() }
                if (variant !in list) list += variant
            }
        }
        return out
    }
}
