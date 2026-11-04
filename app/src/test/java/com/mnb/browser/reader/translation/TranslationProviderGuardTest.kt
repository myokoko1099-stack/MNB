package com.mnb.browser.reader.translation

import com.mnb.browser.translation.SourceLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationProviderGuardTest {
    @Test fun noProviderIsNotConfigured() =
        assertEquals(ProviderReadiness.NotConfigured, TranslationProviderGuard.check(null, SourceLanguage.ENGLISH))

    @Test fun mockProviderIsBlocked() =
        assertEquals(ProviderReadiness.MockOnly, TranslationProviderGuard.check(C1FakeProvider(isMock = true), SourceLanguage.ENGLISH))

    @Test fun unsupportedLanguageIsBlocked() = assertEquals(
        ProviderReadiness.UnsupportedLanguage,
        TranslationProviderGuard.check(C1FakeProvider(supportedSourceLanguages = setOf(SourceLanguage.ENGLISH)), SourceLanguage.KOREAN),
    )

    @Test fun realProviderIsReady() =
        assertEquals(ProviderReadiness.Ready, TranslationProviderGuard.check(C1FakeProvider(), SourceLanguage.JAPANESE))
}
