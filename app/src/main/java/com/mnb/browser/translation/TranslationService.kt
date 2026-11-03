package com.mnb.browser.translation

/**
 * Single entry point for translation. Validates the request, checks the provider supports the
 * language and turns provider exceptions into [TranslationResult.Failure]. Chunking, context
 * memory and quality checks plug in here in later phases. UI code must not call providers
 * directly.
 */
class TranslationService(private val provider: TranslationProvider) {

    fun translate(request: TranslationRequest): TranslationResult {
        request.validate()?.let { return TranslationResult.Failure(it, provider.id) }
        if (request.sourceLanguage !in provider.supportedSourceLanguages) {
            return TranslationResult.Failure(TranslationError.UNSUPPORTED_SOURCE_LANGUAGE, provider.id)
        }
        return try {
            provider.translate(request)
        } catch (e: Exception) {
            TranslationResult.Failure(TranslationError.PROVIDER_FAILURE, provider.id, e.message)
        }
    }
}
