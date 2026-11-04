package com.mnb.browser.translation.quality

/** Thresholds of the deterministic checks. The defaults are deliberately lenient: they flag clear problems only. */
data class QualityConfig(
    /** A translated paragraph shorter than this share of its source (and source at least [minLengthForRatioCheck] long) is suspicious. */
    val minParagraphLengthRatio: Double = 0.25,
    val minChapterLengthRatio: Double = 0.30,
    val minLengthForRatioCheck: Int = 30,
    /** When fewer than this share of the source paragraphs have a translated counterpart the issue is CRITICAL. */
    val criticalMissingParagraphShare: Double = 0.5,
    val minDuplicateLength: Int = 10,
    val minUntranslatedLength: Int = 10,
    val minNumberDigits: Int = 2,
    /** Set to false when the target language is not Myanmar. */
    val expectMyanmarScript: Boolean = true,
    val maxSourceScriptShare: Double = 0.3,
    val minLatinLettersForScriptCheck: Int = 8,
    /** How many paragraphs either side the order check looks at. */
    val orderWindow: Int = 200,
    val excerptLength: Int = 60,
) {
    init {
        require(minParagraphLengthRatio in 0.0..1.0) { "minParagraphLengthRatio must be within 0..1" }
        require(minChapterLengthRatio in 0.0..1.0) { "minChapterLengthRatio must be within 0..1" }
        require(criticalMissingParagraphShare in 0.0..1.0) { "criticalMissingParagraphShare must be within 0..1" }
        require(maxSourceScriptShare in 0.0..1.0) { "maxSourceScriptShare must be within 0..1" }
        require(minLengthForRatioCheck >= 0 && minDuplicateLength >= 1 && minUntranslatedLength >= 1) {
            "length thresholds must be positive"
        }
        require(minNumberDigits >= 1 && minLatinLettersForScriptCheck >= 1) { "thresholds must be positive" }
        require(orderWindow >= 1) { "orderWindow must be at least 1" }
        require(excerptLength >= 1) { "excerptLength must be at least 1" }
    }
}
