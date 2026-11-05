package com.mnb.browser.translation.gemini

import com.mnb.browser.security.ApiKeyLookup
import com.mnb.browser.security.ApiKeyProvider
import com.mnb.browser.security.SecureStorageError
import com.mnb.browser.translation.TranslationRequest

/**
 * Calls Google Gemini directly with the user's own API key (Phase 06-B1).
 *
 * This is a stand-alone provider core. It is not a [com.mnb.browser.translation.TranslationProvider]
 * yet and nothing in the app uses it: a later session adds the adapter that maps [GeminiResult] to
 * a TranslationResult and wires it into the translation layer.
 *
 * [translate] blocks (Keystore, file and network work): call it off the main thread, for example
 * from a WorkManager worker. It never throws.
 */
interface GeminiProvider {
    val id: String
    val model: String

    fun translate(request: TranslationRequest): GeminiResult
}

/**
 * The key rules of this class:
 * - The key comes only from [keys] (the Phase 06-A secure storage). There is no other source and
 *   no shared key.
 * - Without a usable key no request is built and nothing is sent.
 * - The key is revealed only while the request headers are built, and is used only as the
 *   `x-goog-api-key` header. It is never in the URL, the body, a log line or an error.
 * - Every failure is a [GeminiError] with a fixed message. No exception message, HTTP error body
 *   or response text is copied into a result.
 */
class DirectGeminiProvider(
    private val keys: ApiKeyProvider,
    private val http: GeminiHttpClient = HttpUrlConnectionGeminiClient(),
    private val config: GeminiConfig = GeminiConfig(),
) : GeminiProvider {

    override val id: String = ID
    override val model: String = config.model

    override fun translate(request: TranslationRequest): GeminiResult {
        if (request.validate() != null) return GeminiResult.Failure(GeminiError.INVALID_REQUEST)

        val key = when (val lookup = lookupKey()) {
            is ApiKeyLookup.Available -> lookup.key
            is ApiKeyLookup.Missing -> return GeminiResult.Failure(GeminiError.MISSING_API_KEY)
            is ApiKeyLookup.Unavailable -> return GeminiResult.Failure(GeminiError.API_KEY_UNAVAILABLE)
        }

        return try {
            val body = GeminiPromptBuilder.requestBody(request, config)
            val httpRequest = GeminiHttpRequest(
                url = config.generateContentUrl(),
                headers = mapOf(
                    GeminiConfig.API_KEY_HEADER to key.reveal(),
                    "Content-Type" to "application/json; charset=utf-8",
                    "Accept" to "application/json",
                ),
                body = body,
            )
            toResult(http.post(httpRequest))
        } catch (e: Exception) {
            // No message or cause is kept: it could mention the key or the text.
            GeminiResult.Failure(GeminiError.INTERNAL)
        }
    }

    /** Never throws. An [Error] from the storage layer is also treated as "unavailable". */
    private fun lookupKey(): ApiKeyLookup = try {
        keys.lookup()
    } catch (t: Throwable) {
        ApiKeyLookup.Unavailable(SecureStorageError.STORAGE_FAILURE)
    }

    private fun toResult(result: GeminiHttpResult): GeminiResult = when (result) {
        is GeminiHttpResult.Failure -> GeminiResult.Failure(errorFor(result.failure))
        is GeminiHttpResult.Response -> {
            val response = result.response
            if (response.statusCode in 200..299) {
                when (val parsed = GeminiResponseParser.parse(response.body)) {
                    is GeminiParsed.Text -> GeminiResult.Success(parsed.text, config.model)
                    is GeminiParsed.Failed -> GeminiResult.Failure(parsed.error)
                }
            } else {
                GeminiResult.Failure(GeminiResponseParser.errorForStatus(response.statusCode, response.body))
            }
        }
    }

    private fun errorFor(failure: GeminiHttpFailure): GeminiError = when (failure) {
        GeminiHttpFailure.TIMEOUT -> GeminiError.TIMEOUT
        GeminiHttpFailure.NO_CONNECTION -> GeminiError.NETWORK_UNAVAILABLE
        GeminiHttpFailure.IO -> GeminiError.NETWORK_UNAVAILABLE
        GeminiHttpFailure.TLS -> GeminiError.SECURE_CONNECTION_FAILED
        GeminiHttpFailure.TOO_LARGE -> GeminiError.RESPONSE_TOO_LARGE
        GeminiHttpFailure.INSECURE_URL -> GeminiError.INTERNAL
    }

    companion object {
        /** Id for the later translation adapter. It must be the same in every place that names the provider. */
        const val ID = "gemini-direct"
    }
}
