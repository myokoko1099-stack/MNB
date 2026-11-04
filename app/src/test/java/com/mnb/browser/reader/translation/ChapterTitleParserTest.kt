package com.mnb.browser.reader.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterTitleParserTest {
    @Test fun english() = assertEquals(12, ChapterTitleParser.parse("Chapter 12"))
    @Test fun englishCaseAndTitle() = assertEquals(12, ChapterTitleParser.parse("  CHAPTER 12: The Return "))
    @Test fun chinese() = assertEquals(12, ChapterTitleParser.parse("第12章 归来"))
    @Test fun korean() = assertEquals(12, ChapterTitleParser.parse("제12화 귀환"))
    @Test fun japanese() = assertEquals(12, ChapterTitleParser.parse("第12話 帰還"))
    @Test fun zero() = assertEquals(0, ChapterTitleParser.parse("Chapter 0"))

    @Test fun nullAndBlank() {
        assertNull(ChapterTitleParser.parse(null))
        assertNull(ChapterTitleParser.parse("   "))
    }

    @Test fun noNumber() {
        assertNull(ChapterTitleParser.parse("Prologue"))
        assertNull(ChapterTitleParser.parse("Chapter"))
    }

    @Test fun otherWordingIsNotGuessed() {
        assertNull(ChapterTitleParser.parse("Ch. 12"))
        assertNull(ChapterTitleParser.parse("Episode 12"))
        assertNull(ChapterTitleParser.parse("12. The Return"))
    }

    @Test fun chineseNumeralsAreNotGuessed() = assertNull(ChapterTitleParser.parse("第十二章"))

    @Test fun rangesDecimalsAndSuffixesAreAmbiguous() {
        assertNull(ChapterTitleParser.parse("Chapter 12-13"))
        assertNull(ChapterTitleParser.parse("Chapter 12 & 13"))
        assertNull(ChapterTitleParser.parse("Chapter 12.5"))
        assertNull(ChapterTitleParser.parse("Chapter 12a"))
        assertNull(ChapterTitleParser.parse("第12-13章"))
    }

    @Test fun prefixBeforePatternIsAmbiguous() = assertNull(ChapterTitleParser.parse("Volume 2 Chapter 12"))

    @Test fun twoPatternsAreAmbiguous() {
        assertNull(ChapterTitleParser.parse("Chapter 12 Chapter 13"))
        assertNull(ChapterTitleParser.parse("Chapter 12 第13章"))
    }

    @Test fun tooManyDigitsOrFullWidthDigits() {
        assertNull(ChapterTitleParser.parse("Chapter 1234567"))
        assertNull(ChapterTitleParser.parse("第１２章"))
    }
}
