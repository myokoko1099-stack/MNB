package com.mnb.browser.translation

/** Outcome of a translation attempt. Never thrown; always returned. */
sealed interface TranslationResult {
    val providerId: String

    data class Success(
        val translatedText: String,
        override val providerId: String,
        /** True when the text is placeholder output and must not be shown as real translation. */
        val isMock: Boolean,
        val sourceLanguage: SourceLanguage,
        val targetLanguage: TargetLanguage,
    ) : TranslationResult

    data class Failure(
        val error: TranslationError,
        override val providerId: String,
        val message: String? = null,
    ) : TranslationResult
}
