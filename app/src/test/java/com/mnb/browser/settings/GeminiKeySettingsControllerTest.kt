package com.mnb.browser.settings

import com.mnb.browser.security.AesGcmCipher
import com.mnb.browser.security.ApiKey
import com.mnb.browser.security.ApiKeyStore
import com.mnb.browser.security.ExplodingCipher
import com.mnb.browser.security.MemoryBlobStorage
import com.mnb.browser.security.SecureApiKeyStore
import com.mnb.browser.security.SecureResult
import com.mnb.browser.security.SecureStorageError
import com.mnb.browser.security.SoftwareKeyProvider
import com.mnb.browser.security.TEST_API_KEY
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 06-A2. Uses the real [SecureApiKeyStore] on in-memory fakes, so no Android classes are needed. */
class GeminiKeySettingsControllerTest {

    private val direct = Executor { it.run() }

    private class Env(
        val provider: SoftwareKeyProvider = SoftwareKeyProvider(),
        val storage: MemoryBlobStorage = MemoryBlobStorage(),
    ) {
        val store = SecureApiKeyStore(
            AesGcmCipher(provider, SecureApiKeyStore.ASSOCIATED_DATA),
            storage,
        )
    }

    private fun controller(env: Env, executor: Executor = direct) =
        GeminiKeySettingsController({ env.store }, executor)

    private fun storedKey(env: Env): String? =
        (env.store.read() as? SecureResult.Success<ApiKey>)?.value?.reveal()

    // 1
    @Test
    fun initialStateIsCheckingAndIdle() {
        val state = controller(Env()).state
        assertEquals(GeminiKeyStatus.CHECKING, state.status)
        assertFalse(state.busy)
        assertNull(state.message)
        assertEquals(0, state.completedChanges)
    }

    // 2
    @Test
    fun refreshWithNothingStoredShowsNotSet() {
        val c = controller(Env())
        c.refresh()
        assertEquals(GeminiKeyStatus.NOT_SET, c.state.status)
        assertFalse(c.state.busy)
    }

    // 3
    @Test
    fun refreshWithStoredKeyShowsSaved() {
        val env = Env()
        env.store.save(TEST_API_KEY)
        val c = controller(env)
        c.refresh()
        assertEquals(GeminiKeyStatus.SAVED, c.state.status)
    }

    // 4
    @Test
    fun savingANewKeyStoresItAndReportsSaved() {
        val env = Env()
        val c = controller(env)
        c.refresh()
        c.save(TEST_API_KEY)
        assertEquals(GeminiKeyStatus.SAVED, c.state.status)
        assertEquals(GeminiKeyMessage.SAVED, c.state.message)
        assertEquals(1, c.state.completedChanges)
        assertEquals(TEST_API_KEY, storedKey(env))
    }

    // 5
    @Test
    fun savingOverAnExistingKeyUpdatesIt() {
        val env = Env()
        val c = controller(env)
        c.refresh()
        c.save(TEST_API_KEY)
        c.save("AIzaSyOTHER-fake_key_9876543210")
        assertEquals(GeminiKeyStatus.SAVED, c.state.status)
        assertEquals(GeminiKeyMessage.UPDATED, c.state.message)
        assertEquals(2, c.state.completedChanges)
        assertEquals("AIzaSyOTHER-fake_key_9876543210", storedKey(env))
    }

    // 6
    @Test
    fun keyWithSpacesIsRejectedAndNothingIsWritten() {
        val env = Env()
        val c = controller(env)
        c.refresh()
        c.save("not a valid key")
        assertEquals(GeminiKeyMessage.INVALID_INPUT, c.state.message)
        assertEquals(GeminiKeyStatus.NOT_SET, c.state.status)
        assertEquals(0, c.state.completedChanges)
        assertEquals(0, env.storage.writeCount)
    }

    // 7
    @Test
    fun blankKeyIsRejected() {
        val env = Env()
        val c = controller(env)
        c.save("   ")
        assertEquals(GeminiKeyMessage.INVALID_INPUT, c.state.message)
        assertFalse(env.store.hasKey())
    }

    // 8
    @Test
    fun invalidUpdateKeepsTheOldKey() {
        val env = Env()
        val c = controller(env)
        c.refresh()
        c.save(TEST_API_KEY)
        c.save("bad key")
        assertEquals(GeminiKeyMessage.INVALID_INPUT, c.state.message)
        assertEquals(GeminiKeyStatus.SAVED, c.state.status)
        assertEquals(TEST_API_KEY, storedKey(env))
    }

