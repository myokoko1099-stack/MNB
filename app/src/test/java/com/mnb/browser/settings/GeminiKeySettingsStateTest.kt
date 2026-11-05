package com.mnb.browser.settings

import com.mnb.browser.security.AesGcmCipher
import com.mnb.browser.security.ApiKey
import com.mnb.browser.security.ApiKeyStore
import com.mnb.browser.security.MemoryBlobStorage
import com.mnb.browser.security.SecureApiKeyStore
import com.mnb.browser.security.SecureResult
import com.mnb.browser.security.SoftwareKeyProvider
import com.mnb.browser.security.TEST_API_KEY
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 06-A4: the UI state, status/message transitions and executor behaviour of the controller. */
class GeminiKeySettingsStateTest {

    private val direct = Executor { it.run() }
    private val otherKey = "AIzaSyOTHER-fake_key_9876543210"

    private class Env {
        val provider = SoftwareKeyProvider()
        val storage = MemoryBlobStorage()
        val store = SecureApiKeyStore(AesGcmCipher(provider, SecureApiKeyStore.ASSOCIATED_DATA), storage)
    }

    /** Delegates to a real store; the status check (hasKey) can be made to blow up. */
    private class FlakyStore(private val inner: ApiKeyStore) : ApiKeyStore by inner {
        var failChecks = false
        override fun hasKey(): Boolean {
            if (failChecks) throw IllegalStateException("secret check failure")
            return inner.hasKey()
        }
    }

    /** Delegates to a real store and records the name of the thread every call runs on. */
    private class RecordingStore(
        private val inner: ApiKeyStore,
        private val names: MutableList<String>,
    ) : ApiKeyStore {
        override fun hasKey(): Boolean {
            names.add(Thread.currentThread().name)
            return inner.hasKey()
        }

        override fun save(apiKey: String): SecureResult<Unit> {
            names.add(Thread.currentThread().name)
            return inner.save(apiKey)
        }

        override fun update(apiKey: String): SecureResult<Unit> {
            names.add(Thread.currentThread().name)
            return inner.update(apiKey)
        }

        override fun read(): SecureResult<ApiKey> {
            names.add(Thread.currentThread().name)
            return inner.read()
        }

        override fun delete(): SecureResult<Unit> {
            names.add(Thread.currentThread().name)
            return inner.delete()
        }
    }

    // ---- plain state ----

    @Test
    fun defaultStateIsCheckingAndIdle() {
        val s = GeminiKeySettingsState()
        assertEquals(GeminiKeyStatus.CHECKING, s.status)
        assertFalse(s.busy)
        assertNull(s.message)
        assertEquals(0, s.completedChanges)
    }

    @Test
    fun copyAndEqualsFollowTheFields() {
        val a = GeminiKeySettingsState()
        assertEquals(a, GeminiKeySettingsState())
        assertEquals(a.hashCode(), GeminiKeySettingsState().hashCode())
        val b = a.copy(status = GeminiKeyStatus.SAVED, message = GeminiKeyMessage.SAVED, completedChanges = 1)
        assertNotEquals(a, b)
        assertEquals(GeminiKeyStatus.CHECKING, a.status) // the original is untouched
        assertEquals(b, b.copy())
        assertEquals(a, b.copy(status = GeminiKeyStatus.CHECKING, message = null, completedChanges = 0))
    }

    @Test
    fun theStateHasNoFieldThatCouldHoldKeyText() {
        val fields = GeminiKeySettingsState::class.java.declaredFields.filter { !Modifier.isStatic(it.modifiers) }
        assertTrue(fields.isNotEmpty())
        for (field in fields) {
            assertNotEquals("field ${field.name}", String::class.java, field.type)
            assertNotEquals("field ${field.name}", ApiKey::class.java, field.type)
            assertNotEquals("field ${field.name}", CharSequence::class.java, field.type)
            assertNotEquals("field ${field.name}", CharArray::class.java, field.type)
            assertNotEquals("field ${field.name}", ByteArray::class.java, field.type)
        }
    }

