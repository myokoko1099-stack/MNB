package com.mnb.browser.translation

/**
 * One unit of text to translate. Later phases (chunking, context memory) build requests per
 * chunk, so [MAX_TEXT_LENGTH] is a per-request limit, not a per-chapter one.
 */
data class TranslationRequest(
    val text: String,
    val sourceLanguage: SourceLanguage,
    val targetLanguage: TargetLanguage = TargetLanguage.MYANMAR,
) {
    /** Null when the request is valid, otherwise the reason it is not. */
    fun validate(): TranslationError? = when {
        text.isBlank() -> TranslationError.EMPTY_TEXT
        text.length > MAX_TEXT_LENGTH -> TranslationError.TEXT_TOO_LONG
        else -> null
    }

    companion object {
        const val MAX_TEXT_LENGTH = 20_000
    }
}
