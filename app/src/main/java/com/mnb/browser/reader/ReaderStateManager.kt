package com.mnb.browser.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mnb.browser.extraction.ExtractedContent
import com.mnb.browser.reader.translation.ReaderTranslationStateHolder

/**
 * Holds the chapter currently open in the reader, or null when the reader is closed.
 *
 * [translation] (Phase 04-I10-A) is told when a chapter opens or closes, so the translation state
 * always belongs to the open chapter. It is optional: without it the reader behaves exactly as before.
 */
class ReaderStateManager(val translation: ReaderTranslationStateHolder? = null) {
    var content: ExtractedContent? by mutableStateOf(null)
        private set

    fun open(chapter: ExtractedContent) {
        content = chapter
        translation?.onChapterOpened(chapter)
    }

    fun close() {
        content = null
        translation?.onChapterClosed()
    }
}
