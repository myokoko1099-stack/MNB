package com.mnb.browser.translation.quality

/** The kind of problem a check looks for. Used to report which areas are healthy. */
enum class QualityCategory { COMPLETENESS, STRUCTURE, TERMINOLOGY, MEANING, NATURALNESS }

/** One deterministic check. A check that cannot run for the given input is reported as skipped, never as passed. */
enum class QualityCheckType(val category: QualityCategory) {
    TRANSLATION_RESULT(QualityCategory.COMPLETENESS),
    EMPTY_TRANSLATION(QualityCategory.COMPLETENESS),
    PARAGRAPH_COUNT(QualityCategory.COMPLETENESS),
    EMPTY_PARAGRAPHS(QualityCategory.COMPLETENESS),
    CONTENT_LENGTH(QualityCategory.COMPLETENESS),
    UNTRANSLATED_TEXT(QualityCategory.COMPLETENESS),
    CHUNK_TRANSLATIONS(QualityCategory.COMPLETENESS),
    CHUNK_SEQUENCE(QualityCategory.STRUCTURE),
    PARAGRAPH_ORDER(QualityCategory.STRUCTURE),
    DUPLICATE_PARAGRAPHS(QualityCategory.STRUCTURE),
    GLOSSARY_TERMS(QualityCategory.TERMINOLOGY),
    CHARACTER_NAMES(QualityCategory.TERMINOLOGY),
    NUMBERS(QualityCategory.MEANING),
    EXTERNAL_EVALUATION(QualityCategory.MEANING),
    TARGET_SCRIPT(QualityCategory.NATURALNESS),
    TEXT_ARTIFACTS(QualityCategory.NATURALNESS),
}

enum class QualityIssueType(val check: QualityCheckType) {
    EMPTY_TRANSLATION(QualityCheckType.EMPTY_TRANSLATION),
    INVALID_TRANSLATION_RESULT(QualityCheckType.TRANSLATION_RESULT),
    MISSING_PARAGRAPHS(QualityCheckType.PARAGRAPH_COUNT),
    EXTRA_PARAGRAPHS(QualityCheckType.PARAGRAPH_COUNT),
    EMPTY_PARAGRAPH(QualityCheckType.EMPTY_PARAGRAPHS),
    POSSIBLE_MISSING_CONTENT(QualityCheckType.CONTENT_LENGTH),
    UNTRANSLATED_PARAGRAPH(QualityCheckType.UNTRANSLATED_TEXT),
    CHUNK_NOT_TRANSLATED(QualityCheckType.CHUNK_TRANSLATIONS),
    MISSING_CHUNK(QualityCheckType.CHUNK_SEQUENCE),
    DUPLICATE_CHUNK(QualityCheckType.CHUNK_SEQUENCE),
    INVALID_CHUNK_SEQUENCE(QualityCheckType.CHUNK_SEQUENCE),
    PARAGRAPH_ORDER_MISMATCH(QualityCheckType.PARAGRAPH_ORDER),
    DUPLICATE_PARAGRAPH(QualityCheckType.DUPLICATE_PARAGRAPHS),
    GLOSSARY_TERM_MISSING(QualityCheckType.GLOSSARY_TERMS),
    GLOSSARY_VARIANT_USED(QualityCheckType.GLOSSARY_TERMS),
    CHARACTER_NAME_INCONSISTENT(QualityCheckType.CHARACTER_NAMES),
    NUMBER_MISMATCH(QualityCheckType.NUMBERS),
    NO_MYANMAR_TEXT(QualityCheckType.TARGET_SCRIPT),
    SOURCE_SCRIPT_LEFTOVER(QualityCheckType.TARGET_SCRIPT),
    TEXT_ARTIFACT(QualityCheckType.TEXT_ARTIFACTS),
    EVALUATOR_FAILED(QualityCheckType.EXTERNAL_EVALUATION);

    val category: QualityCategory get() = check.category
}

/**
 * How serious an issue is. [weight] is what it subtracts from the score. CRITICAL and MAJOR are
 * reported as issues; MINOR and INFO are reported as warnings.
 */
enum class QualitySeverity(val weight: Int) {
    CRITICAL(60),
    MAJOR(15),
    MINOR(5),
    INFO(0);

    val isBlocking: Boolean get() = this == CRITICAL || this == MAJOR
}

/**
 * Where something is. Indexes are 0-based (human readable descriptions count from 1). Paragraph
 * indexes count non-blank paragraphs as [com.mnb.browser.translation.chunking.ParagraphSplitter] does.
 */
data class QualityLocation(
    val paragraphIndex: Int? = null,
    val chunkIndex: Int? = null,
    val excerpt: String? = null,
)

data class QualityIssue(
    val type: QualityIssueType,
    val severity: QualitySeverity,
    val description: String,
    val sourceLocation: QualityLocation?,
    val translationLocation: QualityLocation?,
    val suggestedAction: String,
) {
    init {
        require(description.isNotBlank()) { "description must not be blank" }
        require(suggestedAction.isNotBlank()) { "suggestedAction must not be blank" }
    }

    val category: QualityCategory get() = type.category
}

enum class CheckStatus {
    PASSED,
    WARNING,
    FAILED,

    /** No check of that area could run for this input, so nothing can be said about it. */
    NOT_EVALUATED,
}

/** Whether meaning was judged by something smarter than the deterministic checks. */
enum class SemanticStatus {
    /** The default: the checks here cannot tell whether the translation says the right thing. */
    NOT_EVALUATED,
    EVALUATED,
}

data class QualityResult(
    /** 0..100. A heuristic of structure and terminology, not a measure of translation accuracy. */
    val overallScore: Int,
    val completeness: CheckStatus,
    val structural: CheckStatus,
    val terminology: CheckStatus,
    val semantic: SemanticStatus,
    /** CRITICAL and MAJOR findings. */
    val issues: List<QualityIssue>,
    /** MINOR and INFO findings. */
    val warnings: List<QualityIssue>,
    /** Checks that ran and found nothing. */
    val passedChecks: List<QualityCheckType>,
    /** Checks that could not run for this input (for example chunk checks without chunks). */
    val skippedChecks: List<QualityCheckType>,
) {
    val allIssues: List<QualityIssue> get() = issues + warnings
    val hasBlockingIssues: Boolean get() = issues.isNotEmpty()
}
