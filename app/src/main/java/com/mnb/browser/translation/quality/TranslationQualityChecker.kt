package com.mnb.browser.translation.quality

/**
 * Checks a translation without a database, a provider or the network, and without changing the
 * source or the translation. Only deterministic checks run here; whether the translation says the
 * right thing is NOT judged ([QualityResult.semantic] stays NOT_EVALUATED unless a
 * [QualityEvaluator] ran). Never throws for a bad translation: problems come back as issues.
 *
 * Checks that cannot run for the given input (chunk checks without chunks, term checks without
 * glossary, paragraph checks on an empty translation) are listed in
 * [QualityResult.skippedChecks] instead of being counted as passed.
 */
class TranslationQualityChecker(
    private val config: QualityConfig = QualityConfig(),
    private val evaluators: List<QualityEvaluator> = emptyList(),
) {
    fun check(input: QualityInput): QualityResult {
        val ctx = CheckContext(input, config)
        val ran = LinkedHashMap<QualityCheckType, List<QualityIssue>>()

        fun record(type: QualityCheckType, issues: List<QualityIssue>?) {
            if (issues != null) ran[type] = issues
        }

        if (input.result != null) record(QualityCheckType.TRANSLATION_RESULT, CompletenessChecks.result(input))
        record(QualityCheckType.EMPTY_TRANSLATION, CompletenessChecks.emptyTranslation(ctx))

        if (input.chunks.isNotEmpty()) {
            record(QualityCheckType.CHUNK_SEQUENCE, StructureChecks.chunkSequence(ctx))
            record(QualityCheckType.CHUNK_TRANSLATIONS, CompletenessChecks.chunkTranslations(ctx))
        }

        // With no translated text the paragraph-level checks would only repeat the same problem.
        if (input.translatedParagraphs.any { it.isNotBlank() }) {
            record(QualityCheckType.PARAGRAPH_COUNT, CompletenessChecks.paragraphCount(ctx))
            record(QualityCheckType.EMPTY_PARAGRAPHS, CompletenessChecks.emptyParagraphs(ctx))
            record(QualityCheckType.CONTENT_LENGTH, CompletenessChecks.contentLength(ctx))
            record(QualityCheckType.UNTRANSLATED_TEXT, CompletenessChecks.untranslated(ctx))
            record(QualityCheckType.DUPLICATE_PARAGRAPHS, StructureChecks.duplicateParagraphs(ctx))
            record(QualityCheckType.PARAGRAPH_ORDER, StructureChecks.paragraphOrder(ctx))
            record(QualityCheckType.GLOSSARY_TERMS, TerminologyChecks.check(ctx, characters = false))
            record(QualityCheckType.CHARACTER_NAMES, TerminologyChecks.check(ctx, characters = true))
            record(QualityCheckType.NUMBERS, TextChecks.numbers(ctx))
            record(QualityCheckType.TARGET_SCRIPT, TextChecks.targetScript(ctx))
            record(QualityCheckType.TEXT_ARTIFACTS, TextChecks.artifacts(ctx))
        }

        var evaluated = false
        if (evaluators.isNotEmpty()) {
            val extra = ArrayList<QualityIssue>()
            for (evaluator in evaluators) {
                try {
                    extra += evaluator.evaluate(input)
                    evaluated = true
                } catch (e: Exception) {
                    extra += QualityIssue(
                        QualityIssueType.EVALUATOR_FAILED,
                        QualitySeverity.INFO,
                        "Evaluator ${evaluator.id} failed: ${e.message ?: e.javaClass.simpleName}",
                        null,
                        null,
                        "Fix or remove this evaluator; its judgement is not part of this result",
                    )
                }
            }
            ran[QualityCheckType.EXTERNAL_EVALUATION] = extra
        }

        val all = ran.values.flatten()
        val ranTypes = ran.keys
        return QualityResult(
            overallScore = QualityScoring.score(all),
            completeness = QualityScoring.status(QualityCategory.COMPLETENESS, ranTypes, all),
            structural = QualityScoring.status(QualityCategory.STRUCTURE, ranTypes, all),
            terminology = QualityScoring.status(QualityCategory.TERMINOLOGY, ranTypes, all),
            semantic = if (evaluated) SemanticStatus.EVALUATED else SemanticStatus.NOT_EVALUATED,
            issues = all.filter { it.severity.isBlocking },
            warnings = all.filterNot { it.severity.isBlocking },
            passedChecks = ran.filter { it.value.isEmpty() }.keys.toList(),
            skippedChecks = QualityCheckType.values().filter { it !in ranTypes },
        )
    }
}
