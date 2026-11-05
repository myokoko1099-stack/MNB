package com.mnb.browser.translation.gemini

import org.json.JSONException
import org.json.JSONObject

internal sealed interface GeminiParsed {
    data class Text(val text: String) : GeminiParsed

    data class Failed(val error: GeminiError) : GeminiParsed
}

/**
 * Reads Gemini responses. Only fixed [GeminiError] codes come out: response text that is not the
 * translation (error bodies, block reasons) is looked at but never copied into a result.
 */
internal object GeminiResponseParser {

    private val BLOCKED_FINISH_REASONS = setOf(
        "SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII", "IMAGE_SAFETY",
    )

    /** [body] is the body of a 2xx response. */
    fun parse(body: String): GeminiParsed {
        val root = try {
            JSONObject(body)
        } catch (e: JSONException) {
            return GeminiParsed.Failed(GeminiError.MALFORMED_RESPONSE)
        }

        val candidates = root.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            val blockReason = root.optJSONObject("promptFeedback")?.stringOrEmpty("blockReason").orEmpty()
            return GeminiParsed.Failed(
                if (blockReason.isNotEmpty()) GeminiError.CONTENT_BLOCKED else GeminiError.EMPTY_RESPONSE,
            )
        }

        val candidate = candidates.optJSONObject(0) ?: return GeminiParsed.Failed(GeminiError.MALFORMED_RESPONSE)
        val finishReason = candidate.stringOrEmpty("finishReason")
        if (finishReason in BLOCKED_FINISH_REASONS) return GeminiParsed.Failed(GeminiError.CONTENT_BLOCKED)
        if (finishReason == "MAX_TOKENS") return GeminiParsed.Failed(GeminiError.TRUNCATED_RESPONSE)

        val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
        val text = StringBuilder()
        if (parts != null) {
            for (index in 0 until parts.length()) {
                val part = parts.optJSONObject(index) ?: continue
                if (part.optBoolean("thought", false)) continue
                text.append(part.stringOrEmpty("text"))
            }
        }
        val result = text.toString().trim()
        return if (result.isEmpty()) {
            GeminiParsed.Failed(GeminiError.EMPTY_RESPONSE)
        } else {
            GeminiParsed.Text(result)
        }
    }

    /** [body] is the body of a non-2xx response; it is only inspected, never returned. */
    fun errorForStatus(statusCode: Int, body: String): GeminiError = when {
        statusCode == 401 || statusCode == 403 -> GeminiError.API_KEY_REJECTED
        statusCode == 429 -> GeminiError.RATE_LIMITED
        statusCode == 404 -> GeminiError.MODEL_NOT_FOUND
        statusCode == 400 -> if (namesTheKey(body)) GeminiError.API_KEY_REJECTED else GeminiError.BAD_REQUEST
        statusCode in 500..599 -> GeminiError.SERVER_ERROR
        else -> GeminiError.BAD_REQUEST
    }

    /** Gemini answers a bad key with HTTP 400 and an error that names the key. */
    private fun namesTheKey(body: String): Boolean =
        body.contains("API_KEY_INVALID") || body.contains("API key not valid", ignoreCase = true)

    /** Android's org.json turns a JSON null into the text "null"; this returns "" instead. */
    private fun JSONObject.stringOrEmpty(name: String): String =
        if (isNull(name)) "" else optString(name, "")
}
