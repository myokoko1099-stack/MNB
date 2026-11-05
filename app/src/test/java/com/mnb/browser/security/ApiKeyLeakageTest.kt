package com.mnb.browser.security

import com.mnb.browser.settings.GeminiKeySettingsController
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.PrintStream
import java.util.concurrent.Executor
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger
import javax.crypto.SecretKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 06-A4: tests that the API key does not leak. Four angles:
 * 1. at rest: the stored bytes never hold the key;
 * 2. in output: nothing reaches stdout, stderr or java.util.logging;
 * 3. in results and states: poisoned components (whose exception messages hold the key) never
 *    get their text into anything the app keeps or shows;
 * 4. in the code: a source scan that fails if logging, saved-state storage or an unexpected reader
 *    of the key is added.
 *
 * Every key used here is a fake.
 */
class ApiKeyLeakageTest {

    private val direct = Executor { it.run() }

    // ================= 1. at rest =================

    @Test
    fun storedBytesHoldNoPartOfTheKey() {
        val keys = listOf(TEST_API_KEY, longKey())
        for (key in keys) {
            val provider = SoftwareKeyProvider()
            val storage = MemoryBlobStorage()
            val store = SecureApiKeyStore(AesGcmCipher(provider, SecureApiKeyStore.ASSOCIATED_DATA), storage)
            assertTrue(store.save(key) is SecureResult.Success)

            val blob = storage.blob!!
            val keyBytes = key.toByteArray(Charsets.UTF_8)
            assertFalse(blob.containsSequence(keyBytes))
            // No run of 8 consecutive key bytes appears anywhere in the stored bytes.
            for (start in 0..(keyBytes.size - 8)) {
                assertFalse(
                    "key bytes at $start found in storage",
                    blob.containsSequence(keyBytes.copyOfRange(start, start + 8)),
                )
            }
            // Nor does the Base64 text that ends up in the preferences file.
            val stored = java.util.Base64.getEncoder().encodeToString(blob)
            assertFalse(stored.contains(key))
            assertFalse(stored.contains(java.util.Base64.getEncoder().encodeToString(keyBytes)))
        }
    }

    @Test
    fun theKeystoreKeyIsNotPartOfTheStoredBytes() {
        val provider = SoftwareKeyProvider()
        val storage = MemoryBlobStorage()
        SecureApiKeyStore(AesGcmCipher(provider, SecureApiKeyStore.ASSOCIATED_DATA), storage).save(TEST_API_KEY)
        val aesKey = provider.secretKey!!.encoded
        assertFalse(storage.blob!!.containsSequence(aesKey))
    }

    @Test
    fun anUpdateLeavesNoTraceOfTheOldKeyInTheStoredBytes() {
        val provider = SoftwareKeyProvider()
        val storage = MemoryBlobStorage()
        val store = SecureApiKeyStore(AesGcmCipher(provider, SecureApiKeyStore.ASSOCIATED_DATA), storage)
        store.save(TEST_API_KEY)
        store.update("AIzaSyNEWER-fake_key_9999999999")
        val blob = storage.blob!!
        assertFalse(blob.containsSequence(TEST_API_KEY.toByteArray()))
        assertFalse(blob.containsSequence("AIzaSyNEWER-fake_key_9999999999".toByteArray()))
    }

    // ================= 2. console and logging =================

    @Test
    fun theWholeFlowPrintsAndLogsNothingThatContainsTheKey() {
        val captured = captureOutput { exerciseEverything() }
        assertFalse(captured, captured.contains(TEST_API_KEY))
        assertFalse(captured, captured.contains(OTHER_API_KEY))
        assertFalse(captured, captured.contains("AIzaSy"))
        assertFalse(captured, captured.contains("secret"))
    }

    @Test
    fun theWholeFlowIsSilentOnTheConsole() {
        // Stronger than "no key": the secure-storage code is not expected to print at all.
        val captured = captureOutput { exerciseEverything() }
        assertEquals("unexpected console or log output: $captured", "", captured)
    }

