package com.mnb.browser.security

import android.content.Context
import android.util.Base64

/**
 * [EncryptedBlobStorage] backed by a private SharedPreferences file of its own (separate from the
 * reader's). Only Base64 of the encrypted envelope is written. Writes use `commit()` so the result
 * is known. The app manifest has `allowBackup="false"`, so this file is not copied to other devices,
 * where the Keystore key would not exist anyway.
 */
class SharedPreferencesEncryptedBlobStorage(
    context: Context,
    prefsName: String = DEFAULT_PREFS_NAME,
    private val entryKey: String = DEFAULT_ENTRY_KEY,
) : EncryptedBlobStorage {

    private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    override fun exists(): Boolean = prefs.contains(entryKey)

    override fun read(): ByteArray? {
        val text = prefs.getString(entryKey, null) ?: return null
        return try {
            Base64.decode(text, Base64.NO_WRAP)
        } catch (e: IllegalArgumentException) {
            throw StoredBlobCorruptedException(e)
        }
    }

    override fun write(blob: ByteArray): Boolean =
        prefs.edit().putString(entryKey, Base64.encodeToString(blob, Base64.NO_WRAP)).commit()

    override fun clear(): Boolean = prefs.edit().remove(entryKey).commit()

    companion object {
        const val DEFAULT_PREFS_NAME = "mnb_secure_storage"
        const val DEFAULT_ENTRY_KEY = "gemini_api_key.v1"
    }
}
