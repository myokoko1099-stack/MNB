package com.mnb.browser.extraction

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Holds what the extraction preview screen shows. */
class ExtractionStateManager {
    var state: ExtractionState by mutableStateOf(ExtractionState.Idle)
        private set

    fun show(newState: ExtractionState) { state = newState }
    fun reset() { state = ExtractionState.Idle }
}
