package com.mnb.browser.translation.gemini

import com.mnb.browser.security.ApiKey
import com.mnb.browser.security.ApiKeyLookup
import com.mnb.browser.security.ApiKeyProvider
import com.mnb.browser.translation.MockTranslationProvider
import com.mnb.browser.translation.SourceLanguage
import com.mnb.browser.translation.TranslationError
import com.mnb.browser.translation.TranslationRequest
import com.mnb.browser.translation.TranslationResult
import com.mnb.browser.translation.TranslationService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 06-B3: every Gemini failure end to end (key lookup -> retry layer -> parser -> adapter ->
 * TranslationService), with a scripted fake HTTP layer and a fake sleeper. No network, no real
 * waiting. Every key and every "secret" text here is fake.
 */
class GeminiErrorHandlingTest {

    private class ScriptedHttp(private vararg val results: GeminiHttpResult) : GeminiHttpClient {
        var calls = 0
        override fun post(request: GeminiHttpRequest): GeminiHttpResult {
            val result = results[minOf(calls, results.size - 1)]
            calls++
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

    private val waits = mutableListOf<Long>()
    private val request = TranslationRequest("Hello world", SourceLanguage.ENGLISH)

    private fun response(status: Int, body: String = "{}", retryAfterMillis: Long? = null) =
        GeminiHttpResult.Response(GeminiHttpResponse(status, body, retryAfterMillis))

    private fun okBody(text: String = "translated") =
        """{"candidates":[{"content":{"parts":[{"text":"$text"}]},"finishReason":"STOP"}]}"""

    private fun availableKeys() = CountingKeys { ApiKeyLookup.Available(ApiKey(FAKE_KEY)) }

    /** The real provider with the real retry layer, but a fake network and no real sleeping. */
    private fun provider(
        http: GeminiHttpClient,
        keys: ApiKeyProvider = availableKeys(),
        policy: GeminiRetryPolicy = GeminiRetryPolicy(),
    ): DirectGeminiProvider = DirectGeminiProvider(
        keys,
        RetryingGeminiHttpClient(http, policy, { waits += it }, { 0.0 }),
        GeminiConfig(retryPolicy = policy),
    )

    private fun errorOf(http: GeminiHttpClient, keys: ApiKeyProvider = availableKeys()): GeminiError {
        val result = provider(http, keys).translate(request)
        assertTrue(result.toString(), result is GeminiResult.Failure)
        return (result as GeminiResult.Failure).error
    }

    // ================= HTTP status codes =================

    @Test
    fun http401IsARejectedKeyAndIsNotRetried() {
        val http = ScriptedHttp(response(401))
        assertEquals(GeminiError.API_KEY_REJECTED, errorOf(http))
        assertEquals(1, http.calls)
        assertTrue(waits.isEmpty())
    }

    @Test
    fun http403WithoutAReasonIsARejectedKeyAndIsNotRetried() {
        val http = ScriptedHttp(response(403))
        assertEquals(GeminiError.API_KEY_REJECTED, errorOf(http))
        assertEquals(1, http.calls)
    }

    @Test
    fun http403ThatNamesTheKeyIsARejectedKey() {
        val body = """{"error":{"code":403,"message":"Your API key was reported as leaked.","status":"PERMISSION_DENIED"}}"""
        assertEquals(GeminiError.API_KEY_REJECTED, errorOf(ScriptedHttp(response(403, body))))
    }

    @Test
    fun http403PermissionDeniedWithoutAKeyReasonIsPermissionDenied() {
        val body = """{"error":{"code":403,"message":"Generative Language API has not been used in project 1 before.","status":"PERMISSION_DENIED"}}"""
        val http = ScriptedHttp(response(403, body))
        assertEquals(GeminiError.PERMISSION_DENIED, errorOf(http))
        assertEquals(1, http.calls)
    }

    @Test
    fun http404IsAMissingModelAndIsNotRetried() {
        val http = ScriptedHttp(response(404))
        assertEquals(GeminiError.MODEL_NOT_FOUND, errorOf(http))
        assertEquals(1, http.calls)
    }

    @Test
    fun http400IsABadRequestAndIsNotRetried() {
        val http = ScriptedHttp(response(400))
        assertEquals(GeminiError.BAD_REQUEST, errorOf(http))
        assertEquals(1, http.calls)
    }

    @Test
    fun http429IsARateLimitThatIsRetriedUntilTheLimit() {
        val http = ScriptedHttp(response(429))
        assertEquals(GeminiError.RATE_LIMITED, errorOf(http))
        assertEquals(GeminiRetryPolicy.DEFAULT_MAX_ATTEMPTS, http.calls)
    }

    @Test
    fun http429ThatClearsAfterAWaitEndsInATranslation() {
        val http = ScriptedHttp(response(429, retryAfterMillis = 2_000), response(200, okBody("မင်္ဂလာပါ")))
        val result = provider(http).translate(request)
        assertEquals(GeminiResult.Success("မင်္ဂလာပါ", GeminiConfig.DEFAULT_MODEL), result)
        assertEquals(2, http.calls)
        assertEquals(listOf(2_000L), waits)
    }

    @Test
    fun http429WithARetryAfterTooLongToWaitFailsFastAsARateLimit() {
        val http = ScriptedHttp(response(429, retryAfterMillis = 120_000))
        assertEquals(GeminiError.RATE_LIMITED, errorOf(http))
        assertEquals(1, http.calls)
        assertTrue(waits.isEmpty())
    }

    @Test
    fun aUsedUpDailyQuotaIsNotRetriedAndIsToldApart() {
        val body = """{"error":{"code":429,"status":"RESOURCE_EXHAUSTED","details":[{"violations":[{"quotaId":"GenerateRequestsPerDayPerProjectPerModel-FreeTier"}]}]}}"""
        val http = ScriptedHttp(response(429, body))
        assertEquals(GeminiError.QUOTA_EXCEEDED, errorOf(http))
        assertEquals(1, http.calls)
        assertFalse(GeminiError.QUOTA_EXCEEDED.retryable)
    }

    @Test
    fun aLimitOfZeroIsAQuotaProblemToo() {
        val body = """{"error":{"message":"Quota exceeded for metric ..., limit: 0"}}"""
        assertEquals(GeminiError.QUOTA_EXCEEDED, errorOf(ScriptedHttp(response(429, body))))
    }

    @Test
    fun aPerMinuteRateLimitIsNotAQuotaProblem() {
        val body = """{"error":{"message":"Quota exceeded","details":[{"violations":[{"quotaId":"GenerateRequestsPerMinutePerProjectPerModel"}]}]}}"""
        assertEquals(GeminiError.RATE_LIMITED, errorOf(ScriptedHttp(response(429, body))))
    }

    @Test
    fun http5xxIsRetriedAndThenReportedAsAServerError() {
        for (status in listOf(500, 502, 503, 504)) {
            val http = ScriptedHttp(response(status))
            assertEquals("status $status", GeminiError.SERVER_ERROR, errorOf(http))
            assertEquals("status $status", GeminiRetryPolicy.DEFAULT_MAX_ATTEMPTS, http.calls)
        }
    }

    @Test
    fun aServerErrorThatPassesIsInvisibleToTheCaller() {
        val http = ScriptedHttp(response(503), response(503), response(200, okBody()))
        val result = provider(http).translate(request)
        assertTrue(result.toString(), result is GeminiResult.Success)
        assertEquals(3, http.calls)
        assertEquals(listOf(1_000L, 2_000L), waits)
    }

    // ================= timeouts and network =================

    @Test
    fun aConnectTimeoutIsReportedAsOneAndRetried() {
        val http = ScriptedHttp(GeminiHttpResult.Failure(GeminiHttpFailure.CONNECT_TIMEOUT))
        assertEquals(GeminiError.CONNECT_TIMEOUT, errorOf(http))
        assertEquals(GeminiRetryPolicy.DEFAULT_MAX_ATTEMPTS, http.calls)
    }

    @Test
    fun aReadTimeoutIsReportedAsOneAndRetriedOnce() {
        val http = ScriptedHttp(GeminiHttpResult.Failure(GeminiHttpFailure.TIMEOUT))
        assertEquals(GeminiError.TIMEOUT, errorOf(http))
        assertEquals(2, http.calls)
    }

    @Test
    fun aTimeoutNeverCrashesAndComesBackAsAFailureResult() {
        val http = ScriptedHttp(GeminiHttpResult.Failure(GeminiHttpFailure.TIMEOUT))
        val service = TranslationService(GeminiTranslationProvider(provider(http)) { false })
        val result = service.translate(request)
        result as TranslationResult.Failure
        assertEquals(TranslationError.PROVIDER_FAILURE, result.error)
        assertEquals(GeminiError.TIMEOUT.message, result.message)
    }

    @Test
    fun aNetworkFailureIsRetriedAndThenReported() {
        for (kind in listOf(GeminiHttpFailure.NO_CONNECTION, GeminiHttpFailure.IO)) {
            val http = ScriptedHttp(GeminiHttpResult.Failure(kind))
            assertEquals(kind.name, GeminiError.NETWORK_UNAVAILABLE, errorOf(http))
            assertEquals(kind.name, GeminiRetryPolicy.DEFAULT_MAX_ATTEMPTS, http.calls)
        }
    }

    @Test
    fun aNetworkThatComesBackEndsInATranslation() {
        val http = ScriptedHttp(GeminiHttpResult.Failure(GeminiHttpFailure.NO_CONNECTION), response(200, okBody()))
        assertTrue(provider(http).translate(request) is GeminiResult.Success)
        assertEquals(2, http.calls)
    }

    @Test
    fun aTlsFailureIsNotRetried() {
        val http = ScriptedHttp(GeminiHttpResult.Failure(GeminiHttpFailure.TLS))
        assertEquals(GeminiError.SECURE_CONNECTION_FAILED, errorOf(http))
        assertEquals(1, http.calls)
    }

    @Test
    fun theRetryNeverReadsTheKeyAgain() {
        val keys = availableKeys()
        val http = ScriptedHttp(response(503))
        provider(http, keys).translate(request)
        assertEquals(GeminiRetryPolicy.DEFAULT_MAX_ATTEMPTS, http.calls)
        assertEquals(1, keys.calls)
    }

    // ================= response content =================

    @Test
    fun anEmptyResponseIsReportedAndNotRetried() {
        for (body in listOf("{}", """{"candidates":[]}""", okBody("   "))) {
            val http = ScriptedHttp(response(200, body))
            assertEquals(body, GeminiError.EMPTY_RESPONSE, errorOf(http))
            assertEquals(body, 1, http.calls)
        }
    }

    @Test
    fun malformedJsonIsReportedAndNotRetried() {
        for (body in listOf("not json", "", "{", "[1,2", "<html>oops</html>")) {
            val http = ScriptedHttp(response(200, body))
            assertEquals(body, GeminiError.MALFORMED_RESPONSE, errorOf(http))
            assertEquals(body, 1, http.calls)
        }
    }

    @Test
    fun aResponseWithTheWrongShapeIsAParsingFailure() {
        for (body in listOf("""{"candidates":[null]}""", """{"candidates":"x"}""", "[]")) {
            val error = errorOf(ScriptedHttp(response(200, body)))
            assertTrue(body + " -> " + error, error == GeminiError.MALFORMED_RESPONSE || error == GeminiError.EMPTY_RESPONSE)
        }
    }

    // ================= missing key / unknown error =================

    @Test
    fun aMissingKeySendsNothingAndIsNotRetried() {
        val http = ScriptedHttp(response(200, okBody()))
        assertEquals(GeminiError.MISSING_API_KEY, errorOf(http, CountingKeys { ApiKeyLookup.Missing }))
        assertEquals(0, http.calls)
    }

    @Test
    fun anExceptionFromTheNetworkLayerIsAnUnknownErrorWithoutItsMessage() {
        val throwing = GeminiHttpClient { throw IllegalStateException("secret-exception-text $FAKE_KEY") }
        val result = provider(throwing).translate(request)
        assertEquals(GeminiResult.Failure(GeminiError.INTERNAL), result)
        assertFalse(result.toString().contains(FAKE_KEY))
        assertFalse(result.toString().contains("secret-exception-text"))
    }

    // ================= redaction =================

    @Test
    fun noFailureAfterAllRetriesHoldsTheKeyOrServerText() {
        val body = """{"error":{"message":"secret-body-text key=$FAKE_KEY Hello world"}}"""
        for (status in listOf(400, 401, 403, 404, 429, 500, 503)) {
            val service = TranslationService(GeminiTranslationProvider(provider(ScriptedHttp(response(status, body)))) { false })
            val result = service.translate(request)
            val text = result.toString()
            assertFalse("status $status: $text", text.contains(FAKE_KEY))
            assertFalse("status $status: $text", text.contains("secret-body-text"))
            assertFalse("status $status: $text", text.contains("Hello world"))
            val failure = result as TranslationResult.Failure
            assertEquals("status $status", TranslationError.PROVIDER_FAILURE, failure.error)
        }
        assertFalse(waits.toString().contains(FAKE_KEY))
    }

    @Test
    fun everyErrorMessageIsFixedAndSafeToShow() {
        for (error in GeminiError.values()) {
            assertTrue(error.name, error.message.isNotBlank())
            assertFalse(error.name, error.message.contains(FAKE_KEY))
            assertFalse(error.name, error.message.contains("AIza"))
            assertFalse(error.name, error.message.contains("http", ignoreCase = true))
            assertFalse(error.name, error.message.contains("key=", ignoreCase = true))
        }
        assertEquals(GeminiError.values().size, GeminiError.values().map { it.message }.toSet().size)
    }

    @Test
    fun everyGeminiErrorReachesTheEngineAsAProviderFailureWithItsOwnMessage() {
        for (error in GeminiError.values()) {
            val fake = object : GeminiProvider {
                override val id = DirectGeminiProvider.ID
                override val model = "m"
                override fun translate(request: TranslationRequest): GeminiResult = GeminiResult.Failure(error)
            }
            val result = TranslationService(GeminiTranslationProvider(fake) { false }).translate(request)
            result as TranslationResult.Failure
            assertEquals(error.name, TranslationError.PROVIDER_FAILURE, result.error)
            assertEquals(error.name, error.message, result.message)
        }
    }

    // ================= regression =================

    @Test
    fun theRealDefaultProviderCanBeBuiltWithoutTouchingTheNetworkOrTheKey() {
        val keys = availableKeys()
        val provider = DirectGeminiProvider(keys)
        assertEquals(DirectGeminiProvider.ID, provider.id)
        assertEquals(GeminiConfig.DEFAULT_MODEL, provider.model)
        assertEquals(0, keys.calls)
    }

    @Test
    fun aSuccessfulTranslationThroughTheServiceIsUnchanged() {
        val http = ScriptedHttp(response(200, okBody("မင်္ဂလာပါ")))
        val service = TranslationService(GeminiTranslationProvider(provider(http)) { false })
        val result = service.translate(request) as TranslationResult.Success
        assertEquals("မင်္ဂလာပါ", result.translatedText)
        assertFalse(result.isMock)
        assertEquals(1, http.calls)
        assertTrue(waits.isEmpty())
    }

    @Test
    fun theMockProviderStillWorksAndNeverRetriesOrWaits() {
        val result = TranslationService(MockTranslationProvider()).translate(request) as TranslationResult.Success
        assertEquals(MockTranslationProvider.MOCK_PREFIX + "Hello world", result.translatedText)
        assertTrue(result.isMock)
        assertTrue(waits.isEmpty())
    }

    companion object {
        const val FAKE_KEY = "fake-test-key-0123456789"
    }
}
