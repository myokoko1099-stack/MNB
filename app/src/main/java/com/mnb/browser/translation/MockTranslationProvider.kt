package com.mnb.browser.translation

/**
 * Test-only provider. It does NOT translate: it returns the input behind a clear marker so
 * it can never be mistaken for real AI output.
 */
class MockTranslationProvider : TranslationProvider {
    override val id = ID
    override val displayName = "Mock (not a real translation)"
    override val isMock = true
    override val supportedSourceLanguages: Set<SourceLanguage> = SourceLanguage.values().toSet()

    override fun translate(request: TranslationRequest): TranslationResult =
        TranslationResult.Success(
            translatedText = MOCK_PREFIX + request.text,
            providerId = id,
            isMock = true,
            sourceLanguage = request.sourceLanguage,
            targetLanguage = request.targetLanguage,
        )

    companion object {
        const val ID = "mock"
        const val MOCK_PREFIX = "[MOCK - not a real translation] "
    }
}