    @Test
    fun statusAndMessageEnumsHaveExactlyTheExpectedValues() {
        assertEquals(
            setOf("CHECKING", "NOT_SET", "SAVED", "UNREADABLE"),
            GeminiKeyStatus.values().map { it.name }.toSet(),
        )
        assertEquals(
            setOf("SAVED", "UPDATED", "DELETED", "INVALID_INPUT", "SAVE_FAILED", "DELETE_FAILED"),
            GeminiKeyMessage.values().map { it.name }.toSet(),
        )
    }

    @Test
    fun onlyTheThreeFailureMessagesAreErrors() {
        val errors = setOf(
            GeminiKeyMessage.INVALID_INPUT,
            GeminiKeyMessage.SAVE_FAILED,
            GeminiKeyMessage.DELETE_FAILED,
        )
        for (m in GeminiKeyMessage.values()) {
            assertEquals(m.name, m in errors, m.isError)
        }
        assertFalse(GeminiKeyMessage.SAVED.isError)
        assertFalse(GeminiKeyMessage.UPDATED.isError)
        assertFalse(GeminiKeyMessage.DELETED.isError)
    }

    // ---- completedChanges ----

    @Test
    fun completedChangesCountsOnlySuccessfulChanges() {
        val env = Env()
        val c = GeminiKeySettingsController({ env.store }, direct)
        c.refresh()
        assertEquals(0, c.state.completedChanges)

        c.save(TEST_API_KEY)
        assertEquals(1, c.state.completedChanges)

        c.save("bad key")
        assertEquals(GeminiKeyMessage.INVALID_INPUT, c.state.message)
        assertEquals(1, c.state.completedChanges)

        c.save(otherKey)
        assertEquals(2, c.state.completedChanges)

        env.storage.failWrites = true
        c.save(TEST_API_KEY)
        assertEquals(GeminiKeyMessage.SAVE_FAILED, c.state.message)
        assertEquals(2, c.state.completedChanges)
        env.storage.failWrites = false

        c.delete()
        assertEquals(3, c.state.completedChanges)

        c.delete() // nothing stored: still a success
        assertEquals(GeminiKeyMessage.DELETED, c.state.message)
        assertEquals(4, c.state.completedChanges)
    }

    // ---- message handling ----

    @Test
    fun refreshKeepsThePreviousMessage() {
        val env = Env()
        val c = GeminiKeySettingsController({ env.store }, direct)
        c.refresh()
        c.save(TEST_API_KEY)
        assertEquals(GeminiKeyMessage.SAVED, c.state.message)

        c.refresh()

        assertEquals(GeminiKeyMessage.SAVED, c.state.message)
        assertEquals(GeminiKeyStatus.SAVED, c.state.status)
        assertEquals(1, c.state.completedChanges)
    }

    @Test
    fun aNewMessageReplacesTheOldOne() {
        val env = Env()
        val c = GeminiKeySettingsController({ env.store }, direct)
        c.refresh()
        c.save(TEST_API_KEY)
        assertEquals(GeminiKeyMessage.SAVED, c.state.message)

        c.save("bad key")

        assertEquals(GeminiKeyMessage.INVALID_INPUT, c.state.message)
    }

    @Test
    fun clearMessageWithoutAMessageKeepsTheSameStateInstance() {
        val env = Env()
        val c = GeminiKeySettingsController({ env.store }, direct)
        c.refresh()
        val before = c.state
        assertNull(before.message)

        c.clearMessage()

        assertSame(before, c.state)
    }

    // ---- status transitions ----

    @Test
    fun statusMovesFromCheckingToNotSetToSavedAndBackToNotSet() {
        val env = Env()
        val c = GeminiKeySettingsController({ env.store }, direct)
        assertEquals(GeminiKeyStatus.CHECKING, c.state.status)
        c.refresh()
        assertEquals(GeminiKeyStatus.NOT_SET, c.state.status)
        c.save(TEST_API_KEY)
        assertEquals(GeminiKeyStatus.SAVED, c.state.status)
        c.delete()
        assertEquals(GeminiKeyStatus.NOT_SET, c.state.status)
    }

