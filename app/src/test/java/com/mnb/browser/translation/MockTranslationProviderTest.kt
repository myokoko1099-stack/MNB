package com.mnb.browser.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockTranslationProviderTest {
    private val provider = MockTranslationProvider()

    @Test fun isFlaggedAsMock() {
        assertTrue(provider.isMock)
        assertEquals("mock", provider.id)
    }

    @Test fun supportsAllFourSourceLanguages() {
        assertEquals(SourceLanguage.values().toSet(), provider.supportedSourceLanguages)
    }

    @Test fun outputIsMarkedAndNeverPassedOffAsRealTranslation() {
        val result = provider.translate(TranslationRequest("Hello", SourceLanguage.ENGLISH))
                as TranslationResult.Success
        assertTrue(result.isMock)
        assertEquals(MockTranslationProvider.MOCK_PREFIX + "Hello", result.translatedText)
        assertTrue(result.translatedText.startsWith("[MOCK"))
    }

    @Test fun resultCarriesLanguagesAndProvider() {
        for (lang in SourceLanguage.values()) {
            val result = provider.translate(TranslationRequest("text", lang)) as TranslationResult.Success
            assertEquals(lang, result.sourceLanguage)
            assertEquals(TargetLanguage.MYANMAR, result.targetLanguage)
            assertEquals("mock", result.providerId)
        }
    }
}
