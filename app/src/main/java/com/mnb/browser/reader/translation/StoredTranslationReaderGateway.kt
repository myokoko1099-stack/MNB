package com.mnb.browser.reader.translation

import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.db.StoredTranslation
import com.mnb.browser.translation.reader.ReaderTranslationStateMapper

/**
 * [ReaderTranslationGateway] that reports what is stored for a chapter (Phase 04-I10-C1). It never
 * starts work. A stored mock translation is Unavailable(MOCK_ONLY); a stale one is OriginalOnly;
 * a storage error is Failed(STORAGE). The two lookups are plain functions so this class does not
 * depend on Room (production passes TranslationRepository::getTranslation / ::isStale).
 */
class StoredTranslationReaderGateway(
    private val getStored: (ChapterId) -> StoredTranslation?,
    private val isStale: (ChapterId) -> Boolean,
) : ReaderTranslationGateway {
    override fun stateOf(identity: ReaderChapterIdentity): ReaderTranslationState = try {
        ReaderTranslationStateMapper.map(
            job = null,
            stored = getStored(identity.chapterId),
            isStale = isStale(identity.chapterId),
        )
    } catch (e: Exception) {
        ReaderTranslationState.Failed(ReaderTranslationFailure.STORAGE, canRetry = true)
    }
}
