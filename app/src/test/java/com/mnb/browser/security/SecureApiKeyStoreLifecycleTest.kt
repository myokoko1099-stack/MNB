package com.mnb.browser.security

import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 06-A4: Save / Read / Update / Delete as sequences, plus how the store behaves when the
 * storage itself throws. Complements [SecureApiKeyStoreTest], which checks the single operations.
 */
class SecureApiKeyStoreLifecycleTest {
    private val provider = SoftwareKeyProvider()
    private val storage = MemoryBlobStorage()
    private val store = SecureApiKeyStore(AesGcmCipher(provider, SecureApiKeyStore.ASSOCIATED_DATA), storage)

    private fun assertOk(result: SecureResult<*>) =
        assertTrue("expected Success but was $result", result is SecureResult.Success<*>)

    private fun assertFailure(expected: SecureStorageError, result: SecureResult<*>) {
        assertTrue("expected Failure($expected) but was $result", result is SecureResult.Failure)
        assertEquals(expected, (result as SecureResult.Failure).error)
    }

    private fun readKey(): String = (store.read() as SecureResult.Success).value.reveal()

    // ---- sequences ----

    @Test
    fun saveReadUpdateReadDeleteReadSequence() {
        assertFailure(SecureStorageError.NOT_FOUND, store.read())

        assertOk(store.save("AIzaSyFIRST-fake_key_1111111111"))
        assertEquals("AIzaSyFIRST-fake_key_1111111111", readKey())

        assertOk(store.update("AIzaSySECOND-fake_key_222222222"))
        assertEquals("AIzaSySECOND-fake_key_222222222", readKey())

        assertOk(store.delete())
        assertFailure(SecureStorageError.NOT_FOUND, store.read())
        assertFalse(store.hasKey())
    }

    @Test
    fun theWholeCycleCanRunManyTimes() {
        repeat(5) { round ->
            val key = "AIzaSyROUND-fake_key_$round"
            assertOk(store.save(key))
            assertEquals(key, readKey())
            assertOk(store.delete())
            assertFalse(store.hasKey())
            assertNull(provider.secretKey)
        }
    }

    @Test
    fun readingDoesNotWriteAnything() {
        store.save(TEST_API_KEY)
        val written = storage.writeCount
        val blob = storage.blob!!.copyOf()
        repeat(10) { readKey() }
        assertEquals(written, storage.writeCount)
        assertArrayEquals(blob, storage.blob)
    }

    @Test
    fun hasKeyNeedsNoKeystoreKey() {
        store.save(TEST_API_KEY)
        provider.secretKey = null
        assertTrue(store.hasKey())
        assertNull(provider.secretKey)
    }

    @Test
    fun hasKeyIsTrueForAnythingStoredEvenIfItIsGarbage() {
        storage.blob = byteArrayOf(9, 9, 9)
        assertTrue(store.hasKey())
    }

    // ---- save / update details ----

    @Test
    fun savingTheSameKeyTwiceStoresDifferentBytesButTheSameKey() {
        store.save(TEST_API_KEY)
        val first = storage.blob!!.copyOf()
        store.save(TEST_API_KEY)
        val second = storage.blob!!.copyOf()
        assertFalse(first.contentEquals(second)) // fresh IV each time
        assertEquals(TEST_API_KEY, readKey())
    }

    @Test
    fun updateKeepsUsingTheSameKeystoreKey() {
        store.save(TEST_API_KEY)
        val before = provider.secretKey
        assertNotNull(before)
        store.update("AIzaSyUPDATED-fake_key_333333333")
        assertSame(before, provider.secretKey)
        assertEquals(0, provider.deleteCalls)
    }

    @Test
    fun anInvalidUpdateLeavesTheStoredBytesUntouched() {
        store.save(TEST_API_KEY)
        val blob = storage.blob!!.copyOf()
        val writes = storage.writeCount
        assertFailure(SecureStorageError.INVALID_INPUT, store.update("not valid"))
        assertArrayEquals(blob, storage.blob)
        assertEquals(writes, storage.writeCount)
        assertEquals(TEST_API_KEY, readKey())
    }