    @Test
    fun aLostKeystoreKeyMakesASavedKeyUnreadableAndSavingFixesIt() {
        val env = Env()
        val c = GeminiKeySettingsController({ env.store }, direct)
        c.refresh()
        c.save(TEST_API_KEY)
        assertEquals(GeminiKeyStatus.SAVED, c.state.status)

        env.provider.secretKey = null // e.g. device restore: the blob is there, the Keystore key is not
        c.refresh()
        assertEquals(GeminiKeyStatus.UNREADABLE, c.state.status)

        c.save(otherKey)
        assertEquals(GeminiKeyStatus.SAVED, c.state.status)
        assertEquals(GeminiKeyMessage.SAVED, c.state.message) // not UPDATED: the old key was unusable
    }

    @Test
    fun savingWhileStillCheckingWithAStoredKeyReportsSavedNotUpdated() {
        val env = Env()
        env.store.save(TEST_API_KEY)
        val c = GeminiKeySettingsController({ env.store }, direct)
        assertEquals(GeminiKeyStatus.CHECKING, c.state.status)

        c.save(otherKey)

        assertEquals(GeminiKeyMessage.SAVED, c.state.message)
        assertEquals(GeminiKeyStatus.SAVED, c.state.status)
        assertEquals(1, c.state.completedChanges)
    }

    @Test
    fun aFailingRefreshKeepsSavedAndTheMessage() {
        val env = Env()
        val flaky = FlakyStore(env.store)
        val c = GeminiKeySettingsController({ flaky }, direct)
        c.refresh()
        c.save(TEST_API_KEY)
        assertEquals(GeminiKeyStatus.SAVED, c.state.status)
        assertEquals(GeminiKeyMessage.SAVED, c.state.message)

        flaky.failChecks = true
        c.refresh()

        assertEquals(GeminiKeyStatus.SAVED, c.state.status) // only CHECKING falls back to UNREADABLE
        assertEquals(GeminiKeyMessage.SAVED, c.state.message)
        assertEquals(1, c.state.completedChanges)
        assertFalse(c.state.busy)
    }

    // ---- executors ----

    @Test
    fun whileASaveIsQueuedTheStateIsBusyAndOtherwiseUnchanged() {
        val env = Env()
        val queue = mutableListOf<Runnable>()
        val c = GeminiKeySettingsController({ env.store }, Executor { queue.add(it) })

        c.save(TEST_API_KEY)

        assertEquals(1, queue.size)
        assertTrue(c.state.busy)
        assertEquals(GeminiKeyStatus.CHECKING, c.state.status)
        assertNull(c.state.message)
        assertEquals(0, c.state.completedChanges)
        assertFalse(env.storage.exists()) // nothing ran yet

        c.save(otherKey) // ignored while busy
        c.delete()
        c.refresh()
        assertEquals(1, queue.size)

        queue.removeAt(0).run()

        assertFalse(c.state.busy)
        assertEquals(GeminiKeyStatus.SAVED, c.state.status)
        assertEquals(GeminiKeyMessage.SAVED, c.state.message)
        assertEquals(1, c.state.completedChanges)
        assertTrue(queue.isEmpty())
    }

    @Test
    fun theStoreIsOnlyTouchedOnTheExecutorThread() {
        val threadName = "mnb-test-key-settings"
        val pool = Executors.newSingleThreadExecutor { r -> Thread(r, threadName) }
        try {
            val env = Env()
            val names = Collections.synchronizedList(mutableListOf<String>())
            val c = GeminiKeySettingsController(
                {
                    names.add(Thread.currentThread().name) // the factory too
                    RecordingStore(env.store, names)
                },
                pool,
            )
            val waitIdle = { pool.submit(Runnable { }).get(5, TimeUnit.SECONDS) }

            c.refresh()
            waitIdle()
            c.save(TEST_API_KEY)
            waitIdle()
            c.delete()
            waitIdle()

            assertEquals(GeminiKeyStatus.NOT_SET, c.state.status)
            assertEquals(2, c.state.completedChanges)
            assertFalse(c.state.busy)
            assertTrue(names.isNotEmpty())
            assertEquals(setOf(threadName), names.toSet())
            assertNotEquals(threadName, Thread.currentThread().name)
        } finally {
            pool.shutdownNow()
        }
    }
}
