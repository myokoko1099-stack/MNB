package com.mnb.browser.extraction

import com.mnb.browser.web.WebViewController
import com.mnb.browser.readermode.CancellationToken
import com.mnb.browser.readermode.PageSession

/** Runs an extraction on the current page and publishes the result to the state manager. */
class ExtractionController(
    private val web: WebViewController,
    private val stateManager: ExtractionStateManager,
) {
    private var requestId = 0
    private var activeRequest: Triple<String, PageSession, CancellationToken>? = null

    init {
        web.tabDeactivatedListener = ::onTabDeactivated
    }

    fun extract() {
        val id = ++requestId
        val tabId = web.activeTabId()
        val session = tabId?.let { web.currentPageSession(it) }
        if (tabId == null || session == null) {
            stateManager.show(ExtractionState.Error("No page is open to extract."))
            return
        }
        val reader = web.readerModeController
        activeRequest?.let { (oldTab, _, oldToken) ->
            reader.cancel(oldTab)
            reader.finishCancellation(oldTab, oldToken)
        }
        activeRequest = null
        val token = reader.beginExtraction(tabId, session)
        if (token == null) {
            stateManager.show(ExtractionState.Error("The page is no longer available to extract."))
            return
        }
        activeRequest = Triple(tabId, session, token)
        stateManager.show(ExtractionState.Loading)
        val started = web.runExtraction { rawJson, url ->
            // A result is accepted only for the exact tab, document, and uncancelled operation.
            if (id != requestId || web.activeTabId() != tabId ||
                !web.isCurrentPageSession(tabId, session) || !reader.accepts(tabId, session, token)
            ) return@runExtraction
            val result = ContentParser.parse(rawJson, url)
            if (result is ExtractionState.Success) reader.markReady(tabId, session, token)
            else reader.markError(tabId, session, token)
            activeRequest = null
            stateManager.show(result)
        }
        if (!started) {
            reader.markError(tabId, session, token)
            activeRequest = null
            stateManager.show(ExtractionState.Error("No page is open to extract."))
        }
    }

    fun close() {
        requestId++
        activeRequest?.let { (tabId, _, token) ->
            web.readerModeController.cancel(tabId)
            web.readerModeController.finishCancellation(tabId, token)
        }
        activeRequest = null
        stateManager.reset()
    }

    private fun onTabDeactivated(tabId: String) {
        web.readerModeController.onTabDeactivated(tabId)
        val request = activeRequest
        if (request?.first == tabId) {
            requestId++
            activeRequest = null
            stateManager.reset()
        }
    }
}