    // ================= 3. poisoned components =================

    @Test
    fun exceptionMessagesFromAPoisonedKeystoreNeverReachResultsOrState() {
        val storage = MemoryBlobStorage()
        val store = SecureApiKeyStore(AesGcmCipher(PoisonedKeyProvider(), SecureApiKeyStore.ASSOCIATED_DATA), storage)
        assertNoSecretText(allTextsFor(store))
    }

    @Test
    fun exceptionMessagesFromAPoisonedStorageNeverReachResultsOrState() {
        val store = SecureApiKeyStore(
            AesGcmCipher(SoftwareKeyProvider(), SecureApiKeyStore.ASSOCIATED_DATA),
            PoisonedStorage(),
        )
        assertNoSecretText(allTextsFor(store))
    }

    @Test
    fun exceptionMessagesFromAPoisonedCipherNeverReachResultsOrState() {
        val storage = MemoryBlobStorage()
        storage.blob = byteArrayOf(1, 2, 3)
        assertNoSecretText(allTextsFor(SecureApiKeyStore(ExplodingCipher(), storage)))
    }

    @Test
    fun exceptionMessagesFromAPoisonedStoreNeverReachTheControllerState() {
        val poisoned = object : ApiKeyStore {
            override fun hasKey(): Boolean = throw IllegalStateException(POISON)
            override fun save(apiKey: String): SecureResult<Unit> = throw IllegalStateException("$POISON $apiKey")
            override fun update(apiKey: String): SecureResult<Unit> = throw IllegalStateException("$POISON $apiKey")
            override fun read(): SecureResult<ApiKey> = throw IllegalStateException(POISON)
            override fun delete(): SecureResult<Unit> = throw IllegalStateException(POISON)
        }
        val controller = GeminiKeySettingsController({ poisoned }, direct)
        val texts = mutableListOf<String>()
        controller.refresh(); texts += controller.state.toString()
        controller.save(TEST_API_KEY); texts += controller.state.toString()
        controller.delete(); texts += controller.state.toString()
        assertNoSecretText(texts)
        assertNoSecretText(listOf(StoreApiKeyProvider(poisoned).lookup().toString()))
    }

    @Test
    fun typedTextThatIsRejectedIsNeverKeptInTheState() {
        val store = SecureApiKeyStore(
            AesGcmCipher(SoftwareKeyProvider(), SecureApiKeyStore.ASSOCIATED_DATA),
            MemoryBlobStorage(),
        )
        val controller = GeminiKeySettingsController({ store }, direct)
        controller.refresh()
        controller.save("pasted wrong text with spaces $TEST_API_KEY")
        val text = controller.state.toString()
        assertFalse(text.contains(TEST_API_KEY))
        assertFalse(text.contains("pasted wrong text"))
    }

    // ================= buffers are cleared =================

    @Test
    fun theBytesHandedToTheCipherOnSaveAreClearedAfterwards() {
        val recording = RecordingCipher(newInnerCipher())
        val store = SecureApiKeyStore(recording, MemoryBlobStorage())
        assertTrue(store.save(TEST_API_KEY) is SecureResult.Success)
        val handed = recording.lastPlainIn!!
        assertEquals(TEST_API_KEY.length, handed.size)
        assertTrue("plain bytes were not cleared", handed.all { it == 0.toByte() })
    }

    @Test
    fun theBytesHandedToTheCipherAreClearedEvenWhenEncryptionFails() {
        val recording = RecordingCipher(newInnerCipher(), failEncrypt = true)
        val store = SecureApiKeyStore(recording, MemoryBlobStorage())
        assertTrue(store.save(TEST_API_KEY) is SecureResult.Failure)
        assertTrue(recording.lastPlainIn!!.all { it == 0.toByte() })
    }

