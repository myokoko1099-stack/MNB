package com.mnb.browser.reader.translation

import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.ContentHash
import com.mnb.browser.translation.TranslationMetadata
import com.mnb.browser.translation.TranslationStatus
import com.mnb.browser.translation.db.StoredTranslation
import com.mnb.browser.translation.reader.ReaderTranslationStateMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the reader shows for each translation state (Phase 04-I10-B). */
class ReaderTranslationDisplayTest {
    private fun of(state: ReaderTranslationState?) = ReaderTranslationDisplay.of(state)

    private val completed = ReaderTranslationState.Completed("ပထမစာပိုဒ်\n\nဒုတိယစာပိုဒ်\r\nတတိယစာပိုဒ်\n", "real")

    // ---- one test per state --------------------------------------------------------------

    @Test fun noStateShowsNoControls() {
        val d = of(null)
        assertFalse(d.showControls)
        assertNull(d.message)
        assertFalse(d.canShowTranslation)
    }

    @Test fun originalOnlyShowsOnlyTheOriginal() {
        val d = of(ReaderTranslationState.OriginalOnly)
        assertFalse(d.showControls)
        assertNull(d.message)
        assertFalse(d.canShowTranslation)
        assertEquals(ReaderViewMode.ORIGINAL, d.effectiveMode(ReaderViewMode.TRANSLATION))
    }

    @Test fun pendingShowsTheWaitingMessage() {
        val d = of(ReaderTranslationState.Pending())
        assertTrue(d.showControls)
        assertEquals("ဘာသာပြန်ရန် စောင့်ဆိုင်းနေသည်", d.message)
        assertNull(d.detail)
        assertFalse(d.showProgress)
        assertFalse(d.canShowTranslation)
    }

    @Test fun pendingShowsTheQueuePositionWhenKnown() {
        val d = of(ReaderTranslationState.Pending(3))
        assertEquals("ဘာသာပြန်ရန် စောင့်ဆိုင်းနေသည်", d.message)
        assertEquals("တန်းစီအမှတ် 3", d.detail)
    }

    @Test fun processingShowsProgress() {
        val d = of(ReaderTranslationState.Processing(1, 4))
        assertTrue(d.showControls)
        assertTrue(d.showProgress)
        assertEquals(0.25f, d.progress!!, 0.0001f)
        assertEquals("ဘာသာပြန်နေသည် (1/4)", d.message)
        assertFalse(d.canShowTranslation)
    }

    @Test fun processingWithUnknownTotalIsIndeterminate() {
        val d = of(ReaderTranslationState.Processing(0, 0))
        assertTrue(d.showProgress)
        assertNull(d.progress)
        assertEquals("ဘာသာပြန်နေသည်", d.message)
    }

    @Test fun completedOffersTheTranslationSplitIntoParagraphs() {
        val d = of(completed)
        assertTrue(d.showControls)
        assertTrue(d.canShowTranslation)
        assertEquals(listOf("ပထမစာပိုဒ်", "ဒုတိယစာပိုဒ်", "တတိယစာပိုဒ်"), d.translatedParagraphs)
        assertNull(d.message)
        assertFalse(d.showProgress)
    }

    @Test fun failedShowsAnErrorAndNoTranslation() {
        val retryable = of(ReaderTranslationState.Failed(ReaderTranslationFailure.PROVIDER, canRetry = true))
        assertTrue(retryable.showControls)
        assertEquals("ဘာသာပြန်ခြင်း မအောင်မြင်ပါ", retryable.message)
        assertTrue(retryable.detail!!.contains("ထပ်ကြိုးစားနိုင်ပါသည်"))
        assertFalse(retryable.canShowTranslation)

        val permanent = of(ReaderTranslationState.Failed(ReaderTranslationFailure.INVALID_CONTENT, canRetry = false))
        assertEquals("ဘာသာပြန်ခြင်း မအောင်မြင်ပါ", permanent.message)
        assertFalse(permanent.detail!!.contains("ထပ်ကြိုးစားနိုင်ပါသည်"))
        assertFalse(permanent.canShowTranslation)
    }

    @Test fun everyFailureReasonHasAMessage() {
        for (reason in ReaderTranslationFailure.values()) {
            val d = of(ReaderTranslationState.Failed(reason, canRetry = false))
            assertEquals("ဘာသာပြန်ခြင်း မအောင်မြင်ပါ", d.message)
            assertTrue(d.detail!!.isNotBlank())
        }
    }

