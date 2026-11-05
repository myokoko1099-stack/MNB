package com.mnb.browser.translation.gemini

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Phase 06-B3: the bounded retry around the Gemini HTTP call. A scripted fake client answers and a
 * fake sleeper records the waits, so nothing really sleeps and no network is used.
 */
class GeminiRetryTest {

    /** Answers with [results] in order; the last answer repeats. */
    private class ScriptedHttp(private vararg val results: GeminiHttpResult) : GeminiHttpClient {
        var calls = 0
        val requests = mutableListOf<GeminiHttpRequest>()

        override fun post(request: GeminiHttpRequest): GeminiHttpResult {
            requests += request
            val result = results[minOf(calls, results.size - 1)]
            calls++
            return result
        }
    }

    private class RecordingSleeper : GeminiSleeper {
        val waits = mutableListOf<Long>()
        override fun sleep(millis: Long) {
            waits += millis
        }
    }

    private val request = GeminiHttpRequest("https://example.test/x", mapOf("x-goog-api-key" to FAKE_KEY), "{}")

    private fun response(status: Int, body: String = "{}", retryAfterMillis: Long? = null) =
        GeminiHttpResult.Response(GeminiHttpResponse(status, body, retryAfterMillis))

    private fun failure(kind: GeminiHttpFailure) = GeminiHttpResult.Failure(kind)

    private val ok = response(200, "{}")

    private fun client(
        http: ScriptedHttp,
        sleeper: GeminiSleeper = RecordingSleeper(),
        policy: GeminiRetryPolicy = GeminiRetryPolicy(),
        random: () -> Double = { 0.0 },
    ) = RetryingGeminiHttpClient(http, policy, sleeper, random)

    // ================= what is retried =================

    @Test
    fun aGoodAnswerIsReturnedAtOnceWithoutWaiting() {
        val http = ScriptedHttp(ok)
        val sleeper = RecordingSleeper()
        assertSame(ok, client(http, sleeper).post(request))
        assertEquals(1, http.calls)
        assertTrue(sleeper.waits.isEmpty())
    }

    @Test
    fun aServerErrorIsRetriedAndASuccessEndsTheRetries() {
        for (status in listOf(500, 502, 503, 504)) {
            val http = ScriptedHttp(response(status), ok)
            assertSame("status $status", ok, client(http).post(request))
            assertEquals("status $status", 2, http.calls)
        }
    }

    @Test
    fun aRateLimitIsRetried() {
        val http = ScriptedHttp(response(429), ok)
        assertSame(ok, client(http).post(request))
        assertEquals(2, http.calls)
    }

    @Test
    fun networkFailuresAreRetried() {
        for (kind in listOf(GeminiHttpFailure.CONNECT_TIMEOUT, GeminiHttpFailure.NO_CONNECTION, GeminiHttpFailure.IO)) {
            val http = ScriptedHttp(failure(kind), ok)
            assertSame(kind.name, ok, client(http).post(request))
            assertEquals(kind.name, 2, http.calls)
        }
    }

    @Test
    fun answersThatARetryCannotFixAreNotRetried() {
        val noRetry = listOf(
            response(400), response(401), response(403), response(404), response(408), response(501),
            response(302), response(
                429,
                """{"error":{"details":[{"violations":[{"quotaId":"GenerateRequestsPerDayPerProjectPerModel"}]}]}}""",
            ),
            failure(GeminiHttpFailure.TLS), failure(GeminiHttpFailure.TOO_LARGE), failure(GeminiHttpFailure.INSECURE_URL),
        )
        for (result in noRetry) {
            val http = ScriptedHttp(result, ok)
            val sleeper = RecordingSleeper()
            assertSame(result.toString(), result, client(http, sleeper).post(request))
            assertEquals(result.toString(), 1, http.calls)
            assertTrue(result.toString(), sleeper.waits.isEmpty())
        }
    }

    // ================= retry count =================

