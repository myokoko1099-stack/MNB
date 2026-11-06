package com.mnb.browser.tabs

import com.mnb.browser.web.FileWebSessionStore
import com.mnb.browser.web.WebSessionStore
import java.io.File

/**
 * One private file per tab for the WebView's saved state (current page plus back/forward history).
 * Tab ids are reduced to safe characters, so an id can never point outside [dirProvider]'s folder.
 * The folder is resolved lazily because filesDir is not available until the Activity is attached.
 */
class TabSessionFiles(private val dirProvider: () -> File) {

    fun storeFor(tabId: String): WebSessionStore = FileWebSessionStore { fileFor(tabId) }

    fun fileFor(tabId: String): File {
        val dir = dirProvider()
        dir.mkdirs()
        return File(dir, safeName(tabId) + ".bin")
    }

    /** Deletes the session file of every tab that is not in [keepIds]. Background thread only. */
    fun prune(keepIds: Collection<String>) {
        try {
            val keep = keepIds.map { safeName(it) + ".bin" }.toSet()
            dirProvider().listFiles()?.forEach { file ->
                if (file.isFile && file.name !in keep) file.delete()
            }
        } catch (e: Exception) {
            // Best effort.
        }
    }

    companion object {
        fun safeName(tabId: String): String =
            tabId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifEmpty { "tab" }
    }
}