    // 9
    @Test
    fun deleteRemovesTheKey() {
        val env = Env()
        env.store.save(TEST_API_KEY)
        val c = controller(env)
        c.refresh()
        c.delete()
        assertEquals(GeminiKeyStatus.NOT_SET, c.state.status)
        assertEquals(GeminiKeyMessage.DELETED, c.state.message)
        assertEquals(1, c.state.completedChanges)
        assertFalse(env.store.hasKey())
    }

    // 10
    @Test
    fun deleteWithNothingStoredStillSucceeds() {
        val c = controller(Env())
        c.refresh()
        c.delete()
        assertEquals(GeminiKeyStatus.NOT_SET, c.state.status)
        assertEquals(GeminiKeyMessage.DELETED, c.state.message)
    }

    // 11
    @Test
    fun failedDeleteKeepsTheKeyAndReportsIt() {
        val env = Env()
        env.store.save(TEST_API_KEY)
        env.storage.failClear = true
        val c = controller(env)
        c.refresh()
        c.delete()
        assertEquals(GeminiKeyMessage.DELETE_FAILED, c.state.message)
        assertEquals(GeminiKeyStatus.SAVED, c.state.status)
        assertEquals(0, c.state.completedChanges)
        assertEquals(TEST_API_KEY, storedKey(env))
    }

    // 12
    @Test
    fun failedSaveReportsFailureWithoutChangingStatus() {
        val env = Env()
        env.storage.failWrites = true
        val c = controller(env)
        c.refresh()
        c.save(TEST_API_KEY)
        assertEquals(GeminiKeyMessage.SAVE_FAILED, c.state.message)
        assertEquals(GeminiKeyStatus.NOT_SET, c.state.status)
        assertEquals(0, c.state.completedChanges)
    }

    // 13
    @Test
    fun savedKeyIsStillThereForANewControllerAfterRestart() {
        val env = Env()
        controller(env).save(TEST_API_KEY)

        // A "restart": a brand-new store and controller on the same Keystore key and storage.
        val restartedStore = SecureApiKeyStore(
            AesGcmCipher(env.provider, SecureApiKeyStore.ASSOCIATED_DATA),
            env.storage,
        )
        val c = GeminiKeySettingsController({ restartedStore }, direct)
        assertEquals(GeminiKeyStatus.CHECKING, c.state.status)
        c.refresh()
        assertEquals(GeminiKeyStatus.SAVED, c.state.status)
        assertEquals(TEST_API_KEY, (restartedStore.read() as SecureResult.Success<ApiKey>).value.reveal())
    }

    // 14
    @Test
    fun storedDataThatCannotBeDecryptedShowsUnreadable() {
        val env = Env()
        env.store.save(TEST_API_KEY)
        env.provider.secretKey = null // the Keystore key is gone
        val c = controller(env)
        c.refresh()
        assertEquals(GeminiKeyStatus.UNREADABLE, c.state.status)
    }

    // 15
    @Test
    fun anUnreadableKeyCanBeReplacedByTypingANewOne() {
        val env = Env()
        env.store.save(TEST_API_KEY)
        env.provider.secretKey = null
        val c = controller(env)
        c.refresh()
        c.save("AIzaSyNEWER-fake_key_5555555555")
        assertEquals(GeminiKeyStatus.SAVED, c.state.status)
        assertEquals(GeminiKeyMessage.SAVED, c.state.message)
        assertEquals("AIzaSyNEWER-fake_key_5555555555", storedKey(env))
    }

    // 16
    @Test
    fun anUnreadableKeyCanBeDeleted() {
        val env = Env()
        env.store.save(TEST_API_KEY)
        env.provider.secretKey = null
        val c = controller(env)
        c.refresh()
        c.delete()
        assertEquals(GeminiKeyStatus.NOT_SET, c.state.status)
        assertFalse(env.store.hasKey())
    }

    // 17
    @Test
    fun stateNeverContainsTheKeyText() {
        val env = Env()
        val c = controller(env)
        c.refresh()
        c.save(TEST_API_KEY)
        assertFalse(c.state.toString().contains(TEST_API_KEY))
        c.save("bad key with spaces")
        assertFalse(c.state.toString().contains("bad key"))
    }

    // 18
    @Test
    fun anExplodingCipherNeverLeaksTheKeyIntoState() {
        val store = SecureApiKeyStore(ExplodingCipher(), MemoryBlobStorage())
        val c = GeminiKeySettingsController({ store }, direct)
        c.refresh()
        c.save(TEST_API_KEY)
        assertEquals(GeminiKeyMessage.SAVE_FAILED, c.state.message)
        assertFalse(c.state.toString().contains(TEST_API_KEY))
        assertFalse(c.state.busy)
    }

