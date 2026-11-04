package com.mnb.browser.translation.usecase

import com.mnb.browser.translation.db.StoredTranslation
import com.mnb.browser.translation.quality.QualityResult

/** Why an instant chapter translation did not produce a COMPLETED translation. */
enum class InstantTranslationFailure {
    /** The original text is empty or blank (or produced no chunks). */
    EMPTY_INPUT,

    /** Source or target code unknown, or the source language is not supported by the provider. */
    UNSUPPORTED_LANGUAGE,

    /** The chunks do not cover the chapter correctly (missing, duplicate or oversized chunk). */
    INVALID_CHUNK,

    /** Every chunk that was tried failed. */
    PROVIDER_FAILURE,

    /** Some chunks were translated, others failed. The finished chunks are kept for a retry. */
    PARTIAL_FAILURE,

    /** Reading or writing the local database failed (including a missing novel). */
    DATABASE_FAILURE,

    /** A request for the same chapter is already running. */
    DUPLICATE_REQUEST,
}

data class ChunkFailure(val chunkIndex: Int, val message: String?)

sealed interface InstantTranslationOutcome {
    /**
     * Every chunk is translated and the full chapter translation is stored with status COMPLETED.
     *
     * [quality] is the report from the deterministic quality checker, warnings included. It is null
     * only when the check itself failed ([qualityError] says why); the translation is still stored.
     * Quality issues do not turn a translation into a failure: the reader decides what to do with them.
     * [QualityResult.semantic] stays NOT_EVALUATED unless an evaluator ran.
     */
    data class Completed(
        val translation: StoredTranslation,
        val quality: QualityResult?,
        val qualityError: String?,
        val chunkCount: Int,
        /** Chunks taken from an earlier run instead of calling the provider. */
        val reusedChunkCount: Int,
        /** True when the whole stored translation was reused and the provider was not called at all. */
        val reusedWholeChapter: Boolean,
    ) : InstantTranslationOutcome

    /** No complete translation exists. Nothing partial is ever returned as a result text. */
    data class Failed(
        val reason: InstantTranslationFailure,
        val message: String? = null,
        val totalChunks: Int = 0,
        val completedChunks: Int = 0,
        val failedChunks: List<ChunkFailure> = emptyList(),
    ) : InstantTranslationOutcome

    /** Stopped by the caller. Finished chunks are kept; the chapter status is CANCELLED. */
    data class Cancelled(val totalChunks: Int, val completedChunks: Int) : InstantTranslationOutcome
}
