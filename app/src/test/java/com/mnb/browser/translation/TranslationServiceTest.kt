package com.mnb.browser.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationServiceTest {
    private class FakeProvider(
        override val supportedSourceLanguages: Set<SourceLanguage> = SourceLanguage.values().toSet(),
        private val onTranslate: () -> TranslationResult = { error("provider should not be called") },
    ) : TranslationProvider {
        override val id = "fake"
        override val displayName = "Fake"
        override val isMock = true
        var calls = 0
        override fun translate(request: TranslationRequest): TranslationResult {
            calls++
            return onTranslate()
        }
    }

    @Test fun validRequestReachesMockProvider() {
        val result = TranslationService(MockTranslationProvider())
            .translate(TranslationRequest("你好", SourceLanguage.CHINESE))
        assertTrue(result is TranslationResult.Success)
        assertEquals(MockTranslationProvider.MOCK_PREFIX + "你好", (result as TranslationResult.Success).translatedText)
    }

    @Test fun invalidRequestFailsWithoutCallingProvider() {
        val provider = FakeProvider()
        val service = TranslationService(provider)
        val empty = service.translate(TranslationRequest(" ", SourceLanguage.ENGLISH))
        val long = service.translate(
            TranslationRequest("a".repeat(TranslationRequest.MAX_TEXT_LENGTH + 1), SourceLanguage.ENGLISH),
        )
        assertEquals(TranslationError.EMPTY_TEXT, (empty as TranslationResult.Failure).error)
        assertEquals(TranslationError.TEXT_TOO_LONG, (long as TranslationResult.Failure).error)
        assertEquals(0, provider.calls)
    }

    @Test fun languageNotSupportedByProviderIsRejected() {
        val provider = FakeProvider(supportedSourceLanguages = setOf(SourceLanguage.ENGLISH))
        val result = TranslationService(provider).translate(TranslationRequest("안녕", SourceLanguage.KOREAN))
        assertEquals(TranslationError.UNSUPPORTED_SOURCE_LANGUAGE, (result as TranslationResult.Failure).error)
        assertEquals(0, provider.calls)
    }

    @Test fun providerExceptionBecomesFailureResult() {
        val provider = FakeProvider(onTranslate = { throw IllegalStateException("boom") })
        val result = TranslationService(provider).translate(TranslationRequest("Hi", SourceLanguage.ENGLISH))
        result as TranslationResult.Failure
        assertEquals(TranslationError.PROVIDER_FAILURE, result.error)
        assertEquals("boom", result.message)
        assertEquals("fake", result.providerId)
    }

    @Test fun providerResultIsReturnedUnchanged() {
        val expected = TranslationResult.Failure(TranslationError.PROVIDER_FAILURE, "fake", "offline")
        val provider = FakeProvider(onTranslate = { expected })
        val result = TranslationService(provider).translate(TranslationRequest("Hi", SourceLanguage.ENGLISH))
        assertEquals(expected, result)
    }
}
