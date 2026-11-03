package com.mnb.browser.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationStatusTest {
    @Test fun hasTheFiveExpectedStatuses() {
        assertEquals(
            listOf("PENDING", "PROCESSING", "COMPLETED", "FAILED", "CANCELLED"),
            TranslationStatus.values().map { it.name },
        )
    }

    @Test fun finishedStatuses() {
        assertFalse(TranslationStatus.PENDING.isFinished)
        assertFalse(TranslationStatus.PROCESSING.isFinished)
        assertTrue(TranslationStatus.COMPLETED.isFinished)
        assertTrue(TranslationStatus.FAILED.isFinished)
        assertTrue(TranslationStatus.CANCELLED.isFinished)
    }

    @Test fun everyStatusRoundTripsByName() {
        for (s in TranslationStatus.values()) assertEquals(s, TranslationStatus.fromName(s.name))
    }

    @Test fun unknownOrMissingStatusNamesGiveNull() {
        assertNull(TranslationStatus.fromName("DONE"))
        assertNull(TranslationStatus.fromName("completed"))
        assertNull(TranslationStatus.fromName(""))
        assertNull(TranslationStatus.fromName(null))
    }

    @Test fun styleDefaultsAndParsing() {
        assertEquals(TranslationStyle.LITERARY, NovelTranslationInfo(
            NovelId("n"), "T", SourceLanguage.ENGLISH, 0L,
        ).style)
        assertEquals(TranslationStyle.LITERAL, TranslationStyle.fromName("LITERAL"))
        assertNull(TranslationStyle.fromName("fancy"))
        assertNull(TranslationStyle.fromName(null))
    }
}
