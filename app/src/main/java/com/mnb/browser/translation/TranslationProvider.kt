package com.mnb.browser.translation

/**
 * Provider-independent translation backend. Real AI providers (a later phase) implement this;
 * nothing outside this package should depend on a concrete provider.
 *
 * Synchronous for now because the project has no coroutines dependency yet. When real
 * network providers arrive this becomes `suspend`.
 */
interface TranslationProvider {
    val id: String
    val displayName: String
    /** True for placeholder providers whose output is not a real translation. */
    val isMock: Boolean
    val supportedSourceLanguages: Set<SourceLanguage>

    /** Callers go through [TranslationService], which validates the request first. */
    fun translate(request: TranslationRequest): TranslationResult
}
