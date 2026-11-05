package com.mnb.browser.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 06-A3: the read-only lookup that Phase 06-B will use. */
class ApiKeyProviderTest {

    private class Env {
        val provider = SoftwareKeyProvider()
        val storage = MemoryBlobStorage()
        val store = SecureApiKeyStore(AesGcmCipher(provider, SecureApiKeyStore.ASSOCIATED_DATA), storage)
        val keys = StoreApiKeyProvider(store)
    }

    @Test
    fun savedKeyIsAvailable() {
        val env = Env()
        env.store.save(TEST_API_KEY)
        val result = env.keys.lookup()
        assertTrue(result is ApiKeyLookup.Available)
        assertEquals(TEST_API_KEY, (result as ApiKeyLookup.Available).key.reveal())
    }

    @Test
    fun nothingStoredIsMissing() {
        assertSame(ApiKeyLookup.Missing, Env().keys.lookup())
    }

    @Test
    fun lostKeystoreKeyIsUnavailableAndTheStoredDataIsKept() {
        val env = Env()
        env.store.save(TEST_API_KEY)
        env.provider.secretKey = null
        assertEquals(ApiKeyLookup.Unavailable(SecureStorageError.KEY_UNAVAILABLE), env.keys.lookup())
        assertTrue(env.store.hasKey())
        assertEquals(1, env.storage.writeCount) // only the original save wrote anything
    }

    @Test
    fun unreadableStorageIsUnavailableNotACrash() {
        val env = Env()
        env.store.save(TEST_API_KEY)
        env.storage.readError = java.io.IOException("secret $TEST_API_KEY")
        assertEquals(ApiKeyLookup.Unavailable(SecureStorageError.STORAGE_FAILURE), env.keys.lookup())
    }

    @Test
    fun anUnexpectedExceptionFromTheStoreNeverEscapes() {
        val throwing = object : ApiKeyStore {
            override fun hasKey(): Boolean = true
            override fun save(apiKey: String): SecureResult<Unit> = throw IllegalStateException(apiKey)
            override fun update(apiKey: String): SecureResult<Unit> = throw IllegalStateException(apiKey)
            override fun read(): SecureResult<ApiKey> = throw IllegalStateException("secret $TEST_API_KEY")
            override fun delete(): SecureResult<Unit> = throw IllegalStateException("secret")
        }
        val result = StoreApiKeyProvider(throwing).lookup()
        assertEquals(ApiKeyLookup.Unavailable(SecureStorageError.STORAGE_FAILURE), result)
        assertFalse(result.toString().contains(TEST_API_KEY))
    }

    @Test
    fun lookupResultsNeverPrintTheKey() {
        val env = Env()
        env.store.save(TEST_API_KEY)
        assertFalse(env.keys.lookup().toString().contains(TEST_API_KEY))
        assertEquals("Missing", ApiKeyLookup.Missing.toString())
    }
}
