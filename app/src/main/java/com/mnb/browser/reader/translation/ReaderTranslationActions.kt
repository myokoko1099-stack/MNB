package com.mnb.browser.reader.translation

import androidx.compose.runtime.staticCompositionLocalOf
import com.mnb.browser.extraction.ExtractedContent

/** True when the reader may offer the "translate" action for a chapter in this state. */
val ReaderTranslationState.canRequestTranslation: Boolean
    get() = when (this) {
        ReaderTranslationState.OriginalOnly -> true
        is ReaderTranslationState.Failed -> canRetry
        is ReaderTranslationState.Unavailable -> reason != ReaderTranslationUnavailableReason.NO_CHAPTER_IDENTITY
        is ReaderTranslationState.Pending,
        is ReaderTranslationState.Processing,
        is ReaderTranslationState.Completed,
        -> false
    }

/** What the reader screen can ask of the translation side (Phase 04-I10-C1). */
interface ReaderTranslationActions {
    /** The user asked to translate the open chapter. Returns immediately; work runs off the main thread. */
    fun requestTranslation(content: ExtractedContent)

    /** The reader shows a chapter: pick up an already stored translation, if any. Returns immediately. */
    fun onChapterShown()
}

/** Null (the default) means the reader has no translation action and works exactly as before. */
val LocalReaderTranslationActions = staticCompositionLocalOf<ReaderTranslationActions?> { null }
