package com.mnb.browser.extraction

import com.mnb.browser.web.WebViewController
import com.mnb.browser.readermode.CancellationToken
import com.mnb.browser.readermode.PageSession
import java.util.UUID

/** Runs DOM extraction and publishes results only for the exact tab/document/request that started it. */
class ExtractionController(
    private val web: WebViewController,
    private val stateManager: ExtractionStateManager,
) {
    private data class ActiveRequest(
        val id: Int,
        val tabId: String,
        val session: PageSession,
        val operationToken: CancellationToken,
        val sessionToken: String,
    )

    private var requestId = 0
    private var activeRequest: ActiveRequest? = null
    private var lastExtracted: Pair<PageSession, ExtractedContent>? = null

    init {
        web.tabDeactivatedListener = ::onTabDeactivated
        web.pageSessionChangedListener = ::onPageSessionChanged
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
        lastExtracted?.let { (oldSession, oldContent) -> web.cleanupDomObserver(oldSession, oldContent) }
        lastExtracted = null
        activeRequest?.let { old ->
            web.cleanupDomObserver(old.session, old.sessionToken)
            reader.cancel(old.tabId)
            reader.finishCancellation(old.tabId, old.operationToken)
        }
        activeRequest = null
        val token = reader.beginExtraction(tabId, session)
        if (token == null) {
            stateManager.show(ExtractionState.Error("The page is no longer available to extract."))
            return
        }
        val sessionToken = UUID.randomUUID().toString()
        val request = ActiveRequest(id, tabId, session, token, sessionToken)
        activeRequest = request
        stateManager.show(ExtractionState.Loading)
        val started = web.runExtraction(session, sessionToken) { rawJson, url ->
            if (activeRequest !== request || request.id != requestId) return@runExtraction
            if (web.activeTabId() != tabId || !web.isCurrentPageSession(tabId, session) || !reader.accepts(tabId, session, token)) {
                activeRequest = null
                stateManager.reset()
                return@runExtraction
            }
            val result = try {
                ContentParser.parse(
                    rawJson,
                    url,
                    expectedSessionToken = sessionToken,
                    expectedDocumentToken = session.documentToken,
                    expectedTabId = session.tabId,
                    expectedNavGeneration = session.navGeneration,
                )
            } catch (_: Exception) {
                ExtractionState.Error("The page returned unreadable content.")
            }
            if (result is ExtractionState.Success) reader.markReady(tabId, session, token)
            else reader.markError(tabId, session, token)
            if (result is ExtractionState.Success) lastExtracted = session to result.content
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
        activeRequest?.let { request ->
            web.cleanupDomObserver(request.session, request.sessionToken)
            web.readerModeController.cancel(request.tabId)
            web.readerModeController.finishCancellation(request.tabId, request.operationToken)
        }
        lastExtracted?.let { (session, content) ->
            web.cleanupDomObserver(session, content)
            web.readerModeController.cancel(session.tabId)?.let { token ->
                web.readerModeController.finishCancellation(session.tabId, token)
            }
        }
        lastExtracted = null
        activeRequest = null
        stateManager.reset()
    }

    private fun onTabDeactivated(tabId: String) {
        web.readerModeController.onTabDeactivated(tabId)
        val request = activeRequest
        if (request?.tabId == tabId) {
            requestId++
            web.cleanupDomObserver(request.session, request.sessionToken)
            activeRequest = null
            stateManager.reset()
        }
        lastExtracted?.takeIf { it.first.tabId == tabId }?.let { (session, content) ->
            web.cleanupDomObserver(session, content)
            lastExtracted = null
            if (request?.tabId != tabId) stateManager.reset()
        }
    }

    private fun onPageSessionChanged(tabId: String, session: PageSession?) {
        val request = activeRequest
        if (request?.tabId == tabId && (session == null || !request.session.sameDocumentAs(session))) {
            requestId++
            activeRequest = null
            stateManager.reset()
        }
        val extracted = lastExtracted
        if (extracted?.first?.tabId == tabId && (session == null || !extracted.first.sameDocumentAs(session))) {
            lastExtracted = null
            stateManager.reset()
        }
    }
}
