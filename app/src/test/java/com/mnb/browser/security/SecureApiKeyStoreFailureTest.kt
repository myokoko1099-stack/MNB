package com.mnb.browser.security

import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 06-A4: invalid stored data and decryption failures, read through [SecureApiKeyStore].
 * Every case checks which [SecureStorageError] comes back and that the failed read changed nothing.
 */
class SecureApiKeyStoreFailureTest {
    private val provider = SoftwareKeyProvider()
    private val storage = MemoryBlobStorage()
    private val cipher = AesGcmCipher(provider, SecureApiKeyStore.ASSOCIATED_DATA)
    private val store = SecureApiKeyStore(cipher, storage)

    private fun assertFailure(expected: SecureStorageError, result: SecureResult<*>) {
        assertTrue("expected Failure($expected) but was $result", result is SecureResult.Failure)
        assertEquals(expected, (result as SecureResult.Failure).error)
    }

    private fun assertOk(result: SecureResult<*>) =
        assertTrue("expected Success but was $result", result is SecureResult.Success<*>)

    private fun flipped(bytes: ByteArray, index: Int): ByteArray =
        bytes.copyOf().also { it[index] = (it[index].toInt() xor 0x01).toByte() }

    // ---- stored bytes that are not an envelope ----

    @Test
    fun anEmptyBlobIsCorrupted() {
        storage.blob = ByteArray(0)
        assertFailure(SecureStorageError.CORRUPTED_DATA, store.read())
    }

    @Test
    fun everyBlobShorterThanTheMinimumEnvelopeIsCorrupted() {
        for (size in 0 until 30) {
            storage.blob = ByteArray(size) { 1 }
            assertFailure(SecureStorageError.CORRUPTED_DATA, store.read())
        }
    }

    @Test
    fun aBlobOfRandomLookingBytesIsCorruptedOrFailsAuthentication() {
        storage.blob = ByteArray(64) { (it * 37 + 11).toByte() }
        val result = store.read()
        assertTrue(result is SecureResult.Failure)
        val error = (result as SecureResult.Failure).error
        assertTrue(error == SecureStorageError.CORRUPTED_DATA || error == SecureStorageError.DECRYPTION_FAILED)
    }

    @Test
    fun aWrongVersionByteIsCorrupted() {
        store.save(TEST_API_KEY)
        storage.blob = storage.blob!!.copyOf().also { it[0] = 2 }
        assertFailure(SecureStorageError.CORRUPTED_DATA, store.read())
    }

    @Test
    fun aWrongIvLengthByteIsCorrupted() {
        store.save(TEST_API_KEY)
        storage.blob = storage.blob!!.copyOf().also { it[1] = 16 }
        assertFailure(SecureStorageError.CORRUPTED_DATA, store.read())
    }

    // ---- modified envelopes ----

    @Test
    fun changingAnyByteAfterTheHeaderIsADecryptionFailure() {
        store.save(TEST_API_KEY)
        val original = storage.blob!!.copyOf()
        for (i in 2 until original.size) {
            storage.blob = flipped(original, i)
            assertFailure(SecureStorageError.DECRYPTION_FAILED, store.read())
        }
    }

    @Test
    fun aTruncatedEnvelopeThatStillParsesIsADecryptionFailure() {
        store.save(TEST_API_KEY)
        val original = storage.blob!!
        storage.blob = original.copyOf(original.size - 1)
        assertFailure(SecureStorageError.DECRYPTION_FAILED, store.read())
    }

    @Test
    fun anEnvelopeWithExtraBytesIsADecryptionFailure() {
        store.save(TEST_API_KEY)
        storage.blob = storage.blob!! + byteArrayOf(0)
        assertFailure(SecureStorageError.DECRYPTION_FAILED, store.read())
    }