    @Test
    fun theBytesHandedToTheCipherAreClearedEvenWhenTheWriteFails() {
        val storage = MemoryBlobStorage().also { it.failWrites = true }
        val recording = RecordingCipher(newInnerCipher())
        assertTrue(SecureApiKeyStore(recording, storage).save(TEST_API_KEY) is SecureResult.Failure)
        assertTrue(recording.lastPlainIn!!.all { it == 0.toByte() })
    }

    @Test
    fun theDecryptedBytesAreClearedAfterASuccessfulRead() {
        val recording = RecordingCipher(newInnerCipher())
        val store = SecureApiKeyStore(recording, MemoryBlobStorage())
        store.save(TEST_API_KEY)
        val result = store.read()
        assertEquals(TEST_API_KEY, (result as SecureResult.Success).value.reveal())
        assertTrue("decrypted bytes were not cleared", recording.lastPlainOut!!.all { it == 0.toByte() })
    }

    @Test
    fun theDecryptedBytesAreClearedWhenTheContentIsNotAValidKey() {
        val inner = newInnerCipher()
        val storage = MemoryBlobStorage()
        storage.blob = inner.encrypt("has spaces".toByteArray())
        val recording = RecordingCipher(inner)
        val result = SecureApiKeyStore(recording, storage).read()
        assertTrue(result is SecureResult.Failure)
        assertTrue(recording.lastPlainOut!!.all { it == 0.toByte() })
    }

    // ================= 4. what the code is allowed to do =================

    @Test
    fun noLoggingOrPrintingInTheKeyHandlingCode() {
        val forbidden = listOf(
            "android.util.Log", "Log.d(", "Log.e(", "Log.w(", "Log.i(", "Log.v(", "Log.wtf(",
            "println(", "print(", "printStackTrace", "System.out", "System.err",
            "Timber", "java.util.logging", "Logger.",
        )
        val problems = mutableListOf<String>()
        for (file in keyHandlingSources()) {
            val text = file.readText()
            for (token in forbidden) {
                if (text.contains(token)) problems += "${file.name} contains $token"
            }
        }
        assertTrue(problems.toString(), problems.isEmpty())
    }

    @Test
    fun theKeyHandlingSourcesWereFound() {
        // Guards the scan above: an empty list would make it pass for the wrong reason.
        val names = keyHandlingSources().map { it.name }.toSet()
        assertTrue(names.toString(), "SecureApiKeyStore.kt" in names)
        assertTrue(names.toString(), "AesGcmCipher.kt" in names)
        assertTrue(names.toString(), "GeminiKeySettingsController.kt" in names)
        assertTrue(names.toString(), "GeminiApiKeySection.kt" in names)
    }

    @Test
    fun theTypedKeyIsNeverPutIntoSavedInstanceState() {
        val section = File(mainSourceRoot(), "com/mnb/browser/ui/GeminiApiKeySection.kt").readText()
        // Comments may mention the name; a real use is followed by a call or a lambda.
        assertFalse(Regex("""rememberSaveable\s*[({]""").containsMatchIn(section))
        assertFalse(section.contains("SavedStateHandle"))
        assertFalse(section.contains("onSaveInstanceState"))
    }

    @Test
    fun theKeyFieldIsMaskedByDefaultAndUsesAPasswordKeyboard() {
        val section = File(mainSourceRoot(), "com/mnb/browser/ui/GeminiApiKeySection.kt").readText()
        assertTrue(section.contains("PasswordVisualTransformation()"))
        assertTrue(section.contains("KeyboardType.Password"))
        assertTrue(section.contains("var visible by remember { mutableStateOf(false) }"))
    }

    @Test
    fun theTypedKeyIsClearedWhenTheAppStops() {
        val section = File(mainSourceRoot(), "com/mnb/browser/ui/GeminiApiKeySection.kt").readText()
        val onStop = section.substringAfter("Lifecycle.Event.ON_STOP", missingDelimiterValue = "")
        assertTrue("ON_STOP handler not found", onStop.isNotEmpty())
        assertTrue(onStop.substringBefore("else ->").contains("input = \"\""))
    }

