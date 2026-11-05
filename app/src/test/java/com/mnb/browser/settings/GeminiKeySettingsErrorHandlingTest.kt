package com.mnb.browser.settings

import com.mnb.browser.security.ApiKey
import com.mnb.browser.security.ApiKeyStore
import com.mnb.browser.security.SecureResult
import com.mnb.browser.security.SecureStorageError
import com.mnb.browser.security.TEST_API_KEY
import java.io.IOException
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Phase 06-A4: every storage error and every kind of unexpected failure, mapped to a fixed UI state. */
class GeminiKeySettingsErrorHandlingTest {

    private val direct = Executor { it.run() }
    private val newKey = "AIzaSyOTHER-fake_key_9876543210"

    /** A store whose every answer is scripted by the test. */
    private class ScriptedStore : ApiKeyStore {
        var exists = false
        var saveResult: SecureResult<Unit> = SecureResult.Success(Unit)
        var updateResult: SecureResult<Unit> = SecureResult.Success(Unit)
        var readResult: SecureResult<ApiKey> = SecureResult.Success(ApiKey(TEST_API_KEY))
        var deleteResult: SecureResult<Unit> = SecureResult.Success(Unit)

        /** Name of the one function that throws [throwable] instead of answering. */
        var throwIn: String? = null
        var throwable: Throwable? = null

        var saveCalls = 0
        var updateCalls = 0

        private fun maybeThrow(name: String) {
            val t = throwable
            if (throwIn == name && t != null) throw t
        }

        override fun hasKey(): Boolean {
            maybeThrow("hasKey")
            return exists
        }

        override fun save(apiKey: String): SecureResult<Unit> {
            saveCalls++
            maybeThrow("save")
            return saveResult
        }

        override fun update(apiKey: String): SecureResult<Unit> {
            updateCalls++
            maybeThrow("update")
            return updateResult
        }

        override fun read(): SecureResult<ApiKey> {
            maybeThrow("read")
            return readResult
        }

        override fun delete(): SecureResult<Unit> {
            maybeThrow("delete")
            return deleteResult
        }
    }

    private fun controller(store: ScriptedStore, executor: Executor = direct) =
        GeminiKeySettingsController({ store }, executor)

    private fun expectedSaveMessage(error: SecureStorageError): GeminiKeyMessage =
        if (error == SecureStorageError.INVALID_INPUT) GeminiKeyMessage.INVALID_INPUT else GeminiKeyMessage.SAVE_FAILED

    private fun assertNoSecretText(c: GeminiKeySettingsController) {
        val text = c.state.toString()
        assertFalse(text, text.contains("secret"))
        assertFalse(text, text.contains(TEST_API_KEY))
        assertFalse(text, text.contains(newKey))
    }

    // ---- save ----

    @Test
    fun everySaveFailureMapsToAFixedMessageAndNeverCountsAsAChange() {
        for (error in SecureStorageError.values()) {
            val store = ScriptedStore()
            store.saveResult = SecureResult.Failure(error, "SomeException")
            val c = controller(store)
            c.refresh()

            c.save(TEST_API_KEY)

            assertEquals(error.name, expectedSaveMessage(error), c.state.message)
            assertFalse(error.name, c.state.busy)
            assertEquals(error.name, 0, c.state.completedChanges)
            assertEquals(error.name, GeminiKeyStatus.NOT_SET, c.state.status)
            assertEquals(error.name, 1, store.saveCalls)
            assertEquals(error.name, 0, store.updateCalls)
        }
    }

    @Test
    fun everyUpdateFailureMapsToAFixedMessageAndKeepsTheSavedStatus() {
        for (error in SecureStorageError.values()) {
            if (error == SecureStorageError.NOT_FOUND) continue // falls back to save, see the next test
            val store = ScriptedStore()
            store.exists = true
            val c = controller(store)
            c.refresh()
            assertEquals(error.name, GeminiKeyStatus.SAVED, c.state.status)
            store.updateResult = SecureResult.Failure(error)

            c.save(newKey)

            assertEquals(error.name, expectedSaveMessage(error), c.state.message)
            assertEquals(error.name, GeminiKeyStatus.SAVED, c.state.status)
            assertFalse(error.name, c.state.busy)
            assertEquals(error.name, 0, c.state.completedChanges)
            assertEquals(error.name, 1, store.updateCalls)
            assertEquals(error.name, 0, store.saveCalls)
        }
    }

