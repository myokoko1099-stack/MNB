package com.mnb.browser.translation.gemini

import com.mnb.browser.translation.SourceLanguage
import com.mnb.browser.translation.TargetLanguage
import com.mnb.browser.translation.TranslationError
import com.mnb.browser.translation.TranslationRequest
import com.mnb.browser.translation.TranslationResult
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 06-B2: the adapter from [GeminiProvider] to the translation engine's TranslationProvider,
 * tested against a fake [GeminiProvider]. No network, no Android, no storage. All keys and texts
 * are fake.
 */
class GeminiTranslationProviderTest {

    private class FakeGemini(
        var result: GeminiResult = GeminiResult.Success("translated", "fake-model"),
        override val id: String = DirectGeminiProvider.ID,
        override val model: String = "fake-model",
    ) : GeminiProvider {
        var calls = 0
        var last: TranslationRequest? = null
        var failWith: Throwable? = null

        override fun translate(request: TranslationRequest): GeminiResult {
            calls++
            last = request
            failWith?.let { throw it }
            return result
        }
    }

    private val request = TranslationRequest("Hello world", SourceLanguage.ENGLISH)

    /** The guard says "not the main thread" so the adapter may run (plain JVM has no main looper). */
    private fun adapter(gemini: FakeGemini, onMain: () -> Boolean = { false }) =
        GeminiTranslationProvider(gemini, onMain)

    private fun failureOf(result: TranslationResult): TranslationResult.Failure {
        assertTrue(result.toString(), result is TranslationResult.Failure)
        return result as TranslationResult.Failure
    }

    // ================= identity =================

    @Test
    fun theAdapterIsARealProviderWithTheGeminiId() {
        val provider = adapter(FakeGemini())
        assertEquals(DirectGeminiProvider.ID, provider.id)
        assertFalse(provider.isMock)
        assertEquals(GeminiTranslationProvider.DISPLAY_NAME, provider.displayName)
    }

    @Test
    fun theAdapterSupportsEverySourceLanguage() {
        assertEquals(SourceLanguage.values().toSet(), adapter(FakeGemini()).supportedSourceLanguages)
    }

    // ================= success mapping =================

    @Test
    fun aGeminiSuccessBecomesATranslationSuccess() {
        val gemini = FakeGemini(GeminiResult.Success("မင်္ဂလာပါ", "fake-model"))
        val result = adapter(gemini).translate(request)
        assertTrue(result.toString(), result is TranslationResult.Success)
        result as TranslationResult.Success
        assertEquals("မင်္ဂလာပါ", result.translatedText)
        assertEquals(DirectGeminiProvider.ID, result.providerId)
        assertFalse(result.isMock)
        assertEquals(SourceLanguage.ENGLISH, result.sourceLanguage)
        assertEquals(TargetLanguage.MYANMAR, result.targetLanguage)
        assertEquals(1, gemini.calls)
    }

    @Test
    fun theLanguagesOfTheRequestAreCarriedIntoTheResult() {
        for (source in SourceLanguage.values()) {
            val result = adapter(FakeGemini()).translate(TranslationRequest("text", source))
            result as TranslationResult.Success
            assertEquals(source, result.sourceLanguage)
            assertEquals(TargetLanguage.MYANMAR, result.targetLanguage)
        }
    }

    @Test
    fun theRequestIsPassedToGeminiUnchanged() {
        val gemini = FakeGemini()
        adapter(gemini).translate(request)
        assertEquals(request, gemini.last)
    }

    @Test
    fun aBlankGeminiTextIsNotASuccess() {
        for (blank in listOf("", "   ", "\n\t")) {
            val result = adapter(FakeGemini(GeminiResult.Success(blank, "fake-model"))).translate(request)
            val failure = failureOf(result)
            assertEquals(TranslationError.PROVIDER_FAILURE, failure.error)
            assertEquals(GeminiError.EMPTY_RESPONSE.message, failure.message)
        }
    }

    // ================= failure mapping =================