    @Test
    fun anInvalidSaveLeavesTheStoredBytesUntouched() {
        store.save(TEST_API_KEY)
        val blob = storage.blob!!.copyOf()
        assertFailure(SecureStorageError.INVALID_INPUT, store.save(""))
        assertArrayEquals(blob, storage.blob)
        assertEquals(TEST_API_KEY, readKey())
    }

    @Test
    fun updatingWhenNothingIsStoredDoesNotCreateAKeystoreKey() {
        assertFailure(SecureStorageError.NOT_FOUND, store.update(TEST_API_KEY))
        assertNull(provider.secretKey)
    }

    @Test
    fun aFailedFirstSaveLeavesNothingStored() {
        storage.failWrites = true
        assertFailure(SecureStorageError.STORAGE_FAILURE, store.save(TEST_API_KEY))
        assertFalse(store.hasKey())
        assertFailure(SecureStorageError.NOT_FOUND, store.read())
    }

    @Test
    fun savingWorksAgainOnceTheStorageRecovers() {
        storage.failWrites = true
        store.save(TEST_API_KEY)
        storage.failWrites = false
        assertOk(store.save(TEST_API_KEY))
        assertEquals(TEST_API_KEY, readKey())
    }

    // ---- delete details ----

    @Test
    fun deleteTwiceIsFine() {
        store.save(TEST_API_KEY)
        assertOk(store.delete())
        assertOk(store.delete())
        assertFalse(store.hasKey())
    }

    @Test
    fun aDeleteThatFailedCanBeRetriedAfterTheStorageRecovers() {
        store.save(TEST_API_KEY)
        storage.failClear = true
        assertFailure(SecureStorageError.STORAGE_FAILURE, store.delete())
        assertEquals(TEST_API_KEY, readKey())
        storage.failClear = false
        assertOk(store.delete())
        assertFalse(store.hasKey())
        assertNull(provider.secretKey)
    }

    @Test
    fun deleteRemovesDataThatCanNoLongerBeDecrypted() {
        store.save(TEST_API_KEY)
        provider.secretKey = newSoftwareKey() // data is now unreadable
        assertFailure(SecureStorageError.DECRYPTION_FAILED, store.read())
        assertOk(store.delete())
        assertFalse(store.hasKey())
        assertNull(storage.blob)
    }

    @Test
    fun deleteRemovesGarbageToo() {
        storage.blob = byteArrayOf(1, 2, 3)
        assertOk(store.delete())
        assertFalse(store.hasKey())
    }

    @Test
    fun theOldKeyCannotComeBackAfterDeleteAndANewSave() {
        store.save("AIzaSyOLD-fake_key_4444444444")
        store.delete()
        store.save("AIzaSyNEW-fake_key_5555555555")
        assertEquals("AIzaSyNEW-fake_key_5555555555", readKey())
    }

    // ---- storage that throws ----

    @Test
    fun aStorageThatThrowsOnExistsMakesHasKeyFalseAndUpdateAStorageFailure() {
        val throwing = ThrowingStorage(throwOnExists = true)
        val s = SecureApiKeyStore(AesGcmCipher(SoftwareKeyProvider(), SecureApiKeyStore.ASSOCIATED_DATA), throwing)
        assertFalse(s.hasKey())
        assertFailure(SecureStorageError.STORAGE_FAILURE, s.update(TEST_API_KEY))
    }

    @Test
    fun aStorageThatThrowsOnWriteIsAStorageFailure() {
        val s = SecureApiKeyStore(
            AesGcmCipher(SoftwareKeyProvider(), SecureApiKeyStore.ASSOCIATED_DATA),
            ThrowingStorage(throwOnWrite = true),
        )
        val result = s.save(TEST_API_KEY)
        assertFailure(SecureStorageError.STORAGE_FAILURE, result)
        assertFalse(result.toString().contains(TEST_API_KEY))
    }

