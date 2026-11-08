package com.mnb.browser.extraction

import org.json.JSONObject

/** Parses the structured response from [DomReaderScript] without allowing malformed page data to crash the app. */
object ContentParser {
    const val MIN_GLOBAL_DEDUP_LENGTH = 20
    const val MAX_PARAGRAPHS = 400
    const val MAX_PARAGRAPH_LENGTH = 12_000
    const val MAX_TOTAL_CHARACTERS = 180_000

    private val WHITESPACE = Regex("[ \\t\\r\\n\\u000C\\u00A0]+")

    fun parse(
        rawJson: String?,
        pageUrl: String?,
        expectedSessionToken: String? = null,
        expectedDocumentToken: String? = null,
        expectedTabId: String? = null,
        expectedNavGeneration: Long? = null,
    ): ExtractionState {
        if (rawJson.isNullOrBlank() || rawJson == "null") {
            return ExtractionState.Error("Could not read the page content. Please try again.")
        }
        val root = try {
            JSONObject(rawJson)
        } catch (_: Exception) {
            return ExtractionState.Error("The page returned unreadable content.")
        }
        val sessionToken = root.optString("sessionToken", "")
        val documentToken = root.optString("documentToken", "")
        if (expectedSessionToken != null && sessionToken != expectedSessionToken) {
            return ExtractionState.Error("The page changed during extraction. Please try again.")
        }
        if (expectedDocumentToken != null && documentToken != expectedDocumentToken) {
            return ExtractionState.Error("The extracted content belongs to another document.")
        }
        val tabId = root.optString("tabId", "")
        val navGeneration = root.optLong("navGeneration", 0L)
        if (expectedTabId != null && tabId != expectedTabId) {
            return ExtractionState.Error("The extracted content belongs to another tab.")
        }
        if (expectedNavGeneration != null && navGeneration != expectedNavGeneration) {
            return ExtractionState.Error("The page changed during extraction. Please try again.")
        }
        if (root.optString("status", "success") == "error" || root.has("error")) {
            return ExtractionState.Error("Extraction failed: ${root.optString("error", "unknown error")}")
        }

        val url = pageUrl?.takeIf { it.isNotBlank() } ?: root.optString("url", "")
        val pageTitle = normalize(root.optString("pageTitle", ""))
        val array = root.optJSONArray("paragraphs")
        val raw = mutableListOf<ExtractedParagraph>()
        var totalCharacters = 0
        if (array != null) {
            val count = minOf(array.length(), MAX_PARAGRAPHS)
            for (i in 0 until count) {
                val item = array.optJSONObject(i) ?: continue
                val text = if (!item.isNull("originalText")) item.optString("originalText", "") else item.optString("t", "")
                if (text.length > MAX_PARAGRAPH_LENGTH || totalCharacters + text.length > MAX_TOTAL_CHARACTERS) break
                totalCharacters += text.length
                raw += ExtractedParagraph(
                    text = text,
                    headingLevel = item.optInt("headingLevel", item.optInt("h", 0)).coerceIn(0, 6),
                    index = item.optInt("index", -1),
                    textHash = item.optString("textHash", ""),
                    contentKind = item.optString("contentKind", "paragraph").take(32),
                )
            }
        }
        val paragraphs = clean(raw)
        if (paragraphs.isEmpty()) return ExtractionState.Empty(url)

        val chapterTitle = normalize(root.optString("chapterTitle", "")).ifEmpty {
            paragraphs.firstOrNull { it.isHeading && it.text != pageTitle }?.text.orEmpty()
        }.ifEmpty { null }
        val truncation = root.optJSONObject("truncation")
        val truncated = truncation?.optBoolean("truncated", false) == true ||
            (array?.length() ?: 0) > MAX_PARAGRAPHS || totalCharacters >= MAX_TOTAL_CHARACTERS
        val chars = truncation?.optInt("totalCharacters", totalCharacters)?.coerceAtLeast(0) ?: totalCharacters
        val confidence = root.optDouble("chapterConfidence", 0.0).takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.0
        return ExtractionState.Success(
            ExtractedContent(
                pageTitle = pageTitle,
                chapterTitle = chapterTitle,
                url = url,
                paragraphs = paragraphs,
                sessionToken = sessionToken,
                documentToken = documentToken,
                chapterConfidence = confidence,
                truncated = truncated,
                totalExtractedCharacters = chars,
                tabId = tabId,
                navGeneration = navGeneration,
                contentType = root.optString("contentType", "content").take(32),
            ),
        )
    }

    /** Keeps paragraph order, deduplicates repeated long blocks, and preserves short repeated dialogue unless adjacent. */
    fun clean(input: List<ExtractedParagraph>): List<ExtractedParagraph> {
        val seenLong = HashSet<String>()
        val out = ArrayList<ExtractedParagraph>(input.size)
        for (p in input) {
            val text = normalize(p.text)
            if (text.isEmpty()) continue
            if (text.length >= MIN_GLOBAL_DEDUP_LENGTH) {
                if (!seenLong.add(text)) continue
            } else if (out.lastOrNull()?.text == text) {
                continue
            }
            out += p.copy(text = text, headingLevel = p.headingLevel.coerceIn(0, 6))
        }
        return out
    }

    /** Collapses whitespace only; Unicode combining/joining characters are otherwise preserved. */
    fun normalize(text: String): String = text.replace(WHITESPACE, " ").trim()
}
