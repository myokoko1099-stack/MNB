package com.mnb.browser.extraction

/** One block of extracted text. headingLevel is 0 for body text, 1..6 for headings. */
data class ExtractedParagraph(val text: String, val headingLevel: Int = 0) {
    val isHeading: Boolean get() = headingLevel > 0
}

data class ExtractedContent(
    val pageTitle: String,
    val chapterTitle: String?,
    val url: String,
    val paragraphs: List<ExtractedParagraph>,
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