    @Test
    fun anEnvelopeMadeForAnotherPurposeIsADecryptionFailure() {
        val otherPurpose = AesGcmCipher(provider, "mnb:something_else:v1")
        storage.blob = otherPurpose.encrypt(TEST_API_KEY.toByteArray())
        assertFailure(SecureStorageError.DECRYPTION_FAILED, store.read())
    }

    // ---- authentic envelopes with content that is not an API key ----

    @Test
    fun authenticContentThatIsNotAValidKeyIsCorrupted() {
        val invalid = listOf(
            ByteArray(0), // empty
            "   ".toByteArray(), // blank
            "has spaces".toByteArray(),
            "line\nbreak".toByteArray(),
            "tab\tinside".toByteArray(),
            "a".repeat(ApiKey.MAX_LENGTH + 1).toByteArray(), // too long
            "k\u00E9y".toByteArray(Charsets.UTF_8), // valid UTF-8, not ASCII
            byteArrayOf(0), // NUL
        )
        for (content in invalid) {
            storage.blob = cipher.encrypt(content)
            assertFailure(SecureStorageError.CORRUPTED_DATA, store.read())
        }
    }

    @Test
    fun authenticContentThatIsNotValidUtf8IsCorrupted() {
        val invalid = listOf(
            byteArrayOf(0xFF.toByte(), 0xFE.toByte()),
            byteArrayOf(0xC3.toByte()), // truncated two-byte sequence
            byteArrayOf(0xE1.toByte(), 0x80.toByte()), // truncated three-byte sequence
            byteArrayOf(0xC0.toByte(), 0xAF.toByte()), // overlong encoding
            byteArrayOf('a'.code.toByte(), 0x80.toByte(), 'b'.code.toByte()), // stray continuation byte
        )
        for (content in invalid) {
            storage.blob = cipher.encrypt(content)
            assertFailure(SecureStorageError.CORRUPTED_DATA, store.read())
        }
    }

    @Test
    fun authenticContentThatIsAValidKeyAfterTrimmingStillReads() {
        // Not corruption: parseOrNull trims, and the text inside is a valid key.
        storage.blob = cipher.encrypt("  $TEST_API_KEY \n".toByteArray())
        val result = store.read()
        assertTrue(result is SecureResult.Success)
        assertEquals(TEST_API_KEY, (result as SecureResult.Success).value.reveal())
    }

    // ---- keys that are missing or wrong ----

    @Test
    fun aMissingKeystoreKeyIsKeyUnavailable() {
        store.save(TEST_API_KEY)
        provider.secretKey = null
        assertFailure(SecureStorageError.KEY_UNAVAILABLE, store.read())
    }

    @Test
    fun aKeystoreThatThrowsWhenAskedForTheKeyIsKeyUnavailable() {
        store.save(TEST_API_KEY)
        val throwing = object : KeyProvider {
            override fun getKey(): SecretKey? = throw IllegalStateException("secret $TEST_API_KEY")
            override fun getOrCreateKey(): SecretKey = throw IllegalStateException("secret $TEST_API_KEY")
            override fun deleteKey() {}
        }
        val s = SecureApiKeyStore(AesGcmCipher(throwing, SecureApiKeyStore.ASSOCIATED_DATA), storage)
        val result = s.read()
        assertFailure(SecureStorageError.KEY_UNAVAILABLE, result)
        assertFalse(result.toString().contains(TEST_API_KEY))
        assertFalse(result.toString().contains("secret"))
    }

    @Test
    fun aKeyOfTheWrongSizeIsADecryptionFailure() {
        store.save(TEST_API_KEY)
        provider.secretKey = SecretKeySpec(ByteArray(5), "AES")
        assertFailure(SecureStorageError.DECRYPTION_FAILED, store.read())
    }

    @Test
    fun aDifferentKeyOfTheRightSizeIsADecryptionFailure() {
        store.save(TEST_API_KEY)
        provider.secretKey = newSoftwareKey()
        assertFailure(SecureStorageError.DECRYPTION_FAILED, store.read())
    }

