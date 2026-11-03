package com.mnb.browser.translation

/**
 * Translation state of one chapter. [contentHash] is the hash of the original text that was
 * translated; use [matchesContent] to see whether a re-extracted chapter changed.
 */
data class ChapterTranslation(
    val chapterId: ChapterId,
    val novelId: NovelId,
    val chapterNumber: Int,
    val originalText: String,
    val updatedAtMillis: Long,
    val translatedText: String? = null,
    val status: TranslationStatus = TranslationStatus.PENDING,
    val contentHash: ContentHash = ContentHash.of(originalText),
) {
    init {
        require(chapterNumber >= 0) { "chapterNumber must not be negative" }
        require(originalText.isNotBlank()) { "originalText must not be blank" }
        require(updatedAtMillis >= 0) { "updatedAtMillis must not be negative" }
        require(status != TranslationStatus.COMPLETED || !translatedText.isNullOrBlank()) {
            "A COMPLETED chapter needs translatedText"
        }
    }

    fun matchesContent(currentText: String): Boolean = contentHash == ContentHash.of(currentText)
}
