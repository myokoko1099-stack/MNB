package com.mnb.browser.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranslationLanguageTest {
    @Test fun fourSourceLanguagesAreSupported() {
        assertEquals(
            setOf("en", "zh", "ko", "ja"),
            SourceLanguage.values().map { it.code }.toSet(),
        )
    }

    @Test fun everySupportedCodeParses() {
        assertEquals(SourceLanguage.ENGLISH, SourceLanguage.fromCode("en"))
        assertEquals(SourceLanguage.CHINESE, SourceLanguage.fromCode("zh"))
        assertEquals(SourceLanguage.KOREAN, SourceLanguage.fromCode("ko"))
        assertEquals(SourceLanguage.JAPANESE, SourceLanguage.fromCode("ja"))
    }

    @Test fun codesAreCaseAndWhitespaceTolerant() {
        assertEquals(SourceLanguage.KOREAN, SourceLanguage.fromCode(" KO "))
    }

    @Test fun unsupportedOrMissingCodesGiveNull() {
        assertNull(SourceLanguage.fromCode("fr"))
        assertNull(SourceLanguage.fromCode("my"))
        assertNull(SourceLanguage.fromCode(""))
        assertNull(SourceLanguage.fromCode(null))
    }

    @Test fun onlyMyanmarIsTheTarget() {
        assertEquals(listOf(TargetLanguage.MYANMAR), TargetLanguage.values().toList())
        assertEquals("my", TargetLanguage.MYANMAR.code)
    }
}
