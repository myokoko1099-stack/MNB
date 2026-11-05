package com.mnb.browser.translation.gemini

/**
 * Settings of the direct Gemini provider (Phase 06-B1). Nothing here is secret: the API key is
 * never part of the configuration and never part of a URL.
 *
 * The base URL must use HTTPS. The model name is limited to a safe character set so it can be put
 * in the request path without changing which endpoint is called.
 */
data class GeminiConfig(
    val model: String = DEFAULT_MODEL,
    val baseUrl: String = BASE_URL,
    val connectTimeoutMillis: Int = DEFAULT_CONNECT_TIMEOUT_MILLIS,
    val readTimeoutMillis: Int = DEFAULT_READ_TIMEOUT_MILLIS,
    val temperature: Double = DEFAULT_TEMPERATURE,
    val retryPolicy: GeminiRetryPolicy = GeminiRetryPolicy(),
) {
    init {
        require(MODEL_PATTERN.matches(model)) { "Invalid Gemini model name" }
        require(baseUrl.startsWith("https://", ignoreCase = true)) { "Gemini base URL must use HTTPS" }
        require(connectTimeoutMillis > 0) { "connectTimeoutMillis must be positive" }
        require(readTimeoutMillis > 0) { "readTimeoutMillis must be positive" }
        require(temperature.isFinite() && temperature in 0.0..2.0) { "temperature must be between 0 and 2" }
    }

    /** The generateContent endpoint of [model]. It never contains the API key. */
    fun generateContentUrl(): String = "${baseUrl.trimEnd('/')}/$API_VERSION/models/$model:generateContent"

    companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com"
        const val API_VERSION = "v1beta"
        const val DEFAULT_MODEL = "gemini-2.5-flash"

        /** The only place the API key is sent: this request header. */
        const val API_KEY_HEADER = "x-goog-api-key"

        const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 15_000
        const val DEFAULT_READ_TIMEOUT_MILLIS = 90_000
        const val DEFAULT_TEMPERATURE = 0.3

        /** Upper limit for a response body. A translated chunk is far smaller than this. */
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024

        private val MODEL_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
    }
}
