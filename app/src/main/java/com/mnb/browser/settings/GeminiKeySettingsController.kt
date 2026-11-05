package com.mnb.browser.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mnb.browser.security.ApiKeyStore
import com.mnb.browser.security.SecureResult
import com.mnb.browser.security.SecureStorageError
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/** Whether a Gemini API key is stored. The key text itself is never part of any state here. */
enum class GeminiKeyStatus {
    /** The first check has not finished yet. */
    CHECKING,

    /** Nothing is stored. */
    NOT_SET,

    /** A key is stored and can be decrypted. */
    SAVED,

    /** Something is stored but it cannot be decrypted (e.g. the Keystore key is gone). */
    UNREADABLE,
}

/** What happened last. Only a code: the UI picks the wording, and no text from an error is kept. */
enum class GeminiKeyMessage {
    SAVED,
    UPDATED,
    DELETED,
    INVALID_INPUT,
    SAVE_FAILED,
    DELETE_FAILED,
}

val GeminiKeyMessage.isError: Boolean
    get() = when (this) {
        GeminiKeyMessage.SAVED,
        GeminiKeyMessage.UPDATED,
        GeminiKeyMessage.DELETED,
        -> false
        GeminiKeyMessage.INVALID_INPUT,
        GeminiKeyMessage.SAVE_FAILED,
        GeminiKeyMessage.DELETE_FAILED,
        -> true
    }

/**
 * Everything the Gemini key settings section shows. There is deliberately no field for the key:
 * the text being typed lives only in the section's own composable and the stored key never
 * leaves [ApiKeyStore].
 *
 * [completedChanges] goes up after every successful save, update or delete, so the UI can clear
 * what the user typed.
 */
data class GeminiKeySettingsState(
    val status: GeminiKeyStatus = GeminiKeyStatus.CHECKING,
    val busy: Boolean = false,
    val message: GeminiKeyMessage? = null,
    val completedChanges: Int = 0,
)

/**
 * Phase 06-A2: connects the Gemini API key settings UI to the Phase 06-A1 [ApiKeyStore].
 *
 * The store does blocking Keystore and file work, so it is created and used only on [executor]
 * (never on the calling thread). Operations are ignored while another one is running. Nothing here
 * logs, and the state only ever holds status codes. The key passed to [save] goes straight to the
 * store and is not kept.
 *
 * [storeFactory] runs lazily, on the executor, the first time the store is needed.
 *
 * Phase 06-A3 hardening: state changes are made under one lock (the UI thread and the executor
 * both write it); a delete is reported as done only after the storage is checked again; any
 * unexpected failure on the executor ends in a fixed message code, never a crash or exception text.
 */
class GeminiKeySettingsController(
    storeFactory: () -> ApiKeyStore,
    private val executor: Executor,
) {
    private val store: ApiKeyStore by lazy(storeFactory)

    var state: GeminiKeySettingsState by mutableStateOf(GeminiKeySettingsState())
        private set

    private val stateLock = Any()

    /**
     * Applies [transform] to the state as one step. Only cheap work goes inside: Keystore and file
     * work is always done before the call, so the UI thread is never blocked behind it.
     */
    private fun update(transform: (GeminiKeySettingsState) -> GeminiKeySettingsState) {
        synchronized(stateLock) { state = transform(state) }
    }

    /** Re-reads whether a key is stored (and whether it can still be decrypted). */
    fun refresh() {
        launch(failureMessage = null) {
            val status = checkStatus()
            update { it.copy(status = status) }
        }
    }

    /**
     * Stores [input]: replaces the key when one is already saved, otherwise saves a new one.
     * Invalid text (empty, too long, spaces or non-ASCII) is rejected by the store.
     */
    fun save(input: String) {
        launch(GeminiKeyMessage.SAVE_FAILED) {
            val replacing = state.status == GeminiKeyStatus.SAVED
            var result = if (replacing) store.update(input) else store.save(input)
            if (replacing && result is SecureResult.Failure && result.error == SecureStorageError.NOT_FOUND) {
                // The stored key disappeared since the last check; fall back to a plain save.
                result = store.save(input)
            }
            // A failed save or update never touches the stored key, so the status is read again
            // from the storage to show what is really there.
            val status = checkStatus()
            val outcome = result
            if (outcome is SecureResult.Success) {
                update {
                    it.copy(
                        status = status,
                        message = if (replacing) GeminiKeyMessage.UPDATED else GeminiKeyMessage.SAVED,
                        completedChanges = it.completedChanges + 1,
                    )
                }
            } else {
                val message = if ((outcome as SecureResult.Failure).error == SecureStorageError.INVALID_INPUT) {
                    GeminiKeyMessage.INVALID_INPUT
                } else {
                    GeminiKeyMessage.SAVE_FAILED
                }
                update { it.copy(status = status, message = message) }
            }
        }
    }

    /** Removes the stored key. Succeeds when nothing is stored. */
    fun delete() {
        launch(GeminiKeyMessage.DELETE_FAILED) {
            val deleted = store.delete() is SecureResult.Success
            // Do not trust the result alone: look at the storage again, so the screen only says
            // "deleted" when nothing is stored any more.
            val status = checkStatus()
            if (deleted && status == GeminiKeyStatus.NOT_SET) {
                update {
                    it.copy(
                        status = GeminiKeyStatus.NOT_SET,
                        message = GeminiKeyMessage.DELETED,
                        completedChanges = it.completedChanges + 1,
                    )
                }
            } else {
                update { it.copy(status = status, message = GeminiKeyMessage.DELETE_FAILED) }
            }
        }
    }

    /** Hides the last message (the UI calls this when the user edits the input). */
    fun clearMessage() {
        update { if (it.message != null) it.copy(message = null) else it }
    }

    /**
     * Checks existence first (cheap, cannot fail on a bad key), then decrypts once to be sure the
     * key is usable. The decrypted key is dropped immediately.
     */
    private fun checkStatus(): GeminiKeyStatus {
        if (!store.hasKey()) return GeminiKeyStatus.NOT_SET
        return when (val result = store.read()) {
            is SecureResult.Success -> GeminiKeyStatus.SAVED
            is SecureResult.Failure ->
                if (result.error == SecureStorageError.NOT_FOUND) GeminiKeyStatus.NOT_SET
                else GeminiKeyStatus.UNREADABLE
        }
    }

    /**
     * Runs [block] on the executor unless another operation is running. If [block] fails
     * unexpectedly the state gets [failureMessage] (or no message when null), and the first check
     * is not left on "Checking" forever. Only [VirtualMachineError] (e.g. out of memory) is not
     * swallowed.
     */
    private fun launch(failureMessage: GeminiKeyMessage?, block: () -> Unit) {
        val started = synchronized(stateLock) {
            if (state.busy) {
                false
            } else {
                state = state.copy(busy = true)
                true
            }
        }
        if (!started) return
        try {
            executor.execute {
                try {
                    block()
                } catch (e: Throwable) {
                    if (e is VirtualMachineError) throw e
                    // Deliberately no message, cause or stack trace: it could mention the key.
                    update {
                        it.copy(
                            status = if (it.status == GeminiKeyStatus.CHECKING) GeminiKeyStatus.UNREADABLE else it.status,
                            message = failureMessage ?: it.message,
                        )
                    }
                } finally {
                    update { it.copy(busy = false) }
                }
            }
        } catch (e: RejectedExecutionException) {
            update { it.copy(busy = false) }
        }
    }
}
