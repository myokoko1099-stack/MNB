package com.mnb.browser.reader.translation

import com.mnb.browser.translation.SourceLanguage
import com.mnb.browser.translation.TranslationProvider

/** Whether a provider may be used to translate a chapter for the reader (Phase 04-I10-C1). */
sealed interface ProviderReadiness {
    data object Ready : ProviderReadiness
    /** No provider is configured. */
    data object NotConfigured : ProviderReadiness
    /** The provider is a placeholder; its output must never be shown as a translation. */
    data object MockOnly : ProviderReadiness
    data object UnsupportedLanguage : ProviderReadiness
}

object TranslationProviderGuard {
    fun check(provider: TranslationProvider?, source: SourceLanguage): ProviderReadiness = when {
        provider == null -> ProviderReadiness.NotConfigured
        provider.isMock -> ProviderReadiness.MockOnly
        source !in provider.supportedSourceLanguages -> ProviderReadiness.UnsupportedLanguage
        else -> ProviderReadiness.Ready
    }
}
