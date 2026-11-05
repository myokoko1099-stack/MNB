package com.mnb.browser.security

/** Why a secure-storage operation did not succeed. Callers map these to UI messages later. */
enum class SecureStorageError {
    /** The key text is empty, too long, or contains characters an API key never has. */
    INVALID_INPUT,

    /** Nothing is stored (read / update). */
    NOT_FOUND,

    /** Encrypted data exists but the Keystore key is gone or unusable (e.g. device restore). */
    KEY_UNAVAILABLE,

    /** The key could not be encrypted or the Keystore refused to work. */
    ENCRYPTION_FAILED,

    /** Stored data exists but could not be decrypted or failed authentication. */
    DECRYPTION_FAILED,

    /** Stored data is not in the expected format (truncated, edited, wrong version). */
    CORRUPTED_DATA,

    /** The local file could not be read, written or cleared. */
    STORAGE_FAILURE,
}

/**
 * Outcome of a secure-storage operation. Never thrown; always returned.
 *
 * Neither case ever carries exception messages: [Failure.causeType] is only the class name of the
 * underlying exception, so a result is always safe to log.
 */
sealed interface SecureResult<out T> {
    data class Success<out T>(val value: T) : SecureResult<T>

    data class Failure(
        val error: SecureStorageError,
        val causeType: String? = null,
    ) : SecureResult<Nothing>
}