    @Test fun unavailableSaysTheTranslationCannotBeProvided() {
        for (reason in ReaderTranslationUnavailableReason.values()) {
            val d = of(ReaderTranslationState.Unavailable(reason))
            assertTrue(d.showControls)
            assertEquals("ဘာသာပြန်မရနိုင်ပါ", d.message)
            assertTrue(d.detail!!.isNotBlank())
            assertFalse(d.canShowTranslation)
            assertTrue(d.translatedParagraphs.isEmpty())
        }
    }

    @Test fun defaultReaderSetupShowsUnavailableAndNoInventedTranslation() {
        // Default holder: NoChapterIdentityResolver, so the chapter is Unavailable(NO_CHAPTER_IDENTITY).
        val d = of(ReaderTranslationState.Unavailable(ReaderTranslationUnavailableReason.NO_CHAPTER_IDENTITY))
        assertEquals("ဘာသာပြန်မရနိုင်ပါ", d.message)
        assertTrue(d.translatedParagraphs.isEmpty())
        assertEquals(ReaderViewMode.ORIGINAL, d.effectiveMode(ReaderViewMode.TRANSLATION))
    }

    // ---- mock output is never shown ---------------------------------------------------------

    @Test fun mockTranslationIsNeverDisplayedAsATranslation() {
        val mock = StoredTranslation(
            ChapterId("c"), "mock translated text", TranslationStatus.COMPLETED, ContentHash.of("orig"),
            TranslationMetadata(providerId = "mock", createdAtMillis = 1L, isMock = true),
        )
        val state = ReaderTranslationStateMapper.map(null, mock)
        val d = of(state)
        assertEquals(ReaderTranslationState.Unavailable(ReaderTranslationUnavailableReason.MOCK_ONLY), state)
        assertFalse(d.canShowTranslation)
        assertTrue(d.translatedParagraphs.isEmpty())
        assertEquals(ReaderViewMode.ORIGINAL, d.effectiveMode(ReaderViewMode.TRANSLATION))
    }

    @Test fun realCompletedTranslationFromTheMapperIsDisplayed() {
        val real = StoredTranslation(
            ChapterId("c"), "real text", TranslationStatus.COMPLETED, ContentHash.of("orig"),
            TranslationMetadata(providerId = "real", createdAtMillis = 1L, isMock = false),
        )
        assertEquals(listOf("real text"), of(ReaderTranslationStateMapper.map(null, real)).translatedParagraphs)
    }

    // ---- original / translation switching ------------------------------------------------------

    @Test fun originalIsAlwaysAvailable() {
        val states = listOf(
            null,
            ReaderTranslationState.OriginalOnly,
            ReaderTranslationState.Pending(1),
            ReaderTranslationState.Processing(1, 2),
            completed,
            ReaderTranslationState.Failed(ReaderTranslationFailure.UNKNOWN, true),
            ReaderTranslationState.Unavailable(ReaderTranslationUnavailableReason.NOT_CONFIGURED),
        )
        for (s in states) assertEquals(ReaderViewMode.ORIGINAL, of(s).effectiveMode(ReaderViewMode.ORIGINAL))
    }

    @Test fun translationIsShownOnlyWhenCompletedAndRequested() {
        val d = of(completed)
        assertEquals(ReaderViewMode.TRANSLATION, d.effectiveMode(ReaderViewMode.TRANSLATION))
        assertEquals(ReaderViewMode.ORIGINAL, d.effectiveMode(ReaderViewMode.ORIGINAL))
    }

    @Test fun translationRequestFallsBackToOriginalForEveryOtherState() {
        val others = listOf(
            null,
            ReaderTranslationState.OriginalOnly,
            ReaderTranslationState.Pending(1),
            ReaderTranslationState.Processing(1, 2),
            ReaderTranslationState.Failed(ReaderTranslationFailure.PROVIDER, true),
            ReaderTranslationState.Unavailable(ReaderTranslationUnavailableReason.MOCK_ONLY),
        )
        for (s in others) assertEquals(ReaderViewMode.ORIGINAL, of(s).effectiveMode(ReaderViewMode.TRANSLATION))
    }

    @Test fun viewReturnsToOriginalWhenTheTranslationStopsBeingCompleted() {
        val requested = ReaderViewMode.TRANSLATION
        assertEquals(ReaderViewMode.TRANSLATION, of(completed).effectiveMode(requested))
        // the reader moved to another chapter: it starts at OriginalOnly
        assertEquals(ReaderViewMode.ORIGINAL, of(ReaderTranslationState.OriginalOnly).effectiveMode(requested))
    }
}
