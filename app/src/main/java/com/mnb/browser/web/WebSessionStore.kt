package com.mnb.browser.web

import java.io.File

/** Persists the WebView's saved state (current page plus its back/forward history). */
interface WebSessionStore {
    fun load(): ByteArray?
    fun save(bytes: ByteArray)
}

/**
 * Stores the session in a private file. The file is resolved lazily because the Activity's
 * filesDir is not available until the Activity is attached.
 */
class FileWebSessionStore(private val fileProvider: () -> File) : WebSessionStore {
    override fun load(): ByteArray? = try {
        fileProvider().takeIf { it.exists() }?.readBytes()
    } catch (e: Exception) {
        null
    }

    override fun save(bytes: ByteArray) {
        try {
            val target = fileProvider()
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            }
        } catch (e: Exception) {
            // Best effort: losing the saved session must never crash the app.
        }
    }
}
