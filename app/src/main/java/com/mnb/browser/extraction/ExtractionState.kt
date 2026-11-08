package com.mnb.browser.extraction

/** One extracted text block and its stable in-document anchor. Legacy preview-only results use index -1. */
data class ExtractedParagraph(
    val text: String,
    val headingLevel: Int = 0,
    val index: Int = -1,
    val textHash: String = "",
    val contentKind: String = if (headingLevel > 0) "heading" else "paragraph",
) {
    val isHeading: Boolean get() = headingLevel > 0
}

data class ExtractedContent(
    val pageTitle: String,
    val chapterTitle: String?,
    val url: String,
    val paragraphs: List<ExtractedParagraph>,
    val sessionToken: String = "",
    val documentToken: String = "",
    val chapterConfidence: Double = 0.0,
    val truncated: Boolean = false,
    val totalExtractedCharacters: Int = 0,
    val tabId: String = "",
    val navGeneration: Long = 0L,
    val contentType: String = "content",
) {
    /** All paragraphs in original order, separated by a blank line. */
    val mainText: String get() = paragraphs.joinToString("\n\n") { it.text }
}

/** What the extraction preview screen shows. */
sealed interface ExtractionState {
    data object Idle : ExtractionState
    data object Loading : ExtractionState
    data class Success(val content: ExtractedContent) : ExtractionState
    data class Empty(val url: String) : ExtractionState
    data class Error(val message: String) : ExtractionState
}