    // ---- a failed read is only a report ----

    @Test
    fun failedReadsNeverChangeTheStoredBytesOrTheKeystoreKey() {
        store.save(TEST_API_KEY)
        val blob = storage.blob!!.copyOf()
        val writes = storage.writeCount
        val keyBefore = provider.secretKey

        provider.secretKey = newSoftwareKey()
        val wrongKey = provider.secretKey
        store.read()
        store.read()

        assertArrayEquals(blob, storage.blob)
        assertEquals(writes, storage.writeCount)
        assertTrue(wrongKey === provider.secretKey) // nothing replaced the key
        assertTrue(keyBefore !== provider.secretKey)
        assertEquals(0, provider.deleteCalls)
        assertTrue(store.hasKey())
    }

    @Test
    fun failedReadsDoNotLeakTheKeyOrTheStoredBytesIntoTheResultText() {
        store.save(TEST_API_KEY)
        val base64 = java.util.Base64.getEncoder().encodeToString(storage.blob!!)
        provider.secretKey = newSoftwareKey()
        val text = store.read().toString()
        assertFalse(text.contains(TEST_API_KEY))
        assertFalse(text.contains(base64))
        assertTrue(text.contains("DECRYPTION_FAILED"))
    }

    @Test
    fun theFailureCarriesOnlyAClassNameOfTheCause() {
        store.save(TEST_API_KEY)
        provider.secretKey = newSoftwareKey()
        val failure = store.read() as SecureResult.Failure
        assertNotNull(failure.causeType)
        assertTrue(failure.causeType!!.startsWith("javax.crypto") || failure.causeType!!.startsWith("java."))
        assertFalse(failure.causeType!!.contains(TEST_API_KEY))
    }

    // ---- recovery ----

    @Test
    fun updateReplacesDataThatCannotBeDecrypted() {
        store.save(TEST_API_KEY)
        provider.secretKey = newSoftwareKey()
        assertFailure(SecureStorageError.DECRYPTION_FAILED, store.read())

        assertOk(store.update("AIzaSyRECOVERED-fake_key_66666666"))
        assertEquals("AIzaSyRECOVERED-fake_key_66666666", (store.read() as SecureResult.Success).value.reveal())
    }

    @Test
    fun saveReplacesCorruptedBytes() {
        storage.blob = byteArrayOf(1, 2, 3)
        assertFailure(SecureStorageError.CORRUPTED_DATA, store.read())
        assertOk(store.save(TEST_API_KEY))
        assertEquals(TEST_API_KEY, (store.read() as SecureResult.Success).value.reveal())
    }

    @Test
    fun saveReplacesDataWhoseKeystoreKeyIsGone() {
        store.save(TEST_API_KEY)
        provider.secretKey = null
        assertFailure(SecureStorageError.KEY_UNAVAILABLE, store.read())
        assertOk(store.save("AIzaSyAFTERLOSS-fake_key_777777"))
        assertEquals("AIzaSyAFTERLOSS-fake_key_777777", (store.read() as SecureResult.Success).value.reveal())
    }

    // ---- rejected input never reaches the cipher ----

    @Test
    fun invalidInputIsRejectedBeforeAnyEncryptionHappens() {
        var encryptCalls = 0
        val counting = object : AeadCipher {
            override fun encrypt(plain: ByteArray): ByteArray {
                encryptCalls++
                return cipher.encrypt(plain)
            }

            override fun decrypt(envelope: ByteArray): ByteArray = cipher.decrypt(envelope)
            override fun deleteKey() = cipher.deleteKey()
        }
        val s = SecureApiKeyStore(counting, storage)
        assertFailure(SecureStorageError.INVALID_INPUT, s.save("bad key"))
        assertFailure(SecureStorageError.INVALID_INPUT, s.update("bad key"))
        assertEquals(0, encryptCalls)
        assertEquals(0, storage.writeCount)
    }
}
