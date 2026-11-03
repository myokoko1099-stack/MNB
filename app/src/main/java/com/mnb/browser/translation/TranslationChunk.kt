package com.mnb.browser.translation

/** One piece of a chapter, sized to fit a single [TranslationRequest]. */
data class TranslationChunk(
    val chunkId: ChunkId,
    val chapterId: ChapterId,
    val chunkIndex: Int,
    val originalText: String,
    val translatedText: String? = null,
    val status: TranslationStatus = TranslationStatus.PENDING,
) {
    init {
        require(chunkIndex >= 0) { "chunkIndex must not be negative" }
        require(originalText.isNotBlank()) { "originalText must not be blank" }
        require(status != TranslationStatus.COMPLETED || !translatedText.isNullOrBlank()) {
            "A COMPLETED chunk needs translatedText"
        }
    }

    /** Bridge to the Phase 04-I1 core; size and language rules are enforced by the request. */
    fun toRequest(sourceLanguage: SourceLanguage): TranslationRequest =
        TranslationRequest(text = originalText, sourceLanguage = sourceLanguage)
}
