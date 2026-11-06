package com.mnb.browser

import android.content.Context
import com.mnb.browser.security.SharedPreferencesEncryptedBlobStorage
import com.mnb.browser.translation.background.TranslationWorkEnvironment
import com.mnb.browser.translation.background.WorkerRefusal
import com.mnb.browser.translation.gemini.DirectGeminiProvider
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Phase 06-B4: the background worker must get the Gemini provider when Android starts the process
 * only to run it, that is, with no Activity. Under Robolectric the Application declared in the
 * manifest is created for every test and runs onCreate, exactly as a fresh process does; nothing
 * here touches MainActivity. No network, no Keystore: the storage check only looks at whether the
 * secure storage file holds an entry, and the entry used here is a fake placeholder, not a key.
 *
 * This proves the wiring on the JVM. It cannot prove that WorkManager really restarts a killed
 * process; that needs a device (see RELEASE-CHECKLIST.md).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MnbApplicationTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @After fun resetEnvironment() {
        TranslationWorkEnvironment.reset()
        prefs().edit().clear().commit()
    }

    private fun prefs() = app.getSharedPreferences(
        SharedPreferencesEncryptedBlobStorage.DEFAULT_PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    private fun saveAPlaceholderEntry() {
        // hasKey() only checks that an entry exists, so no Keystore is involved. This is not a key.
        prefs().edit().putString(SharedPreferencesEncryptedBlobStorage.DEFAULT_ENTRY_KEY, "AAAA").commit()
    }

    // ---- the Application is really used -------------------------------------------------------

    @Test fun theManifestApplicationIsMnbApplication() {
        assertTrue(app.javaClass.name, app is MnbApplication)
    }

    @Test fun theManifestDeclaresMnbApplication() {
        val manifest = File(appDir(), "src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:name=\".MnbApplication\""))
    }

    // ---- a process with no Activity ------------------------------------------------------------

    @Test fun afterProcessStartTheWorkerProviderIsGeminiAndNotTheMock() {
        val provider = TranslationWorkEnvironment.providerFactory()
        assertFalse(provider.isMock)
        assertEquals(DirectGeminiProvider.ID, provider.id)
    }

    @Test fun theWorkerProviderIsTheSameInstanceOnEveryCall() {
        assertTrue(TranslationWorkEnvironment.providerFactory() === TranslationWorkEnvironment.providerFactory())
    }

    @Test fun withoutASavedKeyAFreshProcessWorkerIsRefusedAsNoProvider() {
        assertEquals(WorkerRefusal.NO_PROVIDER, TranslationWorkEnvironment.checkProvider("en"))
    }

    @Test fun withASavedKeyAFreshProcessWorkerMayTranslate() {
        saveAPlaceholderEntry()
        assertNull(TranslationWorkEnvironment.checkProvider("en"))
    }

    @Test fun theWorkerReaderIdIsTheGeminiIdOnlyWhileAKeyIsSaved() {
        assertNull(TranslationWorkEnvironment.readerProviderIdSource())
        saveAPlaceholderEntry()
        assertEquals(DirectGeminiProvider.ID, TranslationWorkEnvironment.readerProviderIdSource())
    }

    @Test fun anUnsupportedLanguageIsStillRefusedWithAKeySaved() {
        saveAPlaceholderEntry()
        assertEquals(WorkerRefusal.UNSUPPORTED_LANGUAGE, TranslationWorkEnvironment.checkProvider("fr"))
    }

    // ---- not initialised yet / initialised twice -----------------------------------------------

    @Test fun beforeTheInstallTheMockDefaultIsNeverUsedForRealWork() {
        TranslationWorkEnvironment.reset() // what a worker would see if nothing had been installed
        saveAPlaceholderEntry()
        val refusal = TranslationWorkEnvironment.checkProvider("en")
        assertNotNull(refusal)
        assertTrue(refusal.toString(), refusal == WorkerRefusal.NO_PROVIDER || refusal == WorkerRefusal.MOCK_PROVIDER)
    }

    @Test fun installingAgainAfterAResetRestoresTheGeminiProvider() {
        TranslationWorkEnvironment.reset()
        (app as MnbApplication).installWorkEnvironment()
        saveAPlaceholderEntry()
        assertNull(TranslationWorkEnvironment.checkProvider("en"))
        assertEquals(DirectGeminiProvider.ID, TranslationWorkEnvironment.providerFactory().id)
    }

    @Test fun installingTwiceChangesNothing() {
        (app as MnbApplication).installWorkEnvironment()
        (app as MnbApplication).installWorkEnvironment()
        saveAPlaceholderEntry()
        assertNull(TranslationWorkEnvironment.checkProvider("en"))
    }

    // ---- start-up is cheap, and the key store is shared ----------------------------------------

    @Test fun startingTheProcessWritesNothingToTheSecureStorage() {
        assertFalse(prefs().contains(SharedPreferencesEncryptedBlobStorage.DEFAULT_ENTRY_KEY))
    }

    @Test fun theKeyStoreIsOneInstancePerProcess() {
        val application = app as MnbApplication
        assertTrue(application.geminiKeyStore === application.geminiKeyStore)
    }

    // ---- source guards -------------------------------------------------------------------------

    @Test fun mainActivityNoLongerSetsTheWorkEnvironment() {
        val text = mainSource("MainActivity.kt")
        assertFalse(text.contains("providerFactory ="))
        assertFalse(text.contains("readerProviderIdSource ="))
    }

    @Test fun mainActivityUsesTheApplicationsKeyStore() {
        val text = mainSource("MainActivity.kt")
        assertTrue(text.contains("(application as MnbApplication).geminiKeyStore"))
        assertFalse(text.contains("SecureApiKeyStore.create("))
    }

    @Test fun onCreateOnlyInstallsTheEnvironment() {
        val text = mainSource("MnbApplication.kt")
        val onCreate = text.substringAfter("override fun onCreate()").substringBefore("/** Points the worker")
        assertTrue(onCreate.contains("installWorkEnvironment()"))
        for (forbidden in listOf(".read()", ".save(", ".delete(", ".hasKey(", "Thread", "Executor", "lookup()")) {
            assertFalse(forbidden, onCreate.contains(forbidden))
        }
    }

    @Test fun theApplicationNeverLogsOrReadsThePlainKey() {
        val text = mainSource("MnbApplication.kt")
        for (forbidden in listOf(
            "android.util.Log", "Log.d(", "Log.e(", "Log.w(", "Log.i(", "Log.v(", "println(", "print(",
            "printStackTrace", "Timber", ".reveal()", "AIza",
        )) {
            assertFalse(forbidden, text.contains(forbidden))
        }
    }

    // ---- helpers -------------------------------------------------------------------------------

    private fun appDir(): File {
        val here = File(".").absoluteFile.normalize()
        return when {
            File(here, "src/main/AndroidManifest.xml").isFile -> here
            File(here, "app/src/main/AndroidManifest.xml").isFile -> File(here, "app")
            else -> error("app module not found from $here")
        }
    }

    private fun mainSource(name: String): String =
        File(appDir(), "src/main/java/com/mnb/browser/$name").readText()
}
