package com.mnb.browser.security

import android.content.Context
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Keeps one API key encrypted at rest: AES-256-GCM, with the key held in the Android Keystore.
 * Only the encrypted envelope is written to local storage; the plain key lives in memory just
 * long enough to encrypt or hand back, and the byte buffers used for it are cleared afterwards.
 *
 * Nothing in this class logs, and no result or exception it produces contains key text.
 *
 * Use one instance for the whole app: operations are serialised per instance.
 */
class SecureApiKeyStore(
    private val cipher: AeadCipher,
    private val storage: EncryptedBlobStorage,
) : ApiKeyStore {

    private val lock = Any()

    override fun hasKey(): Boolean = synchronized(lock) {
        try {
            storage.exists()
        } catch (e: Exception) {
            false
        }
    }

    override fun save(apiKey: String): SecureResult<Unit> =
        synchronized(lock) { store(apiKey, requireExisting = false) }

    override fun update(apiKey: String): SecureResult<Unit> =
        synchronized(lock) { store(apiKey, requireExisting = true) }

    override fun read(): SecureResult<ApiKey> = synchronized(lock) {
        val blob = try {
            storage.read() ?: return@synchronized failure(SecureStorageError.NOT_FOUND)
        } catch (e: StoredBlobCorruptedException) {
            return@synchronized failure(SecureStorageError.CORRUPTED_DATA, e)
        } catch (e: Exception) {
            return@synchronized failure(SecureStorageError.STORAGE_FAILURE, e)
        }

        var plain: ByteArray? = null
        try {
            val decrypted = cipher.decrypt(blob)
            plain = decrypted
            val text = decodeUtf8(decrypted)
                ?: return@synchronized failure(SecureStorageError.CORRUPTED_DATA)
            // The text was valid when it was saved; anything else means the data is not ours.
            val key = ApiKey.parseOrNull(text)
                ?: return@synchronized failure(SecureStorageError.CORRUPTED_DATA)
            SecureResult.Success(key)
        } catch (e: CipherException) {
            failure(mapCipherReason(e.reason), e)
        } catch (e: Exception) {
            failure(SecureStorageError.DECRYPTION_FAILED, e)
        } finally {
            plain?.fill(0)
        }
    }

    override fun delete(): SecureResult<Unit> = synchronized(lock) {
        // Remove the ciphertext first: if that fails nothing has changed and the key is kept.
        try {
            if (!storage.clear()) return@synchronized failure(SecureStorageError.STORAGE_FAILURE)
        } catch (e: Exception) {
            return@synchronized failure(SecureStorageError.STORAGE_FAILURE, e)
        }
        try {
            cipher.deleteKey()
        } catch (e: Exception) {
            // The secret is already gone; a Keystore key with no data protects nothing.
        }
        SecureResult.Success(Unit)
    }

    private fun store(raw: String, requireExisting: Boolean): SecureResult<Unit> {
        val key = ApiKey.parseOrNull(raw) ?: return failure(SecureStorageError.INVALID_INPUT)

        if (requireExisting) {
            val exists = try {
                storage.exists()
            } catch (e: Exception) {
                return failure(SecureStorageError.STORAGE_FAILURE, e)
            }
            if (!exists) return failure(SecureStorageError.NOT_FOUND)
        }

        val plain = key.reveal().toByteArray(Charsets.UTF_8)
        val envelope = try {
            cipher.encrypt(plain)
        } catch (e: Exception) {
            return failure(SecureStorageError.ENCRYPTION_FAILED, e)
        } finally {
            plain.fill(0)
        }

        val written = try {
            storage.write(envelope)
        } catch (e: Exception) {
            return failure(SecureStorageError.STORAGE_FAILURE, e)
        }
        return if (written) SecureResult.Success(Unit) else failure(SecureStorageError.STORAGE_FAILURE)
    }

    private fun mapCipherReason(reason: CipherException.Reason): SecureStorageError = when (reason) {
        CipherException.Reason.KEY_UNAVAILABLE -> SecureStorageError.KEY_UNAVAILABLE
        CipherException.Reason.ENCRYPT_FAILED -> SecureStorageError.ENCRYPTION_FAILED
        CipherException.Reason.DECRYPT_FAILED -> SecureStorageError.DECRYPTION_FAILED
        CipherException.Reason.MALFORMED_ENVELOPE -> SecureStorageError.CORRUPTED_DATA
    }

    private fun decodeUtf8(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (e: CharacterCodingException) {
        null
    }

    /** Failure carrying only a class name of the underlying problem, never its message. */
    private fun failure(error: SecureStorageError, cause: Throwable? = null): SecureResult.Failure {
        val origin = if (cause is CipherException) cause.cause ?: cause else cause
        return SecureResult.Failure(error, origin?.javaClass?.name)
    }

    companion object {
        const val KEY_ALIAS = "mnb.gemini_api_key.aes.v1"
        const val ASSOCIATED_DATA = "mnb:gemini_api_key:v1"

        /** The real wiring: Keystore key + private SharedPreferences file. */
        fun create(context: Context): SecureApiKeyStore = SecureApiKeyStore(
            cipher = AesGcmCipher(AndroidKeystoreKeyProvider(KEY_ALIAS), ASSOCIATED_DATA),
            storage = SharedPreferencesEncryptedBlobStorage(context),
        )
    }
}
