package com.mnb.browser.translation.gemini

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * How often and how long the direct Gemini provider retries one request (Phase 06-B3). It is a
 * small, bounded policy for a single call: there is no queue and nothing is kept between calls.
 *
 * - [maxAttempts] counts the first try, so 3 means at most 2 retries.
 * - The wait before retry n is [baseDelayMillis] * 2^(n-1), at most [maxDelayMillis], plus up to
 *   [jitterFraction] of it (exponential backoff).
 * - A `Retry-After` wait (header, or the `retryDelay` Gemini puts in a 429 body) is obeyed when it
 *   is longer than the backoff, but a wait longer than [maxWaitMillis] is not waited for: the
 *   failure is returned at once and the caller (for example the background job) tries again later.
 * - A read timeout means a whole [GeminiConfig.readTimeoutMillis] was already spent, so it is
 *   retried at most [maxReadTimeoutRetries] times.
 */
data class GeminiRetryPolicy(
    val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    val baseDelayMillis: Long = DEFAULT_BASE_DELAY_MILLIS,
    val maxDelayMillis: Long = DEFAULT_MAX_DELAY_MILLIS,
    val maxWaitMillis: Long = DEFAULT_MAX_WAIT_MILLIS,
    val jitterFraction: Double = DEFAULT_JITTER_FRACTION,
    val maxReadTimeoutRetries: Int = DEFAULT_MAX_READ_TIMEOUT_RETRIES,
) {
    init {
        require(maxAttempts in 1..MAX_ALLOWED_ATTEMPTS) { "maxAttempts must be between 1 and $MAX_ALLOWED_ATTEMPTS" }
        require(baseDelayMillis > 0) { "baseDelayMillis must be positive" }
        require(maxDelayMillis >= baseDelayMillis) { "maxDelayMillis must not be below baseDelayMillis" }
        require(maxWaitMillis >= 0) { "maxWaitMillis must not be negative" }
        require(jitterFraction.isFinite() && jitterFraction in 0.0..0.5) { "jitterFraction must be between 0 and 0.5" }
        require(maxReadTimeoutRetries >= 0) { "maxReadTimeoutRetries must not be negative" }
    }

    /** The backoff wait before retry number [retryNumber] (1 = the first retry), without jitter. */
    fun backoffMillis(retryNumber: Int): Long {
        val shift = (retryNumber - 1).coerceIn(0, MAX_SHIFT)
        return (baseDelayMillis shl shift).coerceAtMost(maxDelayMillis)
    }

    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 3
        const val DEFAULT_BASE_DELAY_MILLIS = 1_000L
        const val DEFAULT_MAX_DELAY_MILLIS = 16_000L
        const val DEFAULT_MAX_WAIT_MILLIS = 20_000L
        const val DEFAULT_JITTER_FRACTION = 0.2
        const val DEFAULT_MAX_READ_TIMEOUT_RETRIES = 1
        const val MAX_ALLOWED_ATTEMPTS = 5
        private const val MAX_SHIFT = 20

        /** One attempt, no retry. */
        val NONE = GeminiRetryPolicy(maxAttempts = 1)
    }
}

/** Waits between two attempts. A function interface so tests do not really sleep. */
fun interface GeminiSleeper {
    /** May throw [InterruptedException]. */
    fun sleep(millis: Long)
}

/**
 * Wraps a [GeminiHttpClient] and repeats a request that failed for a reason that can go away by
 * itself, within [GeminiRetryPolicy]. It repeats the identical request object, so the key is never
 * copied, read again or shown, and it never logs.
 *
 * Retried: connect and read timeouts, no connection, other I/O failures, HTTP 429 that is a rate
 * limit (not a used-up quota), and HTTP 500 / 502 / 503 / 504.
 * Not retried: success, every other status (400, 401, 403, 404, a used-up quota ...), TLS errors,
 * oversized responses and anything else that a second try cannot fix.
 *
 * [post] blocks (it sleeps between tries): call it off the main thread. If the thread is
 * interrupted while waiting, the last result is returned at once.
 */
