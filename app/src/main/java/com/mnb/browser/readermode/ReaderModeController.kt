package com.mnb.browser.readermode

import java.util.concurrent.atomic.AtomicBoolean

/** Cancellation handle carried through extraction and translation work. */
class CancellationToken {
    private val cancelled = AtomicBoolean(false)

    val isCancelled: Boolean get() = cancelled.get()

    fun cancel(): Boolean = cancelled.compareAndSet(false, true)
}

enum class ReaderModeState {
    OFF,
    EXTRACTING,
    TRANSLATING,
    READY,
    ERROR,
    CANCELLING,
}

data class ReaderModeSnapshot(
    val tabId: String,
    val session: PageSession?,
    val state: ReaderModeState,
    val token: CancellationToken?,
)

/**
 * Owns reader-mode state independently for every tab. Every asynchronous transition must provide
 * the PageSession and CancellationToken it started with; stale work is rejected after navigation,
 * tab close, WebView recreation, renderer loss, or cancellation.
 */
class ReaderModeController {
    private data class Entry(
        var session: PageSession,
        var state: ReaderModeState = ReaderModeState.OFF,
        var token: CancellationToken? = null,
    )

    private val entries = HashMap<String, Entry>()

    @Synchronized
    fun snapshot(tabId: String): ReaderModeSnapshot? = entries[tabId]?.let { it.snapshot(tabId) }

    @Synchronized
    fun state(tabId: String): ReaderModeState = entries[tabId]?.state ?: ReaderModeState.OFF

    /** Updates page URL for same-document navigation, but resets state for a new document. */
    @Synchronized
    fun onPageSessionChanged(tabId: String, session: PageSession?) {
        val old = entries[tabId]
        if (session == null) {
            old?.token?.cancel()
            entries.remove(tabId)
            return
        }
        if (old == null) {
            entries[tabId] = Entry(session)
        } else if (!old.session.sameDocumentAs(session)) {
            old.token?.cancel()
            entries[tabId] = Entry(session)
        } else {
            old.session = session
        }
    }

    @Synchronized
    fun beginExtraction(tabId: String, session: PageSession): CancellationToken? =
        begin(tabId, session, ReaderModeState.EXTRACTING)

    @Synchronized
    fun beginTranslation(tabId: String, session: PageSession, token: CancellationToken): Boolean =
        transition(tabId, session, token, ReaderModeState.TRANSLATING)

    @Synchronized
    fun markReady(tabId: String, session: PageSession, token: CancellationToken): Boolean =
        transition(tabId, session, token, ReaderModeState.READY)

    @Synchronized
    fun markError(tabId: String, session: PageSession, token: CancellationToken): Boolean =
        transition(tabId, session, token, ReaderModeState.ERROR)

    /** Moves the state to CANCELLING and invalidates the token; caller may then finish to OFF. */
    @Synchronized
    fun cancel(tabId: String): CancellationToken? {
        val entry = entries[tabId] ?: return null
        val token = entry.token ?: return null
        token.cancel()
        entry.state = ReaderModeState.CANCELLING
        return token
    }

    @Synchronized
    fun finishCancellation(tabId: String, token: CancellationToken): Boolean {
        val entry = entries[tabId] ?: return false
        if (entry.token !== token || entry.state != ReaderModeState.CANCELLING) return false
        entry.state = ReaderModeState.OFF
        entry.token = null
        return true
    }

    /** Returns false for a late result from another document or a cancelled request. */
    @Synchronized
    fun accepts(tabId: String, session: PageSession, token: CancellationToken): Boolean {
        val entry = entries[tabId] ?: return false
        return entry.session.sameDocumentAs(session) && entry.token === token && !token.isCancelled
    }

    @Synchronized
    fun closeTab(tabId: String) {
        entries.remove(tabId)?.token?.cancel()
    }

    @Synchronized
    fun clear() {
        entries.values.forEach { it.token?.cancel() }
        entries.clear()
    }

    private fun begin(tabId: String, session: PageSession, next: ReaderModeState): CancellationToken? {
        val entry = entries[tabId] ?: return null
        if (!entry.session.sameDocumentAs(session)) return null
        entry.token?.cancel()
        return CancellationToken().also {
            entry.token = it
            entry.state = next
        }
    }

    private fun transition(
        tabId: String,
        session: PageSession,
        token: CancellationToken,
        next: ReaderModeState,
    ): Boolean {
        if (!accepts(tabId, session, token)) return false
        entries[tabId]?.state = next
        return true
    }

    private fun Entry.snapshot(tabId: String) = ReaderModeSnapshot(tabId, session, state, token)
}
