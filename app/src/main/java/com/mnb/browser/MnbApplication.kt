package com.mnb.browser

import android.app.Application
import com.mnb.browser.security.SecureApiKeyStore
import com.mnb.browser.security.StoreApiKeyProvider
import com.mnb.browser.translation.background.TranslationWorkEnvironment
import com.mnb.browser.translation.gemini.GeminiTranslationWiring

/**
 * Phase 06-B4: process-wide wiring for background translation.
 *
 * WorkManager can start the app's process only to run a translation worker, for example after the
 * system killed the app or when a retry is due. No Activity exists then, so nothing set up in
 * MainActivity.onCreate would be in place and the worker would find only the mock default, which it
 * refuses. This class runs at every process start, before any worker, and installs the direct
 * Gemini provider (BYOK) as the worker's provider.
 *
 * [onCreate] does no Keystore, file or network work: it only stores lambdas. The store, the
 * wiring and the provider are all created lazily, on the first thread that needs them (the worker
 * thread or the settings thread), never here on the main thread.
 *
 * The key is never kept in this class. The worker's provider reads it through the read-only
 * StoreApiKeyProvider, only at the moment a request is sent.
 */
class MnbApplication : Application() {

    /**
     * The one secure key store of the process, shared by the settings screen, the reader's provider
     * and the background worker (the store serialises its operations per instance). Created on
     * first use; its first use is always off the main thread.
     */
    val geminiKeyStore: SecureApiKeyStore by lazy { SecureApiKeyStore.create(this) }

    /**
     * The provider the background worker uses. "Is a key saved?" is answered from the secure
     * storage itself (a cheap existence check) because the settings screen's in-memory status does
     * not exist in a process that has no Activity. It is only asked on the worker's own thread.
     */
    private val workerTranslation: GeminiTranslationWiring by lazy {
        GeminiTranslationWiring(
            keys = StoreApiKeyProvider(geminiKeyStore),
            keyIsSaved = { geminiKeyStore.hasKey() },
        )
    }

    override fun onCreate() {
        super.onCreate()
        installWorkEnvironment()
    }

    /** Points the worker at the Gemini provider. Safe to call again; tests call it after a reset. */
    internal fun installWorkEnvironment() {
        TranslationWorkEnvironment.providerFactory = { workerTranslation.provider }
        TranslationWorkEnvironment.readerProviderIdSource = { workerTranslation.readerProviderId() }
    }
}
