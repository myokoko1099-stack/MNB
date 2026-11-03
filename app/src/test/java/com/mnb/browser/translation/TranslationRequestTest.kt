package com.mnb.browser.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranslationRequestTest {
    @Test fun validRequestHasNoError() {
        assertNull(TranslationRequest("Hello", SourceLanguage.ENGLISH).validate())
    }

    @Test fun targetDefaultsToMyanmar() {
        assertEquals(TargetLanguage.MYANMAR, TranslationRequest("Hi", SourceLanguage.ENGLISH).targetLanguage)
    }

    @Test fun emptyAndBlankTextAreRejected() {
        for (bad in listOf("", "   ", "\n\t")) {
            assertEquals(bad, TranslationError.EMPTY_TEXT, TranslationRequest(bad, SourceLanguage.JAPANESE).validate())
        }
    }

    @Test fun textAtTheLimitIsAcceptedAndOverIsRejected() {
        val atLimit = "a".repeat(TranslationRequest.MAX_TEXT_LENGTH)
        assertNull(TranslationRequest(atLimit, SourceLanguage.ENGLISH).validate())
        assertEquals(
            TranslationError.TEXT_TOO_LONG,
            TranslationRequest(atLimit + "a", SourceLanguage.ENGLISH).validate(),
        )
    }

    @Test fun cjkTextIsAccepted() {
        assertNull(TranslationRequest("你好，世界", SourceLanguage.CHINESE).validate())
        assertNull(TranslationRequest("안녕하세요", SourceLanguage.KOREAN).validate())
        assertNull(TranslationRequest("こんにちは", SourceLanguage.JAPANESE).validate())
    }
}
