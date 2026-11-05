package com.mnb.browser.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureApiKeyStoreTest {
    private val provider = SoftwareKeyProvider()
    private val storage = MemoryBlobStorage()
    private val store = newStore()

    private fun newStore(
        p: KeyProvider = provider,
        s: EncryptedBlobStorage = storage,
    ) = SecureApiKeyStore(AesGcmCipher(p, SecureApiKeyStore.ASSOCIATED_DATA), s)

    private fun assertFailure(expected: SecureStorageError, result: SecureResult<*>) {
        assertTrue("expected Failure($expected) but was $result", result is SecureResult.Failure)
        assertEquals(expected, (result as SecureResult.Failure).error)
    }

    private fun assertOk(result: SecureResult<*>) =
        assertTrue("expected Success but was $result", result is SecureResult.Success<*>)

    private fun readKey(s: SecureApiKeyStore = store): String =
        ((s.read() as SecureResult.Success).value).reveal()

    // ---- save / read ----

    @Test fun emptyStoreHasNoKeyAndReadIsNotFound() {
        assertFalse(store.hasKey())
        assertFailure(SecureStorageError.NOT_FOUND, store.read())
    }

    @Test fun savedKeyCanBeReadBack() {
        assertOk(store.save(TEST_API_KEY))
        assertTrue(store.hasKey())
        assertEquals(TEST_API_KEY, readKey())
    }

    @Test fun keySurvivesANewStoreInstanceOverTheSameStorageAndKey() {
        store.save(TEST_API_KEY)
        assertEquals(TEST_API_KEY, readKey(newStore()))
    }

    @Test fun surroundingWhitespaceIsTrimmed() {
        store.save("  $TEST_API_KEY \n")
        assertEquals(TEST_API_KEY, readKey())
    }

    @Test fun saveReplacesAnExistingKey() {
        store.save(TEST_API_KEY)
        store.save("AIzaSyOTHER-key")
        assertEquals("AIzaSyOTHER-key", readKey())
    }

    @Test fun invalidKeysAreRejectedAndNothingIsWritten() {
        val tooLong = "a".repeat(ApiKey.MAX_LENGTH + 1)
        for (bad in listOf("", "   ", "ab cd", "ab\ncd", "ab\tcd", "kéy", "\u0000abc", tooLong)) {
            assertFailure(SecureStorageError.INVALID_INPUT, store.save(bad))
            assertFailure(SecureStorageError.INVALID_INPUT, store.update(bad))
        }
        assertEquals(0, storage.writeCount)
        assertFalse(store.hasKey())
    }

    @Test fun maxLengthKeyIsAccepted() {
        val key = "a".repeat(ApiKey.MAX_LENGTH)
        assertOk(store.save(key))
        assertEquals(key, readKey())
    }

    // ---- update ----

    @Test fun updateWithoutAStoredKeyIsNotFound() {
        assertFailure(SecureStorageError.NOT_FOUND, store.update(TEST_API_KEY))
        assertFalse(store.hasKey())
        assertEquals(0, storage.writeCount)
    }

    @Test fun updateReplacesTheStoredKey() {
        store.save(TEST_API_KEY)
        assertOk(store.update("AIzaSyUPDATED-key"))
        assertEquals("AIzaSyUPDATED-key", readKey())
    }

    // ---- delete ----

    @Test fun deleteRemovesTheCiphertextAndTheKeystoreKey() {
        store.save(TEST_API_KEY)
        assertOk(store.delete())
        assertFalse(store.hasKey())
        assertNull(storage.blob)
        assertNull(provider.key)
        assertFailure(SecureStorageError.NOT_FOUND, store.read())
    }

    @Test fun deleteWhenNothingIsStoredSucceeds() {
        assertOk(store.delete())
    }

    @Test fun canSaveAgainAfterDelete() {
        store.save(TEST_API_KEY)
        store.delete()
        assertOk(store.save("AIzaSyAGAIN-key"))
        assertEquals("AIzaSyAGAIN-key", readKey())
    }

    @Test fun failedCiphertextRemovalKeepsTheKeyAndTheData() {
        store.save(TEST_API_KEY)
        storage.failClear = true
        assertFailure(SecureStorageError.STORAGE_FAILURE, store.delete())
        assertNotNull(provider.key)
        assertEquals(TEST_API_KEY, readKey())
    }

    @Test fun failureToDeleteTheKeystoreKeyStillCountsAsDeleted() {
        store.save(TEST_API_KEY)
        provider.failDelete = true
        assertOk(store.delete())
        assertFalse(store.hasKey())
    }

    // ---- no plain text at rest ----

    @Test fun storedBytesDoNotContainThePlainKey() {
        store.save(TEST_API_KEY)
        val blob = storage.blob!!
        assertFalse(blob.containsSequence(TEST_API_KEY.toByteArray(Charsets.UTF_8)))
        assertFalse(blob.containsSequence("AIza".toByteArray()))
    }

    // ---- failures ----

    @Test fun failedWriteReportsStorageFailureAndKeepsTheOldKey() {
        store.save(TEST_API_KEY)
        storage.failWrites = true
        assertFailure(SecureStorageError.STORAGE_FAILURE, store.save("AIzaSyNEW-key"))
        assertFailure(SecureStorageError.STORAGE_FAILURE, store.update("AIzaSyNEW-key"))
        storage.failWrites = false
        assertEquals(TEST_API_KEY, readKey())
    }

    @Test fun tamperedDataIsADecryptionFailure() {
        store.save(TEST_API_KEY)
        val blob = storage.blob!!
        blob[blob.size - 1] = (blob[blob.size - 1].toInt() xor 0x01).toByte()
        val result = store.read()
        assertFailure(SecureStorageError.DECRYPTION_FAILED, result)
        assertFalse(result.toString().contains(TEST_API_KEY))
    }

    @Test fun truncatedOrForeignDataIsCorrupted() {
        store.save(TEST_API_KEY)
        storage.blob = byteArrayOf(1, 2, 3)
        assertFailure(SecureStorageError.CORRUPTED_DATA, store.read())
    }

    @Test fun unreadableStoredTextIsCorrupted() {
        storage.blob = byteArrayOf(1)
        storage.readError = StoredBlobCorruptedException()
        assertFailure(SecureStorageError.CORRUPTED_DATA, store.read())
    }

    @Test fun decryptedGarbageThatIsNotAnApiKeyIsCorrupted() {
        // Encrypted correctly, but the content is not something save() would ever have stored.
        val cipher = AesGcmCipher(provider, SecureApiKeyStore.ASSOCIATED_DATA)
        storage.blob = cipher.encrypt("has spaces".toByteArray())
        assertFailure(SecureStorageError.CORRUPTED_DATA, store.read())
        storage.blob = cipher.encrypt(byteArrayOf(0xC3.toByte()))
        assertFailure(SecureStorageError.CORRUPTED_DATA, store.read())
    }

    @Test fun missingKeystoreKeyWithDataPresentIsKeyUnavailable() {
        store.save(TEST_API_KEY)
        provider.key = null
        assertFailure(SecureStorageError.KEY_UNAVAILABLE, store.read())
        assertTrue(store.hasKey())
        assertNull(provider.key) // reading never invents a key
    }

    @Test fun keyUnavailableIsRecoverableBySavingAgain() {
        store.save(TEST_API_KEY)
        provider.key = null
        assertOk(store.save("AIzaSyRECOVERED-key"))
        assertEquals("AIzaSyRECOVERED-key", readKey())
    }

    @Test fun keyFromADifferentKeystoreKeyIsADecryptionFailure() {
        store.save(TEST_API_KEY)
        provider.key = newSoftwareKey()
        assertFailure(SecureStorageError.DECRYPTION_FAILED, store.read())
    }

    @Test fun storageReadErrorIsStorageFailure() {
        storage.blob = byteArrayOf(1)
        storage.readError = java.io.IOException("disk")
        assertFailure(SecureStorageError.STORAGE_FAILURE, store.read())
    }

    @Test fun unexpectedCipherExceptionsNeverEscapeAndNeverLeakTheirMessage() {
        val exploding = SecureApiKeyStore(ExplodingCipher(), storage)
        val saved = exploding.save(TEST_API_KEY)
        assertFailure(SecureStorageError.ENCRYPTION_FAILED, saved)
        assertFalse(saved.toString().contains(TEST_API_KEY))

        storage.blob = byteArrayOf(1, 2, 3)
        val read = exploding.read()
        assertFailure(SecureStorageError.DECRYPTION_FAILED, read)
        assertFalse(read.toString().contains(TEST_API_KEY))

        assertOk(exploding.delete()) // key deletion failure is tolerated once the data is gone
        assertNull(storage.blob)
    }

    // ---- redaction ----

    @Test fun apiKeyAndResultsNeverPrintTheKey() {
        val key = ApiKey(TEST_API_KEY)
        assertFalse(key.toString().contains(TEST_API_KEY))
        assertFalse(SecureResult.Success(key).toString().contains(TEST_API_KEY))
        assertEquals(TEST_API_KEY, key.reveal())
        assertEquals(ApiKey(TEST_API_KEY), key)
    }

    @Test fun failuresCarryOnlyAClassName() {
        storage.blob = byteArrayOf(1)
        storage.readError = java.io.IOException("secret message $TEST_API_KEY")
        val failure = store.read() as SecureResult.Failure
        assertEquals("java.io.IOException", failure.causeType)
        assertFalse(failure.toString().contains(TEST_API_KEY))
    }
}
