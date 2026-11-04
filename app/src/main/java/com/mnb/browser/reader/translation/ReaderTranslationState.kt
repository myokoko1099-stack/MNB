package com.mnb.browser.reader.translation

/**
 * What the reader may show about the translation of the chapter it has open (Phase 04-I10-A).
 * The reader sees only this; it never sees jobs, queues, providers or databases.
 */
sealed interface ReaderTranslationState {
    /** No translation was asked for (or an old one is outdated): show the original text. */
    data object OriginalOnly : ReaderTranslationState

    /** Waiting in the queue. [queuePosition] is 1 for the next job to start, null when unknown. */
    data class Pending(val queuePosition: Int? = null) : ReaderTranslationState {
        init { require(queuePosition == null || queuePosition >= 1) { "queuePosition starts at 1" } }
    }

    /** Being translated now. [totalChunks] is 0 while the chunk count is not known yet. */
    data class Processing(val completedChunks: Int = 0, val totalChunks: Int = 0) : ReaderTranslationState {
        init {
            require(totalChunks >= 0) { "totalChunks must not be negative" }
            require(completedChunks in 0..totalChunks) { "completedChunks must be between 0 and totalChunks" }
        }

        /** 0..1, or null when the chunk count is not known. */
        val fraction: Float? get() = if (totalChunks == 0) null else completedChunks.toFloat() / totalChunks
    }

    /** A real, up-to-date translation is ready. Placeholder (mock) output never becomes Completed. */
    data class Completed(val translatedText: String, val providerId: String) : ReaderTranslationState {
        init { require(translatedText.isNotBlank()) { "translatedText must not be blank" } }
    }

    /** Translation was tried and failed. [canRetry] is false for problems a retry cannot fix. */
    data class Failed(val reason: ReaderTranslationFailure, val canRetry: Boolean) : ReaderTranslationState

    /** Translation cannot be offered for this chapter at all (see [ReaderTranslationUnavailableReason]). */
    data class Unavailable(val reason: ReaderTranslationUnavailableReason) : ReaderTranslationState
}

enum class ReaderTranslationFailure {
    PROVIDER,
    INVALID_CONTENT,
    UNSUPPORTED_LANGUAGE,
    STORAGE,
    UNKNOWN,
}

enum class ReaderTranslationUnavailableReason {
    /** The chapter could not be identified, so no translation can be tied to it. */
    NO_CHAPTER_IDENTITY,

    /** No translation backend is connected yet. */
    NOT_CONFIGURED,

    /** Only placeholder (mock) output exists, which must not be shown as a translation. */
    MOCK_ONLY,
}

/** True while work for the chapter is waiting or running. */
val ReaderTranslationState.isActive: Boolean
    get() = this is ReaderTranslationState.Pending || this is ReaderTranslationState.Processing

/** True only when translated text may be shown. */
val ReaderTranslationState.hasTranslation: Boolean
    get() = this is ReaderTranslationState.Completed