    @Test
    fun anUpdateThatFindsNothingFallsBackToSaveAndCountsOnlyIfThatWorks() {
        val ok = ScriptedStore()
        ok.exists = true
        val c1 = controller(ok)
        c1.refresh()
        ok.updateResult = SecureResult.Failure(SecureStorageError.NOT_FOUND)
        c1.save(newKey)
        assertEquals(1, ok.updateCalls)
        assertEquals(1, ok.saveCalls)
        assertEquals(GeminiKeyMessage.UPDATED, c1.state.message)
        assertEquals(1, c1.state.completedChanges)

        val failing = ScriptedStore()
        failing.exists = true
        val c2 = controller(failing)
        c2.refresh()
        failing.updateResult = SecureResult.Failure(SecureStorageError.NOT_FOUND)
        failing.saveResult = SecureResult.Failure(SecureStorageError.STORAGE_FAILURE)
        c2.save(newKey)
        assertEquals(1, failing.saveCalls)
        assertEquals(GeminiKeyMessage.SAVE_FAILED, c2.state.message)
        assertEquals(0, c2.state.completedChanges)
        assertFalse(c2.state.busy)
    }

    // ---- status check ----

    @Test
    fun everyReadFailureMapsToNotSetOrUnreadableWithoutAMessage() {
        for (error in SecureStorageError.values()) {
            val store = ScriptedStore()
            store.exists = true
            store.readResult = SecureResult.Failure(error)
            val c = controller(store)

            c.refresh()

            val expected = if (error == SecureStorageError.NOT_FOUND) GeminiKeyStatus.NOT_SET else GeminiKeyStatus.UNREADABLE
            assertEquals(error.name, expected, c.state.status)
            assertNull(error.name, c.state.message)
            assertFalse(error.name, c.state.busy)
            assertEquals(error.name, 0, c.state.completedChanges)
        }
    }

    // ---- delete ----

    @Test
    fun everyDeleteFailureGivesDeleteFailedAndTheStatusFromTheStorage() {
        for (error in SecureStorageError.values()) {
            val store = ScriptedStore()
            store.exists = true
            val c = controller(store)
            c.refresh()
            store.deleteResult = SecureResult.Failure(error)

            c.delete()

            assertEquals(error.name, GeminiKeyMessage.DELETE_FAILED, c.state.message)
            assertEquals(error.name, GeminiKeyStatus.SAVED, c.state.status) // the key is still there
            assertFalse(error.name, c.state.busy)
            assertEquals(error.name, 0, c.state.completedChanges)
        }
    }

    @Test
    fun aDeleteFailureWithNothingStoredStillSaysDeleteFailedButNotSet() {
        for (error in SecureStorageError.values()) {
            val store = ScriptedStore()
            store.exists = false
            store.deleteResult = SecureResult.Failure(error)
            val c = controller(store)
            c.refresh()

            c.delete()

            assertEquals(error.name, GeminiKeyMessage.DELETE_FAILED, c.state.message)
            assertEquals(error.name, GeminiKeyStatus.NOT_SET, c.state.status)
            assertEquals(error.name, 0, c.state.completedChanges)
            assertFalse(error.name, c.state.busy)
        }
    }

    // ---- unexpected throwables ----

    private fun throwables(): List<Throwable> = listOf(
        RuntimeException("secret $TEST_API_KEY"),
        IllegalStateException("secret $TEST_API_KEY"),
        IOException("secret $TEST_API_KEY"),
        AssertionError("secret $TEST_API_KEY"),
        LinkageError("secret $TEST_API_KEY"),
    )

