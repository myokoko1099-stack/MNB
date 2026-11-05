package com.mnb.browser.security

/**
 * The only surface other code (a later Settings UI, a later Gemini client) should use to keep the
 * user's API key. Every function is blocking (Keystore and file I/O): call it off the main thread.
 * Nothing here throws; failures come back as [SecureResult.Failure].
 */
interface ApiKeyStore {

    /** True when an encrypted key is stored. Does not decrypt, so it cannot fail on a bad key. */
    fun hasKey(): Boolean

    /** Stores [apiKey], replacing any key already stored. */
    fun save(apiKey: String): SecureResult<Unit>

    /** Replaces the stored key. Fails with [SecureStorageError.NOT_FOUND] when none is stored. */
    fun update(apiKey: String): SecureResult<Unit>

    /** Decrypts and returns the stored key, or [SecureStorageError.NOT_FOUND] when none is stored. */
    fun read(): SecureResult<ApiKey>

    /** Removes the stored key and its Keystore key. Succeeds when nothing was stored. */
    fun delete(): SecureResult<Unit>
}
