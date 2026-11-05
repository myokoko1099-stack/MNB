package com.mnb.browser.translation.gemini

import com.mnb.browser.security.ApiKey
import com.mnb.browser.security.ApiKeyLookup
import com.mnb.browser.security.ApiKeyProvider
import com.mnb.browser.security.SecureStorageError
import com.mnb.browser.translation.SourceLanguage
import com.mnb.browser.translation.TranslationRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 06-B1: the Gemini provider against a fake HTTP layer. No network is used. Every key and
 * every piece of "secret" text here is fake.
 */
class DirectGeminiProviderTest {

    private class FakeHttp(var result: GeminiHttpResult) : GeminiHttpClient {
        var calls = 0
        var last: GeminiHttpRequest? = null
        var failWith: Throwable? = null

        override fun post(request: GeminiHttpRequest): GeminiHttpResult {
            calls++
            last = request
            failWith?.let { throw it }
            return result
        }
    }

    private class CountingKeys(private val lookup: () -> ApiKeyLookup) : ApiKeyProvider {
        var calls = 0
        override fun lookup(): ApiKeyLookup {
            calls++
            return lookup.invoke()
        }
    }

    private val request = TranslationRequest("Hello \"world\"\nSecond line", SourceLanguage.ENGLISH)

    private fun okBody(text: String = "translated text") =
        """{"candidates":[{"content":{"parts":[{"text":"$text"}]},"finishReason":"STOP"}]}"""

    private fun response(status: Int, body: String = "") =
        GeminiHttpResult.Response(GeminiHttpResponse(status, body))

    private fun availableKeys() = CountingKeys { ApiKeyLookup.Available(ApiKey(FAKE_KEY)) }

    private fun provider(
        http: FakeHttp,
        keys: ApiKeyProvider = availableKeys(),
        config: GeminiConfig = GeminiConfig(),
    ) = DirectGeminiProvider(keys, http, config)

    private fun failureOf(result: GeminiResult): GeminiError {
        assertTrue(result.toString(), result is GeminiResult.Failure)
        return (result as GeminiResult.Failure).error
    }

    // ================= no key: nothing is sent =================

    @Test
    fun aMissingKeyReturnsAnErrorAndSendsNothing() {
        val http = FakeHttp(response(200, okBody()))
        val result = provider(http, CountingKeys { ApiKeyLookup.Missing }).translate(request)
        assertEquals(GeminiError.MISSING_API_KEY, failureOf(result))
        assertEquals(0, http.calls)
    }

    @Test
    fun anUnavailableKeyReturnsAnErrorAndSendsNothing() {
        for (error in SecureStorageError.values()) {
            val http = FakeHttp(response(200, okBody()))
            val result = provider(http, CountingKeys { ApiKeyLookup.Unavailable(error) }).translate(request)
            assertEquals(error.name, GeminiError.API_KEY_UNAVAILABLE, failureOf(result))
            assertEquals(error.name, 0, http.calls)
        }
    }

    @Test
    fun aKeyLookupThatThrowsAnExceptionReturnsAnErrorAndSendsNothing() {
        val http = FakeHttp(response(200, okBody()))
        val keys = CountingKeys { throw IllegalStateException("boom $FAKE_KEY") }
        val result = provider(http, keys).translate(request)
        assertEquals(GeminiError.API_KEY_UNAVAILABLE, failureOf(result))
        assertEquals(0, http.calls)
        assertFalse(result.toString().contains(FAKE_KEY))
    }

    @Test
    fun aKeyLookupThatThrowsAnErrorReturnsAnErrorAndSendsNothing() {
        val http = FakeHttp(response(200, okBody()))
        val keys = CountingKeys { throw LinkageError("boom $FAKE_KEY") }
        val result = provider(http, keys).translate(request)
        assertEquals(GeminiError.API_KEY_UNAVAILABLE, failureOf(result))
        assertEquals(0, http.calls)
    }

    @Test
    fun anInvalidRequestIsRefusedBeforeTheKeyIsRead() {
        val http = FakeHttp(response(200, okBody()))
        val keys = availableKeys()
        val sut = provider(http, keys)
        assertEquals(GeminiError.INVALID_REQUEST, failureOf(sut.translate(TranslationRequest("   ", SourceLanguage.ENGLISH))))
        val tooLong = TranslationRequest("a".repeat(TranslationRequest.MAX_TEXT_LENGTH + 1), SourceLanguage.ENGLISH)
        assertEquals(GeminiError.INVALID_REQUEST, failureOf(sut.translate(tooLong)))
        assertEquals(0, keys.calls)
        assertEquals(0, http.calls)
    }

    // ================= where the key goes =================

