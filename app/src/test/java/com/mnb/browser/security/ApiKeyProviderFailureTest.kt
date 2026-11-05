package com.mnb.browser.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 06-A4: how [StoreApiKeyProvider] (the lookup Phase 06-B will use) reports each failure,
 * and the proof that it is read-only.
 */
class ApiKeyProviderFailureTest {

    private class Env {
        val provider = SoftwareKeyProvider()
        val storage = MemoryBlobStorage()
        val store = SecureApiKeyStore(AesGcmCipher(provider, SecureApiKeyStore.ASSOCIATED_DATA), storage)
        val keys = StoreApiKeyProvider(store)
    }

    // ---- every failure kind maps to Unavailable(code) ----

    @Test
    fun corruptedBytesAreUnavailableAsCorruptedData() {
        val env = Env()
        env.storage.blob = byteArrayOf(1, 2, 3)
        assertEquals(ApiKeyLookup.Unavailable(SecureStorageError.CORRUPTED_DATA), env.keys.lookup())
    }

    @Test
    fun anUnreadableStoredTextIsUnavailableAsCorruptedData() {
        val env = Env()
        env.storage.blob = byteArrayOf(1)
        env.storage.readError = StoredBlobCorruptedException()
        assertEquals(ApiKeyLookup.Unavailable(SecureStorageError.CORRUPTED_DATA), env.keys.lookup())
    }

    @Test
    fun tamperedDataIsUnavailableAsDecryptionFailed() {
        val env = Env()
        env.store.save(TEST_API_KEY)
        val blob = env.storage.blob!!
        blob[blob.size - 1] = (blob[blob.size - 1].toInt() xor 0x01).toByte()
        assertEquals(ApiKeyLookup.Unavailable(SecureStorageError.DECRYPTION_FAILED), env.keys.lookup())
    }

    @Test
    fun aDifferentKeystoreKeyIsUnavailableAsDecryptionFailed() {
        val env = Env()
        env.store.save(TEST_API_KEY)
        env.provider.secretKey = newSoftwareKey()
        assertEquals(ApiKeyLookup.Unavailable(SecureStorageError.DECRYPTION_FAILED), env.keys.lookup())
    }

    @Test
    fun anExplodingCipherIsUnavailableAndNothingEscapes() {
        val storage = MemoryBlobStorage()
        storage.blob = byteArrayOf(1, 2, 3)
        val result = StoreApiKeyProvider(SecureApiKeyStore(ExplodingCipher(), storage)).lookup()
        assertTrue(result is ApiKeyLookup.Unavailable)
        assertFalse(result.toString().contains(TEST_API_KEY))
    }

    // ---- lookups change nothing ----

    @Test
    fun failedLookupsNeverWriteOrDeleteAnything() {
        val env = Env()
        env.store.save(TEST_API_KEY)
        val blob = env.storage.blob!!.copyOf()
        val writes = env.storage.writeCount

        env.provider.secretKey = newSoftwareKey()
        repeat(3) { env.keys.lookup() }
        env.provider.secretKey = null
        repeat(3) { env.keys.lookup() }

        assertEquals(writes, env.storage.writeCount)
        assertTrue(blob.contentEquals(env.storage.blob))
        assertEquals(0, env.provider.deleteCalls)
        assertTrue(env.store.hasKey())
    }

    @Test
    fun successfulLookupsNeverWriteEither() {
        val env = Env()
        env.store.save(TEST_API_KEY)
        val writes = env.storage.writeCount
        repeat(5) { env.keys.lookup() }
        assertEquals(writes, env.storage.writeCount)
    }

    // ---- follows the store ----

    @Test
    fun repeatedLookupsReturnTheSameKey() {
        val env = Env()
        env.store.save(TEST_API_KEY)
        val first = env.keys.lookup()
        val second = env.keys.lookup()
        assertEquals(first, second)
        assertEquals(ApiKey(TEST_API_KEY), (first as ApiKeyLookup.Available).key)
    }

    @Test
    fun lookupFollowsSaveUpdateAndDelete() {
        val env = Env()
        assertSame(ApiKeyLookup.Missing, env.keys.lookup())

        env.store.save("AIzaSyONE-fake_key_1111111111")
        assertEquals(ApiKey("AIzaSyONE-fake_key_1111111111"), (env.keys.lookup() as ApiKeyLookup.Available).key)

        env.store.update("AIzaSyTWO-fake_key_2222222222")
        assertEquals(ApiKey("AIzaSyTWO-fake_key_2222222222"), (env.keys.lookup() as ApiKeyLookup.Available).key)

        env.store.delete()
        assertSame(ApiKeyLookup.Missing, env.keys.lookup())
    }

    @Test
    fun aUnavailableLookupBecomesAvailableAgainAfterTheKeyIsSavedAgain() {
        val env = Env()
        env.store.save(TEST_API_KEY)
        env.provider.secretKey = null
        assertEquals(ApiKeyLookup.Unavailable(SecureStorageError.KEY_UNAVAILABLE), env.keys.lookup())
        env.store.save("AIzaSyAGAIN-fake_key_8888888888")
        assertTrue(env.keys.lookup() is ApiKeyLookup.Available)
    }

    // ---- read-only contract ----

    @Test
    fun theProviderInterfaceHasOnlyLookup() {
        val names = ApiKeyProvider::class.java.declaredMethods.map { it.name }.toSet()
        assertEquals(setOf("lookup"), names)
    }

    @Test
    fun theStoreBackedProviderHasNoWayToChangeTheKey() {
        val forbidden = setOf("save", "update", "delete", "clear", "write", "put", "set", "remove")
        val names = StoreApiKeyProvider::class.java.methods.map { it.name.lowercase() }
        for (name in names) {
            assertFalse("unexpected public method: $name", forbidden.any { name.startsWith(it) })
        }
    }

    // ---- redaction ----

    @Test
    fun everyLookupResultIsSafeToPrint() {
        val env = Env()
        val results = mutableListOf<ApiKeyLookup>()
        results += env.keys.lookup() // Missing
        env.store.save(TEST_API_KEY)
        results += env.keys.lookup() // Available
        env.provider.secretKey = null
        results += env.keys.lookup() // Unavailable
        for (result in results) {
            val text = result.toString()
            assertFalse(text, text.contains(TEST_API_KEY))
            assertFalse(text, text.contains("AIza"))
        }
        assertEquals("Available(key=ApiKey(***))", results[1].toString())
        assertEquals("Unavailable(error=KEY_UNAVAILABLE)", results[2].toString())
    }
}