    @Test
    fun everyGeminiErrorBecomesAProviderFailureWithItsFixedMessage() {
        for (error in GeminiError.values()) {
            val gemini = FakeGemini(GeminiResult.Failure(error))
            val failure = failureOf(adapter(gemini).translate(request))
            assertEquals(error.name, TranslationError.PROVIDER_FAILURE, failure.error)
            assertEquals(error.name, error.message, failure.message)
            assertEquals(error.name, DirectGeminiProvider.ID, failure.providerId)
        }
    }

    @Test
    fun aMissingKeyIsReportedWithTheSettingsHint() {
        val failure = failureOf(adapter(FakeGemini(GeminiResult.Failure(GeminiError.MISSING_API_KEY))).translate(request))
        assertEquals(TranslationError.PROVIDER_FAILURE, failure.error)
        assertEquals(GeminiError.MISSING_API_KEY.message, failure.message)
    }

    // ================= request validation =================

    @Test
    fun anEmptyRequestIsRejectedWithoutCallingGemini() {
        val gemini = FakeGemini()
        val failure = failureOf(adapter(gemini).translate(TranslationRequest("  ", SourceLanguage.ENGLISH)))
        assertEquals(TranslationError.EMPTY_TEXT, failure.error)
        assertEquals(0, gemini.calls)
    }

    @Test
    fun aTooLongRequestIsRejectedWithoutCallingGemini() {
        val gemini = FakeGemini()
        val long = TranslationRequest("a".repeat(TranslationRequest.MAX_TEXT_LENGTH + 1), SourceLanguage.ENGLISH)
        val failure = failureOf(adapter(gemini).translate(long))
        assertEquals(TranslationError.TEXT_TOO_LONG, failure.error)
        assertEquals(0, gemini.calls)
    }

    // ================= exceptions =================

    @Test
    fun anExceptionFromGeminiBecomesAFixedFailureWithoutItsMessage() {
        val gemini = FakeGemini()
        gemini.failWith = IllegalStateException("secret-exception-text $FAKE_KEY Hello world")
        val failure = failureOf(adapter(gemini).translate(request))
        assertEquals(TranslationError.PROVIDER_FAILURE, failure.error)
        assertEquals(GeminiError.INTERNAL.message, failure.message)
        assertFalse(failure.toString().contains(FAKE_KEY))
        assertFalse(failure.toString().contains("secret-exception-text"))
    }

    // ================= main thread guard =================

    @Test
    fun onTheMainThreadNothingIsCalled() {
        val gemini = FakeGemini()
        val failure = failureOf(adapter(gemini, onMain = { true }).translate(request))
        assertEquals(TranslationError.PROVIDER_FAILURE, failure.error)
        assertEquals(GeminiTranslationProvider.MAIN_THREAD_MESSAGE, failure.message)
        assertEquals(0, gemini.calls)
    }

    @Test
    fun aFailingThreadCheckCountsAsTheMainThread() {
        val gemini = FakeGemini()
        val failure = failureOf(adapter(gemini, onMain = { throw IllegalStateException("no looper") }).translate(request))
        assertEquals(GeminiTranslationProvider.MAIN_THREAD_MESSAGE, failure.message)
        assertEquals(0, gemini.calls)
    }

    @Test
    fun theThreadCheckIsFalseOnAnOrdinaryBackgroundThread() {
        val seen = AtomicReference<Boolean?>(null)
        val thread = Thread { seen.set(GeminiTranslationProvider.currentThreadIsAndroidMain()) }
        thread.start()
        thread.join(10_000)
        assertEquals(false, seen.get())
    }

    @Test
    fun aMissingResultFieldNeverLeaksIntoTheResultText() {
        val result = adapter(FakeGemini(GeminiResult.Failure(GeminiError.API_KEY_REJECTED))).translate(request)
        assertFalse(result.toString().contains(FAKE_KEY))
        assertNull((result as TranslationResult.Failure).message?.takeIf { it.contains(FAKE_KEY) })
    }

    companion object {
        const val FAKE_KEY = "fake-test-key-0123456789"
    }
}
