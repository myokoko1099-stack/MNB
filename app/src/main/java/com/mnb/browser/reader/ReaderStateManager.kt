package com.mnb.browser.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mnb.browser.extraction.ExtractedContent

/** Holds the chapter currently open in the reader, or null when the reader is closed. */
class ReaderStateManager {
    var content: ExtractedContent? by mutableStateOf(null)
        private set

    fun open(chapter: ExtractedContent) { content = chapter }
    fun close() { content = null }
}