    @Test
    fun aStorageThatThrowsOnClearIsAStorageFailureAndTheKeystoreKeyIsKept() {
        val keyProvider = SoftwareKeyProvider()
        val s = SecureApiKeyStore(
            AesGcmCipher(keyProvider, SecureApiKeyStore.ASSOCIATED_DATA),
            ThrowingStorage(throwOnClear = true),
        )
        keyProvider.getOrCreateKey()
        assertFailure(SecureStorageError.STORAGE_FAILURE, s.delete())
        assertNotNull(keyProvider.secretKey)
        assertEquals(0, keyProvider.deleteCalls)
    }

    @Test
    fun aStorageThatThrowsOnReadIsAStorageFailure() {
        val s = SecureApiKeyStore(
            AesGcmCipher(SoftwareKeyProvider(), SecureApiKeyStore.ASSOCIATED_DATA),
            ThrowingStorage(throwOnRead = true),
        )
        assertFailure(SecureStorageError.STORAGE_FAILURE, s.read())
    }

    // ---- several threads ----

    @Test
    fun concurrentSavesAndReadsNeverFailAndNeverMixValues() {
        val keys = (0 until 8).map { "AIzaSyCONCURRENT-fake_key_$it" }
        store.save(keys[0])
        val failures = ConcurrentLinkedQueue<String>()
        val pool = Executors.newFixedThreadPool(8)
        try {
            val tasks = keys.map { key ->
                Callable {
                    repeat(25) {
                        val saved = store.save(key)
                        if (saved !is SecureResult.Success) failures.add("save: $saved")
                        val read = store.read()
                        if (read is SecureResult.Success) {
                            if (read.value.reveal() !in keys) failures.add("read returned a value nobody saved")
                        } else {
                            failures.add("read: $read")
                        }
                    }
                }
            }
            pool.invokeAll(tasks).forEach { it.get() }
        } finally {
            pool.shutdownNow()
        }
        assertTrue(failures.toString(), failures.isEmpty())
        assertTrue(readKey() in keys)
    }

    @Test
    fun concurrentDeletesAndSavesEndInAConsistentState() {
        val failures = ConcurrentLinkedQueue<String>()
        val pool = Executors.newFixedThreadPool(4)
        try {
            val tasks = (0 until 4).map { worker ->
                Callable {
                    repeat(25) {
                        if (worker % 2 == 0) store.save(TEST_API_KEY) else store.delete()
                        val has = store.hasKey()
                        val read = store.read()
                        // Between the two calls another thread may change things, so only check
                        // that nothing came back as an unexpected kind of failure.
                        if (read is SecureResult.Failure && read.error != SecureStorageError.NOT_FOUND) {
                            failures.add("unexpected ${read.error} (hasKey=$has)")
                        }
                    }
                }
            }
            pool.invokeAll(tasks).forEach { it.get() }
        } finally {
            pool.shutdownNow()
        }
        assertTrue(failures.toString(), failures.isEmpty())
        // Whatever the final state is, it must be a valid one.
        val finalRead = store.read()
        assertTrue(
            finalRead is SecureResult.Success ||
                (finalRead is SecureResult.Failure && finalRead.error == SecureStorageError.NOT_FOUND),
        )
    }

    /** A storage whose chosen operations throw an exception whose message holds a secret-like text. */
    private class ThrowingStorage(
        private val throwOnExists: Boolean = false,
        private val throwOnRead: Boolean = false,
        private val throwOnWrite: Boolean = false,
        private val throwOnClear: Boolean = false,
    ) : EncryptedBlobStorage {
        private var blob: ByteArray? = null

        override fun exists(): Boolean {
            if (throwOnExists) throw java.io.IOException("exists failed $TEST_API_KEY")
            return blob != null
        }

        override fun read(): ByteArray? {
            if (throwOnRead) throw java.io.IOException("read failed $TEST_API_KEY")
            return blob?.copyOf()
        }

        override fun write(blob: ByteArray): Boolean {
            if (throwOnWrite) throw java.io.IOException("write failed $TEST_API_KEY")
            this.blob = blob.copyOf()
            return true
        }

        override fun clear(): Boolean {
            if (throwOnClear) throw java.io.IOException("clear failed $TEST_API_KEY")
            blob = null
            return true
        }
    }
}
