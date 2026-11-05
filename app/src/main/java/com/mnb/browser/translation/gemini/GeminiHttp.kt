package com.mnb.browser.translation.gemini

/**
 * One HTTPS POST to Gemini. [headers] holds the API key, so [toString] shows nothing but the type:
 * a request that ends up in a log line, an exception or a debugger string leaks neither the key
 * nor the text.
 */
class GeminiHttpRequest(
    val url: String,
    val headers: Map<String, String>,
    val body: String,
) {
    override fun toString(): String = "GeminiHttpRequest(***)"
}

/**
 * What came back. [toString] leaves the body out (an error body is never shown or logged).
 * [retryAfterMillis] is the wait the server asked for with a `Retry-After` header (Phase 06-B3),
 * or null when there was none.
 */
class GeminiHttpResponse(
    val statusCode: Int,
    val body: String,
    val retryAfterMillis: Long? = null,
) {
    override fun toString(): String = "GeminiHttpResponse(status=$statusCode)"
}

/** Why no HTTP response was received. Codes only: no exception message is kept. */
enum class GeminiHttpFailure {
    /** The connection could not be opened in time (connect timeout). */
    CONNECT_TIMEOUT,

    /** The connection was open but the answer did not arrive in time (read timeout). */
    TIMEOUT,
    NO_CONNECTION,
    TLS,
    TOO_LARGE,

    /** The URL is not HTTPS. Nothing was sent. */
    INSECURE_URL,
    IO,
}

sealed interface GeminiHttpResult {
    data class Response(val response: GeminiHttpResponse) : GeminiHttpResult

    data class Failure(val failure: GeminiHttpFailure) : GeminiHttpResult
}

/**
 * The network layer of the Gemini provider. It is an interface so tests (and a later phase) can
 * replace it. [post] blocks: call it off the main thread. It must not throw for network problems;
 * it returns a [GeminiHttpResult.Failure].
 */
fun interface GeminiHttpClient {
    fun post(request: GeminiHttpRequest): GeminiHttpResult
}
