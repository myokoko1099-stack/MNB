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
 * Tracks one identity per main-frame document load, independently for every tab. A start callback
 * creates the identity; further start/history callbacks before the matching finish update its URL
 * without creating another identity. This also covers reload callbacks whose URL changes on redirect.
 */
class PageSessionManager(
    private val newDocumentToken: () -> String = { UUID.randomUUID().toString() },
) {
    private data class LoadState(var inProgress: Boolean = false)

    private val sessions = LinkedHashMap<String, PageSession>()
    private val generations = HashMap<String, Long>()
    private val loads = HashMap<String, LoadState>()

    @Synchronized
    fun current(tabId: String): PageSession? = sessions[tabId]

    /** A top-level load starts a new document unless it is another start in the active redirect chain. */
    @Synchronized
    fun onPageStarted(tabId: String, url: String?): PageSession {
        val load = loads.getOrPut(tabId) { LoadState() }
        val existing = sessions[tabId]
        val session = if (load.inProgress && existing != null) {
            updateUrl(existing, url)
        } else {
            beginNewDocument(tabId, url)
        }
        load.inProgress = true
        return session
    }

    /** Explicit reload signal used by callers that initiate reload outside WebView callbacks. */
    @Synchronized
    fun onReload(tabId: String, url: String?): PageSession {
        val session = beginNewDocument(tabId, url)
        loads.getOrPut(tabId) { LoadState() }.inProgress = true
        return session
    }

    /**
     * History callbacks are URL updates within a document. A reload callback creates a document
     * only when no main-frame load is already in progress; thus either callback ordering is safe,
     * and redirected URLs do not cause another generation.
     */
    @Synchronized
    fun onHistoryUpdated(tabId: String, url: String?, isReload: Boolean): PageSession {
        val existing = sessions[tabId]
        val load = loads.getOrPut(tabId) { LoadState() }
        if (isReload && !load.inProgress) {
            val session = beginNewDocument(tabId, url)
            load.inProgress = true
            return session
        }
        if (existing == null) {
            val session = beginNewDocument(tabId, url)
            load.inProgress = true
            return session
        }
        return updateUrl(existing, url)
    }

    /** Finishing a load records the final URL and closes the callback window; duplicate finishes are idempotent. */
    @Synchronized
    fun onPageFinished(tabId: String, url: String?): PageSession {
        val existing = sessions[tabId]
        val load = loads.remove(tabId)
        if (existing == null) return beginNewDocument(tabId, url)
        // Repeated finish callbacks are not new transitions and must not roll a final URL backward.
        return if (load != null) updateUrl(existing, url) else existing
    }

    /** A destroyed/unloaded WebView invalidates all work for that document. */
    @Synchronized
    fun onWebViewUnloaded(tabId: String) {
        sessions.remove(tabId)
        loads.remove(tabId)
    }

    /** Renderer loss invalidates the old document before WebView recreation. */
    @Synchronized
    fun onRendererCrashed(tabId: String) {
        sessions.remove(tabId)
        loads.remove(tabId)
    }

    @Synchronized
    fun closeTab(tabId: String) {
        sessions.remove(tabId)
        generations.remove(tabId)
        loads.remove(tabId)
    }

    @Synchronized
    fun clear() {
        sessions.clear()
        generations.clear()
        loads.clear()
    }

    private fun updateUrl(existing: PageSession, url: String?): PageSession =
        existing.copy(normalizedUrl = normalizeUrl(url)).also { sessions[existing.tabId] = it }

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
