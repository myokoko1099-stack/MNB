package com.mnb.browser.security

/**
 * What a caller gets when it asks for the stored API key (Phase 06-A3, prepared for 06-B).
 * Nothing here ever carries error text: [Unavailable] holds only a [SecureStorageError] code, and
 * [Available] prints as `Available(key=ApiKey(***))`, so a lookup is always safe to log.
 */
sealed interface ApiKeyLookup {
    /** A key is stored and was decrypted. */
    data class Available(val key: ApiKey) : ApiKeyLookup

    /** No key has been saved. The caller should ask the user to add one in Settings. */
    object Missing : ApiKeyLookup {
        override fun toString(): String = "Missing"
    }

    /** A key is stored but cannot be used right now (see [error]). Nothing was changed or deleted. */
    data class Unavailable(val error: SecureStorageError) : ApiKeyLookup
}

/**
 * Read-only access to the stored API key for the Phase 06-B direct Gemini provider. It can neither
 * save, update nor delete: those stay with [ApiKeyStore] and the settings screen.
 *
 * [lookup] does blocking Keystore and file work: call it off the main thread, use the key only at
 * the point where the request is sent, and do not keep it, log it or put it in any state.
 */
fun interface ApiKeyProvider {
    fun lookup(): ApiKeyLookup
}

/**
 * [ApiKeyProvider] on top of an [ApiKeyStore]. It never throws.
 *
 * Use the same [ApiKeyStore] instance as the settings screen: the store serialises its operations
 * per instance, so a second instance would not be covered by that lock.
 */
class StoreApiKeyProvider(private val store: ApiKeyStore) : ApiKeyProvider {
    override fun lookup(): ApiKeyLookup = try {
        when (val result = store.read()) {
            is SecureResult.Success -> ApiKeyLookup.Available(result.value)
            is SecureResult.Failure ->
                if (result.error == SecureStorageError.NOT_FOUND) {
                    ApiKeyLookup.Missing
                } else {
                    ApiKeyLookup.Unavailable(result.error)
                }
        }
    } catch (e: Exception) {
        // No message or cause is kept: it could mention the key.
        ApiKeyLookup.Unavailable(SecureStorageError.STORAGE_FAILURE)
    }
}
