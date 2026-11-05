package com.mnb.browser.settings

import com.mnb.browser.security.AesGcmCipher
import com.mnb.browser.security.ApiKey
import com.mnb.browser.security.ApiKeyStore
import com.mnb.browser.security.MemoryBlobStorage
import com.mnb.browser.security.SecureApiKeyStore
import com.mnb.browser.security.SecureResult
import com.mnb.browser.security.SoftwareKeyProvider
import com.mnb.browser.security.TEST_API_KEY
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Phase 06-A3: failure and consistency cases on top of the Phase 06-A2 controller tests. */
class GeminiKeySettingsControllerHardeningTest {

    private val direct = Executor { it.run() }

    private class Env {
        val provider = SoftwareKeyProvider()
        val storage = MemoryBlobStorage()
        val store = SecureApiKeyStore(AesGcmCipher(provider, SecureApiKeyStore.ASSOCIATED_DATA), storage)
    }

    private fun storedKey(env: Env): String? =
        (env.store.read() as? SecureResult.Success<ApiKey>)?.value?.reveal()

    @Test
    fun failedUpdateKeepsTheOldKeyAndTheScreenStillSaysSaved() {
        val env = Env()
        val c = GeminiKeySettingsController({ env.store }, direct)
        c.refresh()
        c.save(TEST_API_KEY)
        env.storage.failWrites = true

        c.save("AIzaSyOTHER-fake_key_9876543210")

        assertEquals(GeminiKeyMessage.SAVE_FAILED, c.state.message)
        assertEquals(GeminiKeyStatus.SAVED, c.state.status)
        assertEquals(1, c.state.completedChanges) // the failed update did not count as a change
        assertEquals(TEST_API_KEY, storedKey(env))
    }

    @Test
    fun deleteIsOnlyReportedWhenNothingIsStoredAnyMore() {
        // A store that says "deleted" but still holds the key.
        val lying = object : ApiKeyStore {
            override fun hasKey(): Boolean = true
            override fun save(apiKey: String): SecureResult<Unit> = SecureResult.Success(Unit)
            override fun update(apiKey: String): SecureResult<Unit> = SecureResult.Success(Unit)
            override fun read(): SecureResult<ApiKey> = SecureResult.Success(ApiKey(TEST_API_KEY))
            override fun delete(): SecureResult<Unit> = SecureResult.Success(Unit)
        }
        val c = GeminiKeySettingsController({ lying }, direct)
        c.refresh()
        c.delete()
        assertEquals(GeminiKeyMessage.DELETE_FAILED, c.state.message)
        assertEquals(GeminiKeyStatus.SAVED, c.state.status)
        assertEquals(0, c.state.completedChanges)
    }

    @Test
    fun deleteCleansTheStorageAndOldDataCannotComeBack() {
        val env = Env()
        val c = GeminiKeySettingsController({ env.store }, direct)
        c.refresh()
        c.save(TEST_API_KEY)
        c.delete()
        assertEquals(GeminiKeyStatus.NOT_SET, c.state.status)
        assertFalse(env.storage.exists())
        assertNull(env.provider.secretKey)
    }

    @Test
    fun anExceptionDuringDeleteGivesDeleteFailedNotSaveFailed() {
        val throwing = object : ApiKeyStore {
            override fun hasKey(): Boolean = true
            override fun save(apiKey: String): SecureResult<Unit> = SecureResult.Success(Unit)
            override fun update(apiKey: String): SecureResult<Unit> = SecureResult.Success(Unit)
            override fun read(): SecureResult<ApiKey> = SecureResult.Success(ApiKey(TEST_API_KEY))
            override fun delete(): SecureResult<Unit> = throw IllegalStateException("secret $TEST_API_KEY")
        }
        val c = GeminiKeySettingsController({ throwing }, direct)
        c.refresh()
        c.delete()
        assertEquals(GeminiKeyMessage.DELETE_FAILED, c.state.message)
        assertFalse(c.state.busy)
        assertFalse(c.state.toString().contains(TEST_API_KEY))
    }

    @Test
    fun anErrorThrownByTheStoreIsAlsoContained() {
        val failing = object : ApiKeyStore {
            override fun hasKey(): Boolean = throw LinkageError("secret $TEST_API_KEY")
            override fun save(apiKey: String): SecureResult<Unit> = throw LinkageError("secret $apiKey")
            override fun update(apiKey: String): SecureResult<Unit> = throw LinkageError("secret $apiKey")
            override fun read(): SecureResult<ApiKey> = throw LinkageError("secret")
            override fun delete(): SecureResult<Unit> = throw LinkageError("secret")
        }
        val c = GeminiKeySettingsController({ failing }, direct)
        c.save(TEST_API_KEY)
        assertEquals(GeminiKeyMessage.SAVE_FAILED, c.state.message)
        assertFalse(c.state.busy)
    }

    @Test
    fun aFailedFirstCheckDoesNotLeaveTheScreenOnChecking() {
        val failing = object : ApiKeyStore {
            override fun hasKey(): Boolean = throw IllegalStateException("secret")
            override fun save(apiKey: String): SecureResult<Unit> = SecureResult.Success(Unit)
            override fun update(apiKey: String): SecureResult<Unit> = SecureResult.Success(Unit)
            override fun read(): SecureResult<ApiKey> = throw IllegalStateException("secret")
            override fun delete(): SecureResult<Unit> = SecureResult.Success(Unit)
        }
        val c = GeminiKeySettingsController({ failing }, direct)
        c.refresh()
        assertEquals(GeminiKeyStatus.UNREADABLE, c.state.status)
        assertNull(c.state.message)
        assertFalse(c.state.busy)
    }

    @Test
    fun clearMessageNeverLosesAFinishedOperationsResult() {
        val env = Env()
        val c = GeminiKeySettingsController({ env.store }, direct)
        c.refresh()
        c.save(TEST_API_KEY)
        c.clearMessage()
        assertNull(c.state.message)
        assertEquals(GeminiKeyStatus.SAVED, c.state.status)
        assertEquals(1, c.state.completedChanges)
    }
}
