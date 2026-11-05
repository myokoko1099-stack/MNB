package com.mnb.browser.translation.gemini

import com.mnb.browser.security.ApiKeyProvider
import com.mnb.browser.translation.TranslationProvider

/**
 * Phase 06-B2: hands the Gemini translation provider to the app. The provider is built once and
 * lazily; building it does no Keystore, file or network work.
 *
 * [providerOrNull] is what the reader asks for on the caller's thread (possibly the main thread),
 * so it must stay cheap: it only calls [keyIsSaved], which must be a non-blocking in-memory check
 * (in the app: the status of the Phase 06-A2 settings). Without a saved key it returns null, and
 * the reader keeps showing "not configured" exactly as before this phase. The key itself is read
 * later, on the translation thread, by [DirectGeminiProvider] through [keys].
 */
class GeminiTranslationWiring(
    keys: ApiKeyProvider,
    private val keyIsSaved: () -> Boolean,
    providerFactory: (ApiKeyProvider) -> TranslationProvider = { provider ->
        GeminiTranslationProvider(DirectGeminiProvider(provider))
    },
) {
    val provider: TranslationProvider by lazy { providerFactory(keys) }

    /** Null while no key is known to be saved. Never blocks and never throws. */
    fun providerOrNull(): TranslationProvider? = try {
        if (keyIsSaved()) provider else null
    } catch (e: Exception) {
        null
    }

    /** The id the reader uses for the background worker's provider check; null without a key. */
    fun readerProviderId(): String? = providerOrNull()?.id
}