    @Test
    fun appBackupIsOffSoTheEncryptedFileIsNotCopiedToOtherDevices() {
        val manifest = File(mainSourceRoot().parentFile, "AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
    }

    @Test
    fun onlyTheSecureStoreAndTheGeminiProviderReadThePlainKey() {
        // Phase 06-B1: the direct Gemini provider is the one deliberate extra reader. It uses the
        // key only to build the request header. Add another file here only on purpose.
        val allowed = setOf(
            "com/mnb/browser/security/SecureApiKeyStore.kt",
            "com/mnb/browser/translation/gemini/DirectGeminiProvider.kt",
        )
        val root = mainSourceRoot()
        val readers = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains(".reveal()") }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .toSet()
        assertEquals(allowed, readers)
    }

    @Test
    fun noKeyTextFieldExistsInTheSettingsStateOrResults() {
        val types = listOf(
            com.mnb.browser.settings.GeminiKeySettingsState::class.java,
            SecureResult.Failure::class.java,
            ApiKeyLookup.Unavailable::class.java,
        )
        for (type in types) {
            for (field in type.declaredFields) {
                assertFalse(
                    "${type.simpleName}.${field.name} is a ${field.type.simpleName}",
                    field.type == ApiKey::class.java || field.name.contains("key", ignoreCase = true) &&
                        field.type == String::class.java,
                )
            }
        }
    }

    // ================= helpers =================

    private fun newInnerCipher(): AeadCipher = AesGcmCipher(SoftwareKeyProvider(), SecureApiKeyStore.ASSOCIATED_DATA)

    private fun longKey(): String = (0 until ApiKey.MAX_LENGTH).map { (0x21 + (it * 7) % 94).toChar() }.joinToString("")

    private fun assertNoSecretText(texts: List<String>) {
        for (text in texts) {
            assertFalse(text, text.contains(TEST_API_KEY))
            assertFalse(text, text.contains(POISON))
            assertFalse(text, text.contains("secret"))
        }
    }

    /** Runs every operation on [store] and gathers the printed form of everything that comes back. */
    private fun allTextsFor(store: SecureApiKeyStore): List<String> {
        val texts = mutableListOf<String>()
        texts += store.save(TEST_API_KEY).toString()
        texts += store.update(TEST_API_KEY).toString()
        texts += store.read().toString()
        texts += store.delete().toString()
        texts += StoreApiKeyProvider(store).lookup().toString()
        val controller = GeminiKeySettingsController({ store }, direct)
        controller.refresh(); texts += controller.state.toString()
        controller.save(TEST_API_KEY); texts += controller.state.toString()
        controller.delete(); texts += controller.state.toString()
        return texts
    }

    /** Saves, updates, reads and deletes through the real classes, including many failure paths. */
    private fun exerciseEverything() {
        val provider = SoftwareKeyProvider()
        val storage = MemoryBlobStorage()
        val store = SecureApiKeyStore(AesGcmCipher(provider, SecureApiKeyStore.ASSOCIATED_DATA), storage)
        val controller = GeminiKeySettingsController({ store }, direct)
        val lookup = StoreApiKeyProvider(store)

        controller.refresh()
        controller.save(TEST_API_KEY)
        controller.save(OTHER_API_KEY)
        controller.save("bad $TEST_API_KEY") // rejected: contains a space
        lookup.lookup()

        storage.failWrites = true
        controller.save(TEST_API_KEY)
        storage.failWrites = false

        val blob = storage.blob!!
        blob[blob.size - 1] = (blob[blob.size - 1].toInt() xor 1).toByte()
        controller.refresh()
        lookup.lookup()

        provider.secretKey = null
        controller.refresh()
        store.read()
        controller.save(TEST_API_KEY)

        storage.failClear = true
        controller.delete()
        storage.failClear = false
        controller.delete()

        // Components that throw with the key inside their message.
        allTextsFor(SecureApiKeyStore(ExplodingCipher(), MemoryBlobStorage()))
        allTextsFor(SecureApiKeyStore(AesGcmCipher(PoisonedKeyProvider(), SecureApiKeyStore.ASSOCIATED_DATA), MemoryBlobStorage()))
        allTextsFor(SecureApiKeyStore(AesGcmCipher(SoftwareKeyProvider(), SecureApiKeyStore.ASSOCIATED_DATA), PoisonedStorage()))
    }

