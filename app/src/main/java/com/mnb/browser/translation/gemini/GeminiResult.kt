package com.mnb.browser.translation.gemini

/**
 * Why a Gemini call produced no translation. Every code has one fixed [message]: nothing from an
 * exception, an HTTP error body or the stored key is ever copied into it, so an error is always
 * safe to log or show.
 */
enum class GeminiError(val message: String, val retryable: Boolean) {
    /** The text to translate is empty or too long. Nothing was sent. */
    INVALID_REQUEST("The text to translate is empty or too long.", false),

    /** No key is saved. Nothing was sent. */
    MISSING_API_KEY("No Gemini API key is saved. Add one in Settings.", false),

    /** A key is saved but cannot be read right now. Nothing was sent. */
    API_KEY_UNAVAILABLE("The saved Gemini API key cannot be read. Open Settings and save it again.", false),

    /** Gemini refused the key (HTTP 401 / 403, or a 400 that names the key). */
    API_KEY_REJECTED("Gemini did not accept the API key. Check the key in Settings.", false),

    /** HTTP 429: too many requests or the quota is used up. */
    RATE_LIMITED("Gemini reports too many requests or no quota left. Try again later.", true),

    /** HTTP 404: the model name is not known to Gemini. */
    MODEL_NOT_FOUND("The Gemini model was not found.", false),

    /** Any other HTTP 4xx (or an unexpected status). */
    BAD_REQUEST("Gemini could not process this request.", false),

    /** HTTP 5xx. */
    SERVER_ERROR("Gemini is having problems right now. Try again later.", true),

    NETWORK_UNAVAILABLE("Could not reach Gemini. Check the internet connection.", true),

    TIMEOUT("Gemini took too long to answer.", true),

    SECURE_CONNECTION_FAILED("A secure connection to Gemini could not be made.", false),

    RESPONSE_TOO_LARGE("Gemini sent a response that is too large.", false),

    /** The response was not the JSON that was expected. */
    MALFORMED_RESPONSE("Gemini sent an answer that MNB could not read.", false),

    /** A valid response without any text. */
    EMPTY_RESPONSE("Gemini returned no text.", true),

    /** Gemini declined to translate this text (safety or content policy). */
    CONTENT_BLOCKED("Gemini blocked this text.", false),

    /** The answer was cut off before the end, so it is not a complete translation. */
    TRUNCATED_RESPONSE("Gemini's answer was cut short.", false),

    /** Something unexpected inside the provider. */
    INTERNAL("An unexpected error happened in the Gemini provider.", false),
}

/** Outcome of one Gemini translation call. Never thrown; always returned. */
sealed interface GeminiResult {
    data class Success(val translatedText: String, val model: String) : GeminiResult

    data class Failure(val error: GeminiError) : GeminiResult
}