class RetryingGeminiHttpClient(
    private val delegate: GeminiHttpClient,
    private val policy: GeminiRetryPolicy = GeminiRetryPolicy(),
    private val sleeper: GeminiSleeper = GeminiSleeper { Thread.sleep(it) },
    private val random: () -> Double = { Math.random() },
) : GeminiHttpClient {

    override fun post(request: GeminiHttpRequest): GeminiHttpResult {
        var attempt = 1
        var readTimeouts = 0
        while (true) {
            val result = delegate.post(request)
            if (attempt >= policy.maxAttempts || !isRetryable(result)) return result
            if (isReadTimeout(result)) {
                if (readTimeouts >= policy.maxReadTimeoutRetries) return result
                readTimeouts++
            }
            val wait = waitMillis(result, retryNumber = attempt) ?: return result
            if (wait > 0) {
                try {
                    sleeper.sleep(wait)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return result
                }
            }
            attempt++
        }
    }

    private fun isRetryable(result: GeminiHttpResult): Boolean = when (result) {
        is GeminiHttpResult.Failure -> when (result.failure) {
            GeminiHttpFailure.CONNECT_TIMEOUT,
            GeminiHttpFailure.TIMEOUT,
            GeminiHttpFailure.NO_CONNECTION,
            GeminiHttpFailure.IO,
            -> true
            GeminiHttpFailure.TLS,
            GeminiHttpFailure.TOO_LARGE,
            GeminiHttpFailure.INSECURE_URL,
            -> false
        }
        is GeminiHttpResult.Response -> {
            val status = result.response.statusCode
            when {
                status == 429 ->
                    GeminiResponseParser.errorForStatus(status, result.response.body) == GeminiError.RATE_LIMITED
                else -> status in RETRYABLE_SERVER_STATUSES
            }
        }
    }

    private fun isReadTimeout(result: GeminiHttpResult): Boolean =
        result is GeminiHttpResult.Failure && result.failure == GeminiHttpFailure.TIMEOUT

    /** The wait before the next try, or null when the server asked for a wait that is too long. */
    private fun waitMillis(result: GeminiHttpResult, retryNumber: Int): Long? {
        val backoff = policy.backoffMillis(retryNumber)
        val jitter = (backoff * policy.jitterFraction * random().coerceIn(0.0, 1.0)).toLong()
        val computed = backoff + jitter

        val asked = (result as? GeminiHttpResult.Response)?.response?.let { response ->
            response.retryAfterMillis ?: GeminiRetryAfter.parseBodyRetryDelay(response.body)
        } ?: return computed
        if (asked > policy.maxWaitMillis) return null
        return maxOf(computed, asked)
    }

    private companion object {
        val RETRYABLE_SERVER_STATUSES = setOf(500, 502, 503, 504)
    }
}

/** Reads how long the server asked the client to wait. Pure; never throws. */
internal object GeminiRetryAfter {
    private const val MAX_SECONDS = 24L * 60 * 60
    private val BODY_RETRY_DELAY = Regex("\"retryDelay\"\\s*:\\s*\"(\\d{1,6})(?:\\.\\d+)?s\"")

    /** `Retry-After` is either a number of seconds or an HTTP date. Null when missing or unreadable. */
    fun parseHeader(value: String?, nowMillis: Long): Long? {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return null
        if (text.all { it in '0'..'9' }) {
            if (text.length > 7) return null
            return (text.toLong().coerceAtMost(MAX_SECONDS)) * 1000
        }
        return try {
            val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
            format.timeZone = TimeZone.getTimeZone("GMT")
            val at = format.parse(text)?.time ?: return null
            (at - nowMillis).coerceIn(0L, MAX_SECONDS * 1000)
        } catch (e: Exception) {
            null
        }
    }

    /** Gemini's 429 body can carry `"retryDelay": "37s"`. Null when absent. */
    fun parseBodyRetryDelay(body: String): Long? {
        val seconds = BODY_RETRY_DELAY.find(body)?.groupValues?.get(1)?.toLongOrNull() ?: return null
        return seconds.coerceAtMost(MAX_SECONDS) * 1000
    }
}
