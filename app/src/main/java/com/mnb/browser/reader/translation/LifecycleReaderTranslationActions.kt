package com.mnb.browser.reader.translation

import com.mnb.browser.extraction.ExtractedContent
import com.mnb.browser.readermode.ReaderModeController
import com.mnb.browser.web.WebViewController

/** Connects the production translation action to the originating tab/document lifecycle. */
class LifecycleReaderTranslationActions(
    private val delegate: ReaderInstantTranslationController,
    private val web: WebViewController,
) : ReaderTranslationActions {
    override fun onChapterShown() = delegate.onChapterShown()

    override fun requestTranslation(content: ExtractedContent) {
        val tabId = web.activeTabId() ?: return
        val session = web.currentPageSession(tabId) ?: return
        val lifecycle: ReaderModeController = web.readerModeController
        val token = lifecycle.snapshot(tabId)?.token ?: return
        if (!lifecycle.beginTranslation(tabId, session, token)) return
        val accepted = { lifecycle.accepts(tabId, session, token) && web.isCurrentPageSession(tabId, session) }
        val result = delegate.start(content, isCancelled = { !accepted() }) { state ->
            if (!accepted()) return@start
            if (state is ReaderTranslationState.Completed) lifecycle.markReady(tabId, session, token)
            else lifecycle.markError(tabId, session, token)
        }
        if (result != StartResult.STARTED && accepted()) {
            lifecycle.markError(tabId, session, token)
        }
    }
}
