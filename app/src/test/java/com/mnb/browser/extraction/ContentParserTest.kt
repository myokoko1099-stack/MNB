package com.mnb.browser.extraction

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentParserTest {

    private fun json(
        vararg paragraphs: Pair<String, Int>,
        pageTitle: String = "Site - Chapter 5",
        chapterTitle: String = "",
        url: String = "https://json.example/page",
    ): String = JSONObject()
        .put("pageTitle", pageTitle)
        .put("chapterTitle", chapterTitle)
        .put("url", url)
        .put(
            "paragraphs",
            JSONArray().apply { paragraphs.forEach { put(JSONObject().put("t", it.first).put("h", it.second)) } },
        )
        .toString()

    private fun body(vararg texts: String) = json(*texts.map { it to 0 }.toTypedArray())

    private fun success(raw: String, url: String? = "https://example.com/ch5"): ExtractedContent {
        val state = ContentParser.parse(raw, url)
        assertTrue("expected Success but was $state", state is ExtractionState.Success)
        return (state as ExtractionState.Success).content
    }

    private val long1 = "The first long paragraph of the chapter, with enough characters."
    private val long2 = "The second long paragraph of the chapter, also long enough here."
    private val long3 = "The third long paragraph of the chapter, again long enough text."

    // ---- extraction ----

    @Test fun extractsTitleChapterUrlAndText() {
        val c = success(json(long1 to 0, long2 to 0, pageTitle = "Novel - Ch 5", chapterTitle = "Chapter 5"))
        assertEquals("Novel - Ch 5", c.pageTitle)
        assertEquals("Chapter 5", c.chapterTitle)
        assertEquals("https://example.com/ch5", c.url)
        assertEquals(listOf(long1, long2), c.paragraphs.map { it.text })
        assertEquals("$long1\n\n$long2", c.mainText)
    }

    @Test fun webViewUrlIsPreferredAndJsonUrlIsFallback() {
        assertEquals("https://example.com/ch5", success(body(long1), "https://example.com/ch5").url)
        assertEquals("https://json.example/page", success(body(long1), null).url)
        assertEquals("https://json.example/page", success(body(long1), "  ").url)
    }

    @Test fun myanmarTextIsPreservedExactly() {
        val burmese = "မင်္ဂလာပါ။ ဒီနေ့ ကျွန်တော် ခရီးထွက်မယ်။ နေရောင်က လင်းလင်းထိန်ထိန် ရှိနေတယ်။"
        val c = success(body(burmese))
        assertEquals(burmese, c.paragraphs.single().text)
    }

    @Test fun whitespaceIsCollapsedAndTrimmedOnly() {
        val c = success(body("  Hello \t  world\u00A0 again \n "))
        assertEquals("Hello world again", c.paragraphs.single().text)
    }

    @Test fun headingsAreKeptWithTheirLevel() {
        val c = success(json("Chapter 5" to 1, "Part one" to 2, long1 to 0))
        assertEquals(listOf(1, 2, 0), c.paragraphs.map { it.headingLevel })
        assertTrue(c.paragraphs[0].isHeading)
        assertTrue(!c.paragraphs[2].isHeading)
    }

    @Test fun chapterTitleFallsBackToFirstHeadingThatIsNotThePageTitle() {
        val c = success(json("Site - Chapter 5" to 1, "Chapter 5" to 2, long1 to 0, chapterTitle = ""))
        assertEquals("Chapter 5", c.chapterTitle)
    }

    @Test fun chapterTitleIsNullWhenNoneAvailable() {
        assertNull(success(body(long1)).chapterTitle)
    }

    // ---- paragraph ordering ----

    @Test fun paragraphOrderIsPreserved() {
        val texts = (1..50).map { "Paragraph number $it of the chapter, padded so it is long enough." }
        assertEquals(texts, success(body(*texts.toTypedArray())).paragraphs.map { it.text })
    }

    @Test fun orderIsPreservedWhenDuplicatesAreRemoved() {
        val c = success(body(long1, long2, long1, long3, long2))
        assertEquals(listOf(long1, long2, long3), c.paragraphs.map { it.text })
    }

    // ---- duplicate filtering ----

    @Test fun longDuplicateAnywhereIsRemovedKeepingTheFirst() {
        val c = success(body(long1, long2, long3, long1))
        assertEquals(3, c.paragraphs.size)
        assertEquals(long1, c.paragraphs.first().text)
    }

    @Test fun duplicatesAreDetectedAfterWhitespaceNormalisation() {
        val c = success(body(long1, "  " + long1.replace(" ", "   ") + " "))
        assertEquals(1, c.paragraphs.size)
    }

    @Test fun shortRepeatedLinesAreKeptWhenNotAdjacent() {
        val c = success(body("“Yes.”", "He nodded and walked away from the door slowly.", "“Yes.”"))
        assertEquals(3, c.paragraphs.size)
    }

    @Test fun shortRepeatedLinesAreDroppedWhenAdjacent() {
        val c = success(body("“Yes.”", "“Yes.”", "He nodded and walked away from the door slowly."))
        assertEquals(listOf("“Yes.”", "He nodded and walked away from the door slowly."), c.paragraphs.map { it.text })
    }

    @Test fun cleanOnLongBoundaryLengthIsDeduplicated() {
        val exactlyMin = "x".repeat(ContentParser.MIN_GLOBAL_DEDUP_LENGTH)
        val result = ContentParser.clean(
            listOf(ExtractedParagraph(exactlyMin), ExtractedParagraph("between"), ExtractedParagraph(exactlyMin)),
        )
        assertEquals(listOf(exactlyMin, "between"), result.map { it.text })
    }

    // ---- empty content ----

    @Test fun noParagraphsMeansEmpty() {
        val state = ContentParser.parse(json(), "https://example.com/x")
        assertEquals(ExtractionState.Empty("https://example.com/x"), state)
    }

    @Test fun onlyBlankParagraphsMeansEmpty() {
        assertTrue(ContentParser.parse(body("", "   ", "\n\t\u00A0"), "https://example.com/x") is ExtractionState.Empty)
    }

    @Test fun missingParagraphsKeyMeansEmpty() {
        assertTrue(ContentParser.parse("""{"pageTitle":"t","url":"u"}""", "https://example.com/x") is ExtractionState.Empty)
    }

    // ---- errors ----

    @Test fun nullBlankAndLiteralNullAreErrors() {
        assertTrue(ContentParser.parse(null, "u") is ExtractionState.Error)
        assertTrue(ContentParser.parse("   ", "u") is ExtractionState.Error)
        assertTrue(ContentParser.parse("null", "u") is ExtractionState.Error)
    }

    @Test fun malformedJsonIsAnError() {
        assertTrue(ContentParser.parse("{not json", "u") is ExtractionState.Error)
        assertTrue(ContentParser.parse("\"just a string\"", "u") is ExtractionState.Error)
    }

    @Test fun scriptReportedErrorIsSurfaced() {
        val state = ContentParser.parse("""{"error":"boom"}""", "u")
        assertTrue(state is ExtractionState.Error)
        assertTrue((state as ExtractionState.Error).message.contains("boom"))
    }
}
