package com.mnb.browser.security

import java.nio.ByteBuffer
import java.security.InvalidKeyException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM with a fresh random 96-bit IV for every encryption (the cipher generates it, which
 * is also what the Android Keystore requires).
 *
 * [associatedData] is authenticated but not stored: it ties an envelope to its purpose, so an
 * envelope copied from somewhere else fails to decrypt.
 *
 * Envelope layout: `[version = 1][ivLength = 12][iv][ciphertext + 16-byte tag]`.
 */
class AesGcmCipher(
    private val keyProvider: KeyProvider,
    associatedData: String,
) : AeadCipher {

    private val aad: ByteArray = associatedData.toByteArray(Charsets.UTF_8)

    override fun encrypt(plain: ByteArray): ByteArray {
        try {
            return try {
                encryptWith(keyProvider.getOrCreateKey(), plain)
            } catch (e: InvalidKeyException) {
                // The stored key is unusable (e.g. invalidated). We are about to replace the stored
                // value anyway, so replace the key too and try once more.
                keyProvider.deleteKey()
                encryptWith(keyProvider.getOrCreateKey(), plain)
            }
        } catch (e: Exception) {
            throw CipherException(CipherException.Reason.ENCRYPT_FAILED, e)
        }
    }

    override fun decrypt(envelope: ByteArray): ByteArray {
        if (envelope.size < MIN_ENVELOPE_SIZE ||
            envelope[0] != VERSION ||
            envelope[1].toInt() != IV_SIZE
        ) {
            throw CipherException(CipherException.Reason.MALFORMED_ENVELOPE)
        }
        val key = try {
            keyProvider.getKey()
        } catch (e: Exception) {
            throw CipherException(CipherException.Reason.KEY_UNAVAILABLE, e)
        }
        if (key == null) throw CipherException(CipherException.Reason.KEY_UNAVAILABLE)

        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, envelope, HEADER_SIZE, IV_SIZE))
            cipher.updateAAD(aad)
            val dataStart = HEADER_SIZE + IV_SIZE
            return cipher.doFinal(envelope, dataStart, envelope.size - dataStart)
        } catch (e: Exception) {
            // Includes AEADBadTagException: wrong key, wrong context, or modified data.
            throw CipherException(CipherException.Reason.DECRYPT_FAILED, e)
        }
    }

    override fun deleteKey() {
        keyProvider.deleteKey()
    }

    private fun encryptWith(key: SecretKey, plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(aad)
        val iv = cipher.iv
        check(iv != null && iv.size == IV_SIZE) { "unexpected IV" }
        val encrypted = cipher.doFinal(plain)
        return ByteBuffer.allocate(HEADER_SIZE + iv.size + encrypted.size)
            .put(VERSION)
            .put(IV_SIZE.toByte())
            .put(iv)
            .put(encrypted)
            .array()
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val VERSION: Byte = 1
        const val IV_SIZE = 12
        const val TAG_BITS = 128
        const val HEADER_SIZE = 2
        const val MIN_ENVELOPE_SIZE = HEADER_SIZE + IV_SIZE + TAG_BITS / 8
    }
}
