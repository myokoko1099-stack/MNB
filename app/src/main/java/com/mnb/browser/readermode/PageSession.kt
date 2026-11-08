package com.mnb.browser.readermode

import java.net.URI
import java.util.UUID

/** Identity of one document loaded in one browser tab. */
data class PageSession(
    val tabId: String,
    val navGeneration: Long,
    val normalizedUrl: String,
    val documentToken: String,
) {
    /** Stable identity used to accept results; URL changes within the same document do not matter. */
    fun sameDocumentAs(other: PageSession?): Boolean =
        other != null &&
            tabId == other.tabId &&
            navGeneration == other.navGeneration &&
            documentToken == other.documentToken
}

/**
 * Tracks page identity independently for every tab. Page-start/reload/recreate events create a new
 * document; history updates without a reload only update the URL and preserve the document token.
 */
class PageSessionManager(
    private val newDocumentToken: () -> String = { UUID.randomUUID().toString() },
) {
    private val sessions = LinkedHashMap<String, PageSession>()
    private val generations = HashMap<String, Long>()
    /** WebView commonly reports a reload in history callbacks after onPageStarted. */
    private val startedReloads = HashMap<String, String>()

    @Synchronized
    fun current(tabId: String): PageSession? = sessions[tabId]

    /** A main-frame page start always begins a new document, including same-URL reloads. */
    @Synchronized
    fun onPageStarted(tabId: String, url: String?): PageSession {
        val normalized = normalizeUrl(url)
        startedReloads[tabId] = normalized
        return beginNewDocument(tabId, url)
    }

    /** A reload is a new document even if the URL is unchanged. */
    @Synchronized
    fun onReload(tabId: String, url: String?): PageSession {
        startedReloads.remove(tabId)
        return beginNewDocument(tabId, url)
    }

    /** A normal history callback without reload is same-document until page-start says otherwise. */
    @Synchronized
    fun onHistoryUpdated(tabId: String, url: String?, isReload: Boolean): PageSession {
        if (isReload) {
            val normalized = normalizeUrl(url)
            // Android WebView may emit onPageStarted followed by doUpdateVisitedHistory(true)
            // for the *same* reload. The page-start already established its new identity.
            if (startedReloads[tabId] == normalized && sessions[tabId]?.normalizedUrl == normalized) {
                return sessions.getValue(tabId)
            }
            startedReloads.remove(tabId)
            return beginNewDocument(tabId, url)
        }
        val existing = sessions[tabId]
        if (existing == null) return beginNewDocument(tabId, url)
        val updated = existing.copy(normalizedUrl = normalizeUrl(url))
        sessions[tabId] = updated
        return updated
    }

    /** Duplicate page-finished callbacks must not create another document identity. */
    @Synchronized
    fun onPageFinished(tabId: String, url: String?): PageSession {
        startedReloads.remove(tabId)
        val existing = sessions[tabId]
        if (existing != null) return existing
        return beginNewDocument(tabId, url)
    }

    /** A destroyed/unloaded WebView invalidates all work for that document. */
    @Synchronized
    fun onWebViewUnloaded(tabId: String) {
        sessions.remove(tabId)
    }

    /** Renderer loss invalidates the old document before WebView recreation. */
    @Synchronized
    fun onRendererCrashed(tabId: String) {
        sessions.remove(tabId)
    }

    @Synchronized
    fun closeTab(tabId: String) {
        sessions.remove(tabId)
        generations.remove(tabId)
        startedReloads.remove(tabId)
    }

    @Synchronized
    fun clear() {
        sessions.clear()
        generations.clear()
        startedReloads.clear()
    }

    private fun beginNewDocument(tabId: String, url: String?): PageSession {
        val generation = (generations[tabId] ?: 0L) + 1L
        generations[tabId] = generation
        return PageSession(tabId, generation, normalizeUrl(url), newDocumentToken()).also {
            sessions[tabId] = it
        }
    }

    companion object {
        /** Normalizes only transport/host spelling; path, query, and fragment remain observable URL data. */
        fun normalizeUrl(url: String?): String {
            val text = url?.trim().orEmpty()
            if (text.isEmpty()) return ""
            return try {
                val uri = URI(text)
                URI(
                    uri.scheme?.lowercase(),
                    uri.userInfo,
                    uri.host?.lowercase(),
                    uri.port,
                    uri.path,
                    uri.query,
                    uri.fragment,
                ).toString()
            } catch (_: Exception) {
                text
            }
        }
    }
}
