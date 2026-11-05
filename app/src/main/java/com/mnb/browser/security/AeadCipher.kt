package com.mnb.browser.security

/** Encrypts and decrypts small byte arrays. [encrypt] output is self-contained (it carries its own IV). */
interface AeadCipher {
    /** @throws CipherException on any failure. */
    fun encrypt(plain: ByteArray): ByteArray

    /** @throws CipherException on any failure. */
    fun decrypt(envelope: ByteArray): ByteArray

    /** Removes the encryption key, if any. Existing envelopes can no longer be decrypted. */
    fun deleteKey()
}

/** The only exception a cipher throws. Its message is the [reason] name, never any data. */
class CipherException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason {
        /** No usable key exists to decrypt with. */
        KEY_UNAVAILABLE,
        ENCRYPT_FAILED,

        /** Wrong key, wrong context, or the data was modified. */
        DECRYPT_FAILED,

        /** The envelope is not in the expected format. */
        MALFORMED_ENVELOPE,
    }
}