    @Test
    fun theKeyIsOnlyInTheRequestHeader() {
        val http = FakeHttp(response(200, okBody()))
        provider(http).translate(request)

        val sent = http.last!!
        assertEquals(FAKE_KEY, sent.headers[GeminiConfig.API_KEY_HEADER])
        assertFalse("key in url", sent.url.contains(FAKE_KEY))
        assertFalse("key in body", sent.body.contains(FAKE_KEY))
        assertFalse("key in url", sent.url.contains("key=", ignoreCase = true))
        val otherHeaders = sent.headers.filterKeys { it != GeminiConfig.API_KEY_HEADER }
        assertTrue("key in another header", otherHeaders.values.none { it.contains(FAKE_KEY) })
    }

    @Test
    fun theRequestGoesToTheHttpsGenerateContentEndpointOfTheModel() {
        val http = FakeHttp(response(200, okBody()))
        provider(http, config = GeminiConfig(model = "gemini-test-model")).translate(request)

        val url = http.last!!.url
        assertTrue(url, url.startsWith("https://"))
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-test-model:generateContent",
            url,
        )
        assertNull(java.net.URL(url).query)
    }

    @Test
    fun theKeyIsReadAtSendTimeForEveryCall() {
        val http = FakeHttp(response(200, okBody()))
        val keys = availableKeys()
        val sut = provider(http, keys)
        sut.translate(request)
        sut.translate(request)
        assertEquals(2, keys.calls)
        assertEquals(2, http.calls)
    }

    @Test
    fun aRequestObjectNeverPrintsTheKeyOrTheText() {
        val http = FakeHttp(response(200, okBody()))
        provider(http).translate(request)
        val text = http.last!!.toString()
        assertFalse(text.contains(FAKE_KEY))
        assertFalse(text.contains("Second line"))
    }

    // ================= success =================

    @Test
    fun aGoodResponseReturnsTheTranslatedText() {
        val http = FakeHttp(response(200, okBody("မင်္ဂလာပါ")))
        val result = provider(http).translate(request)
        assertEquals(GeminiResult.Success("မင်္ဂလာပါ", GeminiConfig.DEFAULT_MODEL), result)
    }

    @Test
    fun theProviderReportsItsIdAndModel() {
        val sut = provider(FakeHttp(response(200, okBody())), config = GeminiConfig(model = "gemini-x"))
        assertEquals("gemini-direct", sut.id)
        assertEquals("gemini-x", sut.model)
    }

    // ================= HTTP errors =================

    @Test
    fun httpStatusCodesMapToFixedErrors() {
        val table = listOf(
            400 to GeminiError.BAD_REQUEST,
            401 to GeminiError.API_KEY_REJECTED,
            403 to GeminiError.API_KEY_REJECTED,
            404 to GeminiError.MODEL_NOT_FOUND,
            408 to GeminiError.BAD_REQUEST,
            429 to GeminiError.RATE_LIMITED,
            500 to GeminiError.SERVER_ERROR,
            502 to GeminiError.SERVER_ERROR,
            503 to GeminiError.SERVER_ERROR,
            302 to GeminiError.BAD_REQUEST,
        )
        for ((status, expected) in table) {
            val result = provider(FakeHttp(response(status, "{}"))).translate(request)
            assertEquals("status $status", expected, failureOf(result))
        }
    }

    @Test
    fun aBadKeyAnswerWithStatus400IsReportedAsARejectedKey() {
        val body = """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT"}}"""
        assertEquals(GeminiError.API_KEY_REJECTED, failureOf(provider(FakeHttp(response(400, body))).translate(request)))
        val byReason = """{"error":{"details":[{"reason":"API_KEY_INVALID"}]}}"""
        assertEquals(GeminiError.API_KEY_REJECTED, failureOf(provider(FakeHttp(response(400, byReason))).translate(request)))
    }

    @Test
    fun anErrorBodyIsNeverCopiedIntoTheResult() {
        val body = """{"error":{"message":"secret-body-text $FAKE_KEY"}}"""
        for (status in listOf(400, 401, 403, 404, 429, 500, 503)) {
            val result = provider(FakeHttp(response(status, body))).translate(request)
            val text = result.toString()
            assertFalse(text, text.contains("secret-body-text"))
            assertFalse(text, text.contains(FAKE_KEY))
            val error = failureOf(result)
            assertFalse(error.message.contains("secret-body-text"))
        }
    }

    @Test
    fun retryableFlagsFollowTheKindOfFailure() {
        assertTrue(GeminiError.RATE_LIMITED.retryable)
        assertTrue(GeminiError.SERVER_ERROR.retryable)
        assertTrue(GeminiError.NETWORK_UNAVAILABLE.retryable)
        assertTrue(GeminiError.TIMEOUT.retryable)
        assertFalse(GeminiError.API_KEY_REJECTED.retryable)
        assertFalse(GeminiError.MISSING_API_KEY.retryable)
        assertFalse(GeminiError.CONTENT_BLOCKED.retryable)
    }

    // ================= transport failures =================

    @Test
    fun transportFailuresMapToFixedErrors() {
        val table = mapOf(
            GeminiHttpFailure.CONNECT_TIMEOUT to GeminiError.CONNECT_TIMEOUT,
            GeminiHttpFailure.TIMEOUT to GeminiError.TIMEOUT,
            GeminiHttpFailure.NO_CONNECTION to GeminiError.NETWORK_UNAVAILABLE,
            GeminiHttpFailure.IO to GeminiError.NETWORK_UNAVAILABLE,
            GeminiHttpFailure.TLS to GeminiError.SECURE_CONNECTION_FAILED,
            GeminiHttpFailure.TOO_LARGE to GeminiError.RESPONSE_TOO_LARGE,
            GeminiHttpFailure.INSECURE_URL to GeminiError.INTERNAL,
        )
        assertEquals(GeminiHttpFailure.values().toSet(), table.keys)
        for ((failure, expected) in table) {
            val result = provider(FakeHttp(GeminiHttpResult.Failure(failure))).translate(request)
            assertEquals(failure.name, expected, failureOf(result))
        }
    }

    @Test
    fun aHttpClientThatThrowsGivesAnInternalErrorWithoutLeaking() {
        val http = FakeHttp(response(200, okBody()))
        http.failWith = IllegalStateException("secret-exception-text $FAKE_KEY")
        val result = provider(http).translate(request)
        assertEquals(GeminiError.INTERNAL, failureOf(result))
        val text = result.toString()
        assertFalse(text.contains(FAKE_KEY))
        assertFalse(text.contains("secret-exception-text"))
    }

    // ================= response content =================

    @Test
    fun responseProblemsMapToFixedErrors() {
        val table = listOf(
            "not json" to GeminiError.MALFORMED_RESPONSE,
            "{}" to GeminiError.EMPTY_RESPONSE,
            """{"candidates":[]}""" to GeminiError.EMPTY_RESPONSE,
            """{"candidates":[{"content":{"parts":[{"text":"  "}]},"finishReason":"STOP"}]}""" to GeminiError.EMPTY_RESPONSE,
            """{"promptFeedback":{"blockReason":"SAFETY"}}""" to GeminiError.CONTENT_BLOCKED,
            """{"candidates":[{"finishReason":"SAFETY"}]}""" to GeminiError.CONTENT_BLOCKED,
            """{"candidates":[{"finishReason":"PROHIBITED_CONTENT"}]}""" to GeminiError.CONTENT_BLOCKED,
            """{"candidates":[{"content":{"parts":[{"text":"cut"}]},"finishReason":"MAX_TOKENS"}]}""" to GeminiError.TRUNCATED_RESPONSE,
            """{"candidates":[null]}""" to GeminiError.MALFORMED_RESPONSE,
        )
        for ((body, expected) in table) {
            val result = provider(FakeHttp(response(200, body))).translate(request)
            assertEquals(body, expected, failureOf(result))
        }
    }

    @Test
    fun aTruncatedAnswerIsNeverReturnedAsATranslation() {
        val body = """{"candidates":[{"content":{"parts":[{"text":"half a chapter"}]},"finishReason":"MAX_TOKENS"}]}"""
        val result = provider(FakeHttp(response(200, body))).translate(request)
        assertTrue(result is GeminiResult.Failure)
    }

    @Test
    fun severalTextPartsAreJoinedAndThoughtPartsAreSkipped() {
        val body = """{"candidates":[{"content":{"parts":[
            {"text":"hidden reasoning","thought":true},
            {"text":"first "},{"text":"second"}]},"finishReason":"STOP"}]}"""
        val result = provider(FakeHttp(response(200, body))).translate(request)
        assertEquals("first second", (result as GeminiResult.Success).translatedText)
    }

    @Test
    fun aJsonNullTextIsNotTurnedIntoTheWordNull() {
        val body = """{"candidates":[{"content":{"parts":[{"text":null}]},"finishReason":"STOP"}]}"""
        val result = provider(FakeHttp(response(200, body))).translate(request)
        assertEquals(GeminiError.EMPTY_RESPONSE, failureOf(result))
    }

    // ================= no failure text can hold a key =================

    @Test
    fun noFixedMessageLooksLikeAKeyOrAUrl() {
        for (error in GeminiError.values()) {
            assertTrue(error.name, error.message.isNotBlank())
            assertFalse(error.name, error.message.contains("AIza"))
            assertFalse(error.name, error.message.contains("http", ignoreCase = true))
            assertNotNull(error.message)
        }
    }

    private companion object {
        const val FAKE_KEY = "AIzaSyFAKE-test_key_1234567890"
    }
}