    /** Collects everything written to stdout, stderr and java.util.logging while [block] runs. */
    private fun captureOutput(block: () -> Unit): String {
        val buffer = ByteArrayOutputStream()
        val stream = PrintStream(buffer, true, "UTF-8")
        val logText = StringBuilder()
        val handler = object : Handler() {
            override fun publish(record: LogRecord) {
                logText.append(record.level).append(' ').append(record.message).append(' ').append(record.thrown).append('\n')
            }

            override fun flush() {}
            override fun close() {}
        }
        val rootLogger = Logger.getLogger("")
        val oldOut = System.out
        val oldErr = System.err
        System.setOut(stream)
        System.setErr(stream)
        rootLogger.addHandler(handler)
        try {
            block()
        } finally {
            System.setOut(oldOut)
            System.setErr(oldErr)
            rootLogger.removeHandler(handler)
            stream.flush()
        }
        return buffer.toString("UTF-8") + logText.toString()
    }

    private fun mainSourceRoot(): File {
        val candidates = listOf("src/main/java", "app/src/main/java").map { File(it) }
        return candidates.firstOrNull { it.isDirectory }
            ?: error("main sources not found from ${File(".").absoluteFile.normalize()}")
    }

    /** Every file that handles the key: the security package, the settings controller and the key section. */
    private fun keyHandlingSources(): List<File> {
        val root = File(mainSourceRoot(), "com/mnb/browser")
        val folders = listOf("security", "settings").map { File(root, it) }.filter { it.isDirectory }
        val files = folders.flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
        return files + File(root, "ui/GeminiApiKeySection.kt")
    }

    private class RecordingCipher(
        private val inner: AeadCipher,
        private val failEncrypt: Boolean = false,
    ) : AeadCipher {
        var lastPlainIn: ByteArray? = null
        var lastPlainOut: ByteArray? = null

        override fun encrypt(plain: ByteArray): ByteArray {
            lastPlainIn = plain
            if (failEncrypt) throw IllegalStateException("encrypt failed")
            return inner.encrypt(plain)
        }

        override fun decrypt(envelope: ByteArray): ByteArray = inner.decrypt(envelope).also { lastPlainOut = it }

        override fun deleteKey() {
            inner.deleteKey()
        }
    }

    private class PoisonedKeyProvider : KeyProvider {
        override fun getKey(): SecretKey? = throw IllegalStateException("$POISON $TEST_API_KEY")
        override fun getOrCreateKey(): SecretKey = throw IllegalStateException("$POISON $TEST_API_KEY")
        override fun deleteKey() {
            throw IllegalStateException("$POISON $TEST_API_KEY")
        }
    }

    private class PoisonedStorage : EncryptedBlobStorage {
        override fun exists(): Boolean = throw IOException("$POISON $TEST_API_KEY")
        override fun read(): ByteArray? = throw IOException("$POISON $TEST_API_KEY")
        override fun write(blob: ByteArray): Boolean = throw IOException("$POISON $TEST_API_KEY")
        override fun clear(): Boolean = throw IOException("$POISON $TEST_API_KEY")
    }

    private companion object {
        const val OTHER_API_KEY = "AIzaSyOTHER-fake_key_0000000000"
        const val POISON = "secret-error-text"
    }
}
