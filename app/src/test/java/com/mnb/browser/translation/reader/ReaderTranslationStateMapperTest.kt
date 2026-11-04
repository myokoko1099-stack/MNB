package com.mnb.browser.translation.reader

import com.mnb.browser.reader.translation.ReaderTranslationFailure
import com.mnb.browser.reader.translation.ReaderTranslationState
import com.mnb.browser.reader.translation.ReaderTranslationUnavailableReason
import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.ContentHash
import com.mnb.browser.translation.TranslationError
import com.mnb.browser.translation.TranslationJob
import com.mnb.browser.translation.TranslationMetadata
import com.mnb.browser.translation.TranslationStatus
import com.mnb.browser.translation.db.StoredTranslation
import org.junit.Assert.assertEquals
import org.junit.Test

/** Core translation data -> reader state (Phase 04-I10-A). */
class ReaderTranslationStateMapperTest {
    private val id = ChapterId("c1")

    private fun job(status: TranslationStatus, completed: Int = 0, total: Int = 4, error: TranslationError? = null) =
        TranslationJob(1L, id, total, 10L, 10L, completed, status, error)

    private fun stored(
        text: String? = "translated",
        status: TranslationStatus = TranslationStatus.COMPLETED,
        mock: Boolean = false,
    ) = StoredTranslation(
        id, text, status, ContentHash.of("orig"),
        TranslationMetadata(providerId = "prov", createdAtMillis = 1L, isMock = mock),
    )

    private fun map(job: TranslationJob?, stored: StoredTranslation? = null, stale: Boolean = false, pos: Int? = null) =
        ReaderTranslationStateMapper.map(job, stored, stale, pos)

    @Test fun nothingKnownIsOriginalOnly() {
        assertEquals(ReaderTranslationState.OriginalOnly, map(null))
    }

    @Test fun pendingJobIsPendingWithPosition() {
        assertEquals(ReaderTranslationState.Pending(2), map(job(TranslationStatus.PENDING), pos = 2))
        assertEquals(ReaderTranslationState.Pending(null), map(job(TranslationStatus.PENDING)))
        assertEquals(ReaderTranslationState.Pending(null), map(job(TranslationStatus.PENDING), pos = 0))
    }

    @Test fun processingJobCarriesProgress() {
        assertEquals(
            ReaderTranslationState.Processing(2, 4),
            map(job(TranslationStatus.PROCESSING, completed = 2, total = 4)),
        )
    }

    @Test fun completedTranslationIsCompleted() {
        assertEquals(
            ReaderTranslationState.Completed("translated", "prov"),
            map(job(TranslationStatus.COMPLETED, 4), stored()),
        )
        assertEquals(ReaderTranslationState.Completed("translated", "prov"), map(null, stored()))
    }

    @Test fun mockTranslationIsNeverShownAsATranslation() {
        assertEquals(
            ReaderTranslationState.Unavailable(ReaderTranslationUnavailableReason.MOCK_ONLY),
            map(job(TranslationStatus.COMPLETED, 4), stored(mock = true)),
        )
    }

    @Test fun staleTranslationFallsBackToOriginal() {
        assertEquals(ReaderTranslationState.OriginalOnly, map(null, stored(), stale = true))
        assertEquals(ReaderTranslationState.OriginalOnly, map(job(TranslationStatus.COMPLETED, 4), stored(), stale = true))
    }

    @Test fun activeJobBeatsAnOlderStoredTranslation() {
        assertEquals(ReaderTranslationState.Processing(1, 4), map(job(TranslationStatus.PROCESSING, 1), stored(), stale = true))
    }

    @Test fun failedJobMapsTheErrorAndRetryability() {
        assertEquals(
            ReaderTranslationState.Failed(ReaderTranslationFailure.PROVIDER, true),
            map(job(TranslationStatus.FAILED, error = TranslationError.PROVIDER_FAILURE)),
        )
        assertEquals(
            ReaderTranslationState.Failed(ReaderTranslationFailure.INVALID_CONTENT, false),
            map(job(TranslationStatus.FAILED, error = TranslationError.EMPTY_TEXT)),
        )
        assertEquals(
            ReaderTranslationState.Failed(ReaderTranslationFailure.INVALID_CONTENT, false),
            map(job(TranslationStatus.FAILED, error = TranslationError.TEXT_TOO_LONG)),
        )
        assertEquals(
            ReaderTranslationState.Failed(ReaderTranslationFailure.UNSUPPORTED_LANGUAGE, false),
            map(job(TranslationStatus.FAILED, error = TranslationError.UNSUPPORTED_SOURCE_LANGUAGE)),
        )
        assertEquals(
            ReaderTranslationState.Failed(ReaderTranslationFailure.UNKNOWN, true),
            map(job(TranslationStatus.FAILED)),
        )
    }

    @Test fun freshStoredTranslationBeatsAFailedJob() {
        assertEquals(
            ReaderTranslationState.Completed("translated", "prov"),
            map(job(TranslationStatus.FAILED), stored()),
        )
    }

    @Test fun cancelledJobIsOriginalOnly() {
        assertEquals(ReaderTranslationState.OriginalOnly, map(job(TranslationStatus.CANCELLED)))
    }

    @Test fun completedJobWithoutStoredTextIsAStorageFailure() {
        val expected = ReaderTranslationState.Failed(ReaderTranslationFailure.STORAGE, true)
        assertEquals(expected, map(job(TranslationStatus.COMPLETED, 4)))
        assertEquals(expected, map(job(TranslationStatus.COMPLETED, 4), stored(text = null, status = TranslationStatus.FAILED)))
    }
}