    @Test
    fun aServerThatKeepsFailingIsTriedExactlyMaxAttemptsTimes() {
        val last = response(503)
        val http = ScriptedHttp(last)
        val result = client(http).post(request)
        assertEquals(GeminiRetryPolicy.DEFAULT_MAX_ATTEMPTS, http.calls)
        assertSame(last, result)
    }

    @Test
    fun theAttemptLimitFollowsThePolicy() {
        for (max in 1..5) {
            val http = ScriptedHttp(response(500))
            client(http, policy = GeminiRetryPolicy(maxAttempts = max)).post(request)
            assertEquals("max $max", max, http.calls)
        }
    }

    @Test
    fun thePolicyNoneNeverRetries() {
        val http = ScriptedHttp(response(500), ok)
        client(http, policy = GeminiRetryPolicy.NONE).post(request)
        assertEquals(1, http.calls)
    }

    @Test
    fun aConnectTimeoutThatKeepsHappeningUsesAllAttempts() {
        val http = ScriptedHttp(failure(GeminiHttpFailure.CONNECT_TIMEOUT))
        val result = client(http).post(request)
        assertEquals(GeminiRetryPolicy.DEFAULT_MAX_ATTEMPTS, http.calls)
        assertEquals(failure(GeminiHttpFailure.CONNECT_TIMEOUT), result)
    }

    @Test
    fun aReadTimeoutIsRetriedOnlyOnceBecauseItAlreadyCostAWholeReadTimeout() {
        val http = ScriptedHttp(failure(GeminiHttpFailure.TIMEOUT))
        val result = client(http).post(request)
        assertEquals(1 + GeminiRetryPolicy.DEFAULT_MAX_READ_TIMEOUT_RETRIES, http.calls)
        assertEquals(failure(GeminiHttpFailure.TIMEOUT), result)
    }

    @Test
    fun readTimeoutRetriesCanBeSwitchedOff() {
        val http = ScriptedHttp(failure(GeminiHttpFailure.TIMEOUT))
        client(http, policy = GeminiRetryPolicy(maxReadTimeoutRetries = 0)).post(request)
        assertEquals(1, http.calls)
    }

    @Test
    fun everyRetrySendsTheSameRequestObject() {
        val http = ScriptedHttp(response(503), response(503), ok)
        client(http).post(request)
        assertEquals(3, http.requests.size)
        assertTrue(http.requests.all { it === request })
    }

    // ================= exponential backoff =================

