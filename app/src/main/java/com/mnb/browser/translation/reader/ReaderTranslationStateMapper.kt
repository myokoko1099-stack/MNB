package com.mnb.browser.translation.reader

import com.mnb.browser.reader.translation.ReaderTranslationFailure
import com.mnb.browser.reader.translation.ReaderTranslationState
import com.mnb.browser.reader.translation.ReaderTranslationUnavailableReason
import com.mnb.browser.translation.TranslationError
import com.mnb.browser.translation.TranslationJob
import com.mnb.browser.translation.TranslationStatus
import com.mnb.browser.translation.db.StoredTranslation

/**
 * Turns what the translation core knows about one chapter (its latest job and stored translation)
 * into the [ReaderTranslationState] the reader understands (Phase 04-I10-A). Pure: no database,
 * no queue, no provider, so it can sit behind any [com.mnb.browser.reader.translation.ReaderTranslationGateway].
 *
 * Order of the rules:
 *  1. A waiting or running job decides: Pending / Processing.
 *  2. Otherwise an up-to-date COMPLETED translation: Completed, but placeholder (mock) output is
 *     Unavailable(MOCK_ONLY) and is never shown as a translation.
 *  3. Otherwise a FAILED job: Failed.
 *  4. A COMPLETED job without stored text is a storage problem: Failed(STORAGE).
 *  5. Anything else (no job, cancelled job, outdated translation): OriginalOnly.
 */
object ReaderTranslationStateMapper {
    fun map(
        job: TranslationJob?,
        stored: StoredTranslation?,
        isStale: Boolean = false,
        queuePosition: Int? = null,
    ): ReaderTranslationState {
        if (job != null) {
            when (job.status) {
                TranslationStatus.PENDING -> return ReaderTranslationState.Pending(queuePosition?.takeIf { it >= 1 })
                TranslationStatus.PROCESSING ->
                    return ReaderTranslationState.Processing(job.completedChunks, job.totalChunks)
                else -> Unit
            }
        }

        val text = stored?.takeIf { it.status == TranslationStatus.COMPLETED && !isStale }?.translatedText
        if (stored != null && !text.isNullOrBlank()) {
            return if (stored.metadata.isMock) {
                ReaderTranslationState.Unavailable(ReaderTranslationUnavailableReason.MOCK_ONLY)
            } else {
                ReaderTranslationState.Completed(text, stored.metadata.providerId)
            }
        }

        return when (job?.status) {
            TranslationStatus.FAILED -> failed(job?.errorCode)
            TranslationStatus.COMPLETED ->
                if (isStale) ReaderTranslationState.OriginalOnly
                else ReaderTranslationState.Failed(ReaderTranslationFailure.STORAGE, canRetry = true)
            else -> ReaderTranslationState.OriginalOnly
        }
    }

    private fun failed(error: TranslationError?): ReaderTranslationState.Failed = when (error) {
        TranslationError.EMPTY_TEXT, TranslationError.TEXT_TOO_LONG ->
            ReaderTranslationState.Failed(ReaderTranslationFailure.INVALID_CONTENT, canRetry = false)
        TranslationError.UNSUPPORTED_SOURCE_LANGUAGE ->
            ReaderTranslationState.Failed(ReaderTranslationFailure.UNSUPPORTED_LANGUAGE, canRetry = false)
        TranslationError.PROVIDER_FAILURE ->
            ReaderTranslationState.Failed(ReaderTranslationFailure.PROVIDER, canRetry = true)
        null -> ReaderTranslationState.Failed(ReaderTranslationFailure.UNKNOWN, canRetry = true)
    }
}