    // 19
    @Test
    fun anUnexpectedExceptionFromTheStoreIsReportedWithoutItsMessage() {
        val throwing = object : ApiKeyStore {
            override fun hasKey(): Boolean = throw IllegalStateException("secret $TEST_API_KEY")
            override fun save(apiKey: String): SecureResult<Unit> = throw IllegalStateException("secret $apiKey")
            override fun update(apiKey: String): SecureResult<Unit> = throw IllegalStateException("secret $apiKey")
            override fun read(): SecureResult<ApiKey> = throw IllegalStateException("secret")
            override fun delete(): SecureResult<Unit> = throw IllegalStateException("secret")
        }
        val c = GeminiKeySettingsController({ throwing }, direct)
        c.save(TEST_API_KEY)
        assertEquals(GeminiKeyMessage.SAVE_FAILED, c.state.message)
        assertFalse(c.state.busy)
        assertFalse(c.state.toString().contains(TEST_API_KEY))
    }

    // 20
    @Test
    fun updateFallsBackToSaveWhenTheStoredKeyVanished() {
        val fake = object : ApiKeyStore {
            var saved: String? = null
            override fun hasKey(): Boolean = true
            override fun save(apiKey: String): SecureResult<Unit> {
                saved = apiKey
                return SecureResult.Success(Unit)
            }
            override fun update(apiKey: String): SecureResult<Unit> =
                SecureResult.Failure(SecureStorageError.NOT_FOUND)
            override fun read(): SecureResult<ApiKey> = SecureResult.Success(ApiKey(saved ?: "existing-key"))
            override fun delete(): SecureResult<Unit> = SecureResult.Success(Unit)
        }
        val c = GeminiKeySettingsController({ fake }, direct)
        c.refresh() // status SAVED, so save() tries update() first
        c.save(TEST_API_KEY)
        assertEquals(TEST_API_KEY, fake.saved)
        assertEquals(GeminiKeyStatus.SAVED, c.state.status)
        assertEquals(GeminiKeyMessage.UPDATED, c.state.message)
    }

    // 21
    @Test
    fun theStoreIsOnlyTouchedOnTheExecutor() {
        val queue = ArrayDeque<Runnable>()
        val manual = Executor { queue.addLast(it) }
        var created = 0
        val env = Env()
        val c = GeminiKeySettingsController({ created++; env.store }, manual)

        c.refresh()
        assertEquals(0, created) // nothing ran on the calling thread
        assertEquals(1, queue.size)
        assertTrue(c.state.busy)

        queue.removeFirst().run()
        assertEquals(1, created)
        assertFalse(c.state.busy)
        assertEquals(GeminiKeyStatus.NOT_SET, c.state.status)
    }

    // 22
    @Test
    fun operationsAreIgnoredWhileAnotherOneIsRunning() {
        val queue = ArrayDeque<Runnable>()
        val manual = Executor { queue.addLast(it) }
        val env = Env()
        val c = controller(env, manual)

        c.save(TEST_API_KEY)
        c.save("AIzaSyOTHER-fake_key_9876543210") // ignored: busy
        c.delete() // ignored: busy
        assertEquals(1, queue.size)

        queue.removeFirst().run()
        assertEquals(TEST_API_KEY, storedKey(env))
        assertFalse(c.state.busy)
    }

    // 23
    @Test
    fun aRejectedExecutorLeavesTheControllerUsable() {
        val rejecting = Executor { throw RejectedExecutionException() }
        val c = controller(Env(), rejecting)
        c.save(TEST_API_KEY)
        assertFalse(c.state.busy)
    }

    // 24
    @Test
    fun clearMessageRemovesTheMessageOnly() {
        val c = controller(Env())
        c.save("bad key")
        assertNotNull(c.state.message)
        c.clearMessage()
        assertNull(c.state.message)
        assertEquals(GeminiKeyStatus.NOT_SET, c.state.status)
    }

    // 25
    @Test
    fun onlyFailureMessagesAreMarkedAsErrors() {
        val errors = GeminiKeyMessage.values().filter { it.isError }.toSet()
        assertEquals(
            setOf(GeminiKeyMessage.INVALID_INPUT, GeminiKeyMessage.SAVE_FAILED, GeminiKeyMessage.DELETE_FAILED),
            errors,
        )
    }
}