    @Test
    fun aThrowableFromAnyStoreFunctionEndsInAFixedStateWithoutKeyText() {
        for (t in throwables()) {
            val label = t.javaClass.simpleName

            // hasKey and read are reached by the status check.
            for (name in listOf("hasKey", "read")) {
                val store = ScriptedStore()
                store.exists = true
                store.throwIn = name
                store.throwable = t
                val c = controller(store)
                c.refresh()
                assertEquals("$label/$name", GeminiKeyStatus.UNREADABLE, c.state.status)
                assertNull("$label/$name", c.state.message)
                assertFalse("$label/$name", c.state.busy)
                assertNoSecretText(c)
            }

            // save: first save of a new key.
            val saving = ScriptedStore()
            saving.throwIn = "save"
            saving.throwable = t
            val c1 = controller(saving)
            c1.save(TEST_API_KEY)
            assertEquals("$label/save", GeminiKeyMessage.SAVE_FAILED, c1.state.message)
            assertFalse("$label/save", c1.state.busy)
            assertEquals("$label/save", 0, c1.state.completedChanges)
            assertNoSecretText(c1)

            // update: a key is already saved.
            val updating = ScriptedStore()
            updating.exists = true
            val c2 = controller(updating)
            c2.refresh()
            updating.throwIn = "update"
            updating.throwable = t
            c2.save(newKey)
            assertEquals("$label/update", GeminiKeyMessage.SAVE_FAILED, c2.state.message)
            assertEquals("$label/update", GeminiKeyStatus.SAVED, c2.state.status)
            assertFalse("$label/update", c2.state.busy)
            assertEquals("$label/update", 0, c2.state.completedChanges)
            assertNoSecretText(c2)

            // delete
            val deleting = ScriptedStore()
            deleting.exists = true
            val c3 = controller(deleting)
            c3.refresh()
            deleting.throwIn = "delete"
            deleting.throwable = t
            c3.delete()
            assertEquals("$label/delete", GeminiKeyMessage.DELETE_FAILED, c3.state.message)
            assertFalse("$label/delete", c3.state.busy)
            assertEquals("$label/delete", 0, c3.state.completedChanges)
            assertNoSecretText(c3)

            // hasKey failing during a save/delete (after the store call) is contained the same way.
            val late = ScriptedStore()
            late.throwIn = "hasKey"
            late.throwable = t
            val c4 = controller(late)
            c4.save(TEST_API_KEY)
            assertEquals("$label/save+check", GeminiKeyMessage.SAVE_FAILED, c4.state.message)
            assertFalse("$label/save+check", c4.state.busy)
            assertEquals("$label/save+check", 0, c4.state.completedChanges)
            assertNoSecretText(c4)
        }
    }

    @Test
    fun anOutOfMemoryErrorIsRethrownButTheControllerIsNotLeftBusy() {
        val store = ScriptedStore()
        store.throwIn = "save"
        store.throwable = OutOfMemoryError("secret $TEST_API_KEY")
        val c = controller(store)

        try {
            c.save(TEST_API_KEY)
            fail("an OutOfMemoryError must not be swallowed")
        } catch (e: OutOfMemoryError) {
            // expected: VirtualMachineError is never contained
        }

        assertFalse(c.state.busy)
        assertEquals(0, c.state.completedChanges)
        assertNoSecretText(c)
    }

    // ---- factory and executor ----

    @Test
    fun aStoreFactoryThatFailsOnceIsRetriedOnTheNextOperation() {
        var calls = 0
        val store = ScriptedStore()
        val c = GeminiKeySettingsController(
            {
                calls++
                if (calls == 1) throw IllegalStateException("secret $TEST_API_KEY")
                store
            },
            direct,
        )

        c.refresh()
        assertEquals(GeminiKeyStatus.UNREADABLE, c.state.status)
        assertNull(c.state.message)
        assertFalse(c.state.busy)
        assertNoSecretText(c)

        c.refresh() // lazy does not remember an exception, so the factory runs again
        assertEquals(2, calls)
        assertEquals(GeminiKeyStatus.NOT_SET, c.state.status)
        assertFalse(c.state.busy)

        c.refresh() // and now the store is cached
        assertEquals(2, calls)
    }

    @Test
    fun aRejectedExecutorLeavesTheDefaultStateAndNotBusy() {
        val rejecting = Executor { throw RejectedExecutionException("shut down") }
        val store = ScriptedStore()

        val refreshing = controller(store, rejecting)
        refreshing.refresh()
        assertEquals(GeminiKeySettingsState(), refreshing.state)
        assertFalse(refreshing.state.busy)

        val deleting = controller(store, rejecting)
        deleting.delete()
        assertEquals(GeminiKeySettingsState(), deleting.state)
        assertFalse(deleting.state.busy)

        val saving = controller(store, rejecting)
        saving.save(TEST_API_KEY)
        assertEquals(GeminiKeySettingsState(), saving.state)
        assertFalse(saving.state.busy)

        assertEquals(0, store.saveCalls)
        assertTrue(store.exists.not())
    }
}
