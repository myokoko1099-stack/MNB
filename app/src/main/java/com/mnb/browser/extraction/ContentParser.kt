package com.mnb.browser.extraction

import org.json.JSONObject

/**
 * Turns the raw JSON produced by [ExtractionScript] into an [ExtractionState].
 * Pure logic: no WebView, no UI. Owns whitespace clean-up and duplicate filtering.
 */
object ContentParser {

    /** Paragraphs at least this long are de-duplicated anywhere in the page. */
    const val MIN_GLOBAL_DEDUP_LENGTH = 20

    private val WHITESPACE = Regex("[ \\t\\r\\n\\u000C\\u00A0]+")

    fun parse(rawJson: String?, pageUrl: String?): ExtractionState {
        if (rawJson.isNullOrBlank() || rawJson == "null") {
            return ExtractionState.Error("Could not read the page content. Please try again.")
        }
        val root = try {
            JSONObject(rawJson)
        } catch (e: Exception) {
            return ExtractionState.Error("The page returned unreadable content.")
        }
        if (root.has("error")) {
            return ExtractionState.Error("Extraction failed: ${root.optString("error")}")
        }

        val url = pageUrl?.takeIf { it.isNotBlank() } ?: root.optString("url", "")
        val pageTitle = if (root.isNull("pageTitle")) "" else normalize(root.optString("pageTitle", ""))

        val raw = mutableListOf<ExtractedParagraph>()
        val array = root.optJSONArray("paragraphs")
        if (array != null) {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                if (item.isNull("t")) continue
                raw += ExtractedParagraph(item.optString("t", ""), item.optInt("h", 0).coerceIn(0, 6))
            }
        }

        val paragraphs = clean(raw)
        if (paragraphs.isEmpty()) return ExtractionState.Empty(url)

        val jsChapter = if (root.isNull("chapterTitle")) "" else normalize(root.optString("chapterTitle", ""))
        val chapterTitle = jsChapter.ifEmpty {
            paragraphs.firstOrNull { it.isHeading && it.text != pageTitle }?.text.orEmpty()
        }.ifEmpty { null }

        return ExtractionState.Success(ExtractedContent(pageTitle, chapterTitle, url, paragraphs))
    }

    /**
     * Normalises whitespace, drops blanks and removes duplicates while keeping the original
     * order. Long paragraphs are unique page-wide; short ones (e.g. repeated dialogue such as
     * "Yes.") are only dropped when they directly repeat the previous paragraph.
     */
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
            out += ExtractedParagraph(text, p.headingLevel)
        }
        return out
    }

    /** Collapses runs of whitespace and trims. Does not touch any other character. */
    fun normalize(text: String): String = text.replace(WHITESPACE, " ").trim()
}
