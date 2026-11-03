package com.mnb.browser.extraction

import com.mnb.browser.web.WebViewController

/** Runs an extraction on the current page and publishes the result to the state manager. */
class ExtractionController(
    private val web: WebViewController,
    private val stateManager: ExtractionStateManager,
) {
    private var requestId = 0

    fun extract() {
        val id = ++requestId
        stateManager.show(ExtractionState.Loading)
        val started = web.runExtraction { rawJson, url ->
            // Ignore results from a request the user already closed or replaced.
            if (id == requestId) stateManager.show(ContentParser.parse(rawJson, url))
        }
        if (!started) stateManager.show(ExtractionState.Error("No page is open to extract."))
    }

    fun close() {
        requestId++
        stateManager.reset()
    }
}