    @Test
    fun theWaitsDoubleFromRetryToRetry() {
        val sleeper = RecordingSleeper()
        client(ScriptedHttp(response(503)), sleeper, GeminiRetryPolicy(maxAttempts = 5)).post(request)
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L), sleeper.waits)
    }

    @Test
    fun theBackoffNeverGrowsPastTheMaximum() {
        val sleeper = RecordingSleeper()
        val policy = GeminiRetryPolicy(maxAttempts = 5, baseDelayMillis = 1_000, maxDelayMillis = 3_000)
        client(ScriptedHttp(response(503)), sleeper, policy).post(request)
        assertEquals(listOf(1_000L, 2_000L, 3_000L, 3_000L), sleeper.waits)
    }

    @Test
    fun jitterAddsUpToTheConfiguredFractionOfTheBackoff() {
        val policy = GeminiRetryPolicy(maxAttempts = 3, jitterFraction = 0.2)
        val full = RecordingSleeper()
        client(ScriptedHttp(response(503)), full, policy, random = { 1.0 }).post(request)
        assertEquals(listOf(1_200L, 2_400L), full.waits)
        val none = RecordingSleeper()
        client(ScriptedHttp(response(503)), none, policy, random = { 0.0 }).post(request)
        assertEquals(listOf(1_000L, 2_000L), none.waits)
    }

    @Test
    fun aBrokenRandomSourceCannotPushTheWaitOutOfRange() {
        val sleeper = RecordingSleeper()
        client(ScriptedHttp(response(503)), sleeper, random = { 99.0 }).post(request)
        assertEquals(listOf(1_200L, 2_400L), sleeper.waits)
    }

    @Test
    fun thePolicyComputesBackoffWithoutOverflow() {
        val policy = GeminiRetryPolicy()
        assertEquals(1_000L, policy.backoffMillis(1))
        assertEquals(2_000L, policy.backoffMillis(2))
        assertEquals(4_000L, policy.backoffMillis(3))
        assertEquals(GeminiRetryPolicy.DEFAULT_MAX_DELAY_MILLIS, policy.backoffMillis(50))
        assertEquals(1_000L, policy.backoffMillis(0))
    }

    // ================= Retry-After =================

    @Test
    fun aLongerRetryAfterWaitIsObeyed() {
        val sleeper = RecordingSleeper()
        client(ScriptedHttp(response(429, retryAfterMillis = 5_000), ok), sleeper).post(request)
        assertEquals(listOf(5_000L), sleeper.waits)
    }

    @Test
    fun aShorterRetryAfterDoesNotShortenTheBackoff() {
        val sleeper = RecordingSleeper()
        client(ScriptedHttp(response(429, retryAfterMillis = 200), ok), sleeper).post(request)
        assertEquals(listOf(1_000L), sleeper.waits)
    }

    @Test
    fun aRetryAfterOfZeroUsesTheBackoff() {
        val sleeper = RecordingSleeper()
        client(ScriptedHttp(response(503, retryAfterMillis = 0), ok), sleeper).post(request)
        assertEquals(listOf(1_000L), sleeper.waits)
    }

    @Test
    fun aRetryAfterBeyondTheLimitStopsTheRetriesAtOnce() {
        val answer = response(429, retryAfterMillis = GeminiRetryPolicy.DEFAULT_MAX_WAIT_MILLIS + 1)
        val http = ScriptedHttp(answer, ok)
        val sleeper = RecordingSleeper()
        assertSame(answer, client(http, sleeper).post(request))
        assertEquals(1, http.calls)
        assertTrue(sleeper.waits.isEmpty())
    }

    @Test
    fun theLimitItselfIsStillWaitedFor() {
        val sleeper = RecordingSleeper()
        val answer = response(503, retryAfterMillis = GeminiRetryPolicy.DEFAULT_MAX_WAIT_MILLIS)
        client(ScriptedHttp(answer, ok), sleeper).post(request)
        assertEquals(listOf(GeminiRetryPolicy.DEFAULT_MAX_WAIT_MILLIS), sleeper.waits)
    }

    @Test
    fun theRetryDelayInAGeminiRateLimitBodyIsObeyedWhenThereIsNoHeader() {
        val body = """{"error":{"code":429,"status":"RESOURCE_EXHAUSTED","details":[{"retryDelay":"7s"}]}}"""
        val sleeper = RecordingSleeper()
        client(ScriptedHttp(response(429, body), ok), sleeper).post(request)
        assertEquals(listOf(7_000L), sleeper.waits)
    }

    @Test
    fun theHeaderWinsOverTheBodyDelay() {
        val body = """{"error":{"details":[{"retryDelay":"7s"}]}}"""
        val sleeper = RecordingSleeper()
        client(ScriptedHttp(response(429, body, retryAfterMillis = 3_000), ok), sleeper).post(request)
        assertEquals(listOf(3_000L), sleeper.waits)
    }

    @Test
    fun noWaitFollowsTheLastAttempt() {
        val sleeper = RecordingSleeper()
        client(ScriptedHttp(response(429, retryAfterMillis = 5_000)), sleeper, GeminiRetryPolicy(maxAttempts = 2)).post(request)
        assertEquals(listOf(5_000L), sleeper.waits)
    }

    // ================= Retry-After parsing =================

    @Test
    fun theRetryAfterHeaderIsReadAsSecondsOrADate() {
        assertEquals(120_000L, GeminiRetryAfter.parseHeader("120", 0))
        assertEquals(5_000L, GeminiRetryAfter.parseHeader(" 5 ", 0))
        assertEquals(0L, GeminiRetryAfter.parseHeader("0", 0))
        val date = "Wed, 21 Oct 2015 07:28:00 GMT"
        assertEquals(10_000L, GeminiRetryAfter.parseHeader(date, 1_445_412_480_000L - 10_000L))
        assertEquals(0L, GeminiRetryAfter.parseHeader(date, 1_445_412_480_000L + 60_000L))
    }

    @Test
    fun anUnreadableRetryAfterHeaderIsIgnored() {
        for (value in listOf(null, "", "   ", "abc", "-5", "1.5", "99999999", "tomorrow")) {
            assertNull(value.toString(), GeminiRetryAfter.parseHeader(value, 0))
        }
    }

    @Test
    fun theBodyRetryDelayIsReadInWholeSeconds() {
        assertEquals(37_000L, GeminiRetryAfter.parseBodyRetryDelay("""{"retryDelay": "37s"}"""))
        assertEquals(37_000L, GeminiRetryAfter.parseBodyRetryDelay("""{"retryDelay":"37.5s"}"""))
        assertNull(GeminiRetryAfter.parseBodyRetryDelay("{}"))
        assertNull(GeminiRetryAfter.parseBodyRetryDelay("""{"retryDelay":"soon"}"""))
        assertNull(GeminiRetryAfter.parseBodyRetryDelay("not json"))
    }

    // ================= interruption =================

    @Test
    fun anInterruptedWaitReturnsTheLastResultAndKeepsTheInterruptFlag() {
        val answer = response(503)
        val http = ScriptedHttp(answer, ok)
        val sleeper = GeminiSleeper { throw InterruptedException() }
        try {
            assertSame(answer, client(http, sleeper).post(request))
            assertEquals(1, http.calls)
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted() // clear the flag for other tests
        }
    }

    // ================= policy rules =================

    @Test
    fun badPolicyValuesAreRejected() {
        val bad = listOf<() -> Any>(
            { GeminiRetryPolicy(maxAttempts = 0) },
            { GeminiRetryPolicy(maxAttempts = GeminiRetryPolicy.MAX_ALLOWED_ATTEMPTS + 1) },
            { GeminiRetryPolicy(baseDelayMillis = 0) },
            { GeminiRetryPolicy(baseDelayMillis = 5_000, maxDelayMillis = 1_000) },
            { GeminiRetryPolicy(maxWaitMillis = -1) },
            { GeminiRetryPolicy(jitterFraction = 0.6) },
            { GeminiRetryPolicy(jitterFraction = Double.NaN) },
            { GeminiRetryPolicy(maxReadTimeoutRetries = -1) },
        )
        for (build in bad) {
            try {
                build()
                fail("expected IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test
    fun theDefaultPolicyIsBounded() {
        val policy = GeminiRetryPolicy()
        assertEquals(3, policy.maxAttempts)
        assertTrue(policy.maxAttempts <= GeminiRetryPolicy.MAX_ALLOWED_ATTEMPTS)
        assertEquals(policy, GeminiConfig().retryPolicy)
    }

    // ================= no secrets =================

    @Test
    fun theRetryLayerNeverPutsTheKeyOrTheBodyIntoAnything() {
        val answer = response(503, "error body with $FAKE_KEY")
        val sleeper = RecordingSleeper()
        val result = client(ScriptedHttp(answer), sleeper).post(request)
        assertFalse(result.toString().contains(FAKE_KEY))
        assertFalse(sleeper.waits.toString().contains(FAKE_KEY))
        assertFalse(request.toString().contains(FAKE_KEY))
    }

    companion object {
        const val FAKE_KEY = "fake-test-key-0123456789"
    }
}
