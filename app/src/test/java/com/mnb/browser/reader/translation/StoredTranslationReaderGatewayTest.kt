package com.mnb.browser.reader.translation

import com.mnb.browser.translation.TranslationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoredTranslationReaderGatewayTest {
    private val identity = BoundChapterIdentityResolver(c1BoundStore()).resolve(c1Chapter(1))!!

    @Test fun storedRealTranslationIsCompleted() {
        val gateway = StoredTranslationReaderGateway({ c1Stored(it) }, { false })
        assertEquals(ReaderTranslationState.Completed("မြန်မာစာသား", "fake-real"), gateway.stateOf(identity))
    }

    @Test fun storedMockTranslationIsNeverCompleted() {
        val gateway = StoredTranslationReaderGateway({ c1Stored(it, isMock = true) }, { false })
        assertEquals(
            ReaderTranslationState.Unavailable(ReaderTranslationUnavailableReason.MOCK_ONLY),
            gateway.stateOf(identity),
        )
    }

    @Test fun staleOrMissingOrUnfinishedIsOriginalOnly() {
        assertEquals(ReaderTranslationState.OriginalOnly, StoredTranslationReaderGateway({ c1Stored(it) }, { true }).stateOf(identity))
        assertEquals(ReaderTranslationState.OriginalOnly, StoredTranslationReaderGateway({ null }, { false }).stateOf(identity))
        val pending = StoredTranslationReaderGateway({ c1Stored(it, text = null, status = TranslationStatus.PENDING) }, { false })
        assertEquals(ReaderTranslationState.OriginalOnly, pending.stateOf(identity))
    }

    @Test fun storageErrorIsFailed() {
        val state = StoredTranslationReaderGateway({ error("db down") }, { false }).stateOf(identity)
        assertTrue(state is ReaderTranslationState.Failed && state.reason == ReaderTranslationFailure.STORAGE)
    }
}
