package com.mnb.browser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.work.WorkManager
import com.mnb.browser.extraction.ExtractionController
import com.mnb.browser.extraction.ExtractionStateManager
import com.mnb.browser.navigation.NavigationController
import com.mnb.browser.reader.ReaderPositionRepository
import com.mnb.browser.reader.ReaderSettingsRepository
import com.mnb.browser.reader.ReaderStateManager
import com.mnb.browser.reader.translation.BackgroundTranslationWiring
import com.mnb.browser.reader.translation.BoundChapterIdentityResolver
import com.mnb.browser.reader.translation.InstantTranslationWiring
import com.mnb.browser.reader.translation.LocalReaderTranslationActions
import com.mnb.browser.reader.translation.NovelBindingStore
import com.mnb.browser.reader.translation.ReaderBackgroundTranslationCoordinator
import com.mnb.browser.reader.translation.ReaderInstantTranslationController
import com.mnb.browser.reader.translation.ReaderTranslationStateHolder
import com.mnb.browser.reader.translation.UnavailableReaderTranslationGateway
import com.mnb.browser.security.ApiKeyProvider
import com.mnb.browser.security.StoreApiKeyProvider
import com.mnb.browser.settings.GeminiKeySettingsController
import com.mnb.browser.settings.GeminiKeyStatus
import com.mnb.browser.reader.SharedPreferencesKeyValueStore
import com.mnb.browser.state.BrowserStateManager
import com.mnb.browser.translation.TranslationProvider
import com.mnb.browser.translation.background.TranslationWorkEnvironment
import com.mnb.browser.translation.background.WorkManagerTranslationScheduler
import com.mnb.browser.translation.gemini.GeminiTranslationWiring
import com.mnb.browser.translation.db.MnbDatabase
import com.mnb.browser.ui.BrowserScreen
import com.mnb.browser.ui.LocalGeminiKeySettings
import com.mnb.browser.web.FileWebSessionStore
import com.mnb.browser.web.WebViewController
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val stateManager = BrowserStateManager()
    // filesDir is resolved lazily, once the Activity is attached.
    private val webController = WebViewController(
        stateManager,
        FileWebSessionStore { File(filesDir, "web_session.bin") },
    )
    private val navigation = NavigationController(webController, stateManager)
    private val extractionState = ExtractionStateManager()
    private val extraction = ExtractionController(webController, extractionState)

    // Lazy: applicationContext is only available after the Activity is attached.
    private val novelBindings by lazy {
        NovelBindingStore(SharedPreferencesKeyValueStore(applicationContext))
    }
    // Phase 04-I10-C1: chapters are identified from the novel URL binding plus the chapter title.
    // Phase 04-I10-C2: every opened chapter is also reported to the background translation coordinator.
    private val translationHolder: ReaderTranslationStateHolder by lazy {
        ReaderTranslationStateHolder(
            BoundChapterIdentityResolver(novelBindings),
            chapterListener = { identity, content ->
                backgroundCoordinator.onChapterOpened(identity, content.url, content.mainText)
            },
        )
    }
    private val readerState by lazy { ReaderStateManager(translationHolder) }
    private val readerPositions by lazy {
        ReaderPositionRepository(SharedPreferencesKeyValueStore(applicationContext))
    }
    private val readerSettings by lazy {
        ReaderSettingsRepository(SharedPreferencesKeyValueStore(applicationContext))
    }

    // Phase 06-B2: the real provider is the direct Gemini provider with the user's own key (BYOK).
    // MockTranslationProvider must NOT be returned here. The source only answers "is a key saved?"
    // from the in-memory settings status (no Keystore or file work: it may run on the main thread).
    // Without a saved key it returns null, so every chapter keeps showing Unavailable(NOT_CONFIGURED),
    // starts no background translation and opens neither the database nor WorkManager. The key is read
    // later, on the translation thread, by the provider itself. The background worker gets the same
    // provider id from MnbApplication (Phase 06-B4), which installs it at process start, so the worker
    // also works when Android starts the process only to run it (no Activity, no onCreate here).
    private val translationProviderSource: () -> TranslationProvider? = { geminiTranslation.providerOrNull() }

    // Instant translation runs on its own single background thread, never on the main thread.
    private val translationExecutorLazy: Lazy<ExecutorService> = lazy {
        Executors.newSingleThreadExecutor { r -> Thread(r, "mnb-instant-translation").apply { isDaemon = true } }
    }
    private val translationWiring by lazy { InstantTranslationWiring { MnbDatabase.getInstance(applicationContext) } }
    private val translationController by lazy {
        ReaderInstantTranslationController(
            holder = translationHolder,
            bindings = novelBindings,
            gateway = UnavailableReaderTranslationGateway,
            providerSource = translationProviderSource,
            backendSource = { provider -> translationWiring.backendFor(provider) },
            executor = translationExecutorLazy.value,
        )
    }

    // Phase 04-I10-C2: background translation follows reader navigation on its own single thread
    // (database and queue work never run on the main thread).
    private val backgroundExecutorLazy: Lazy<ExecutorService> = lazy {
        Executors.newSingleThreadExecutor { r -> Thread(r, "mnb-background-translation").apply { isDaemon = true } }
    }
    private val backgroundWiring by lazy {
        BackgroundTranslationWiring(
            database = { MnbDatabase.getInstance(applicationContext) },
            scheduler = { WorkManagerTranslationScheduler(WorkManager.getInstance(applicationContext)) },
        )
    }
    private val backgroundCoordinator: ReaderBackgroundTranslationCoordinator by lazy {
        ReaderBackgroundTranslationCoordinator(
            currentChapterId = { translationHolder.current?.identity?.chapterId },
            bindingFor = { url -> novelBindings.find(url) },
            providerSource = translationProviderSource,
            workerProviderSource = { TranslationWorkEnvironment.providerFactory() },
            portsSource = { provider -> backgroundWiring.portsFor(provider) },
            executor = backgroundExecutorLazy.value,
        )
    }

    // Phase 06-A2: Gemini API key settings. Keystore and file work of the secure store runs only on
    // this single background thread or on a translation thread, never on the main thread.
    private val geminiKeyExecutorLazy: Lazy<ExecutorService> = lazy {
        Executors.newSingleThreadExecutor { r -> Thread(r, "mnb-gemini-key-settings").apply { isDaemon = true } }
    }

    // One store instance for the settings screen, the translation provider and the background worker:
    // the store serialises its operations per instance. It lives in MnbApplication (Phase 06-B4) and is
    // created on first use, which is always off the main thread.
    private val geminiKeyStore by lazy { (application as MnbApplication).geminiKeyStore }
    private val geminiKeySettings by lazy {
        GeminiKeySettingsController(
            storeFactory = { geminiKeyStore },
            executor = geminiKeyExecutorLazy.value,
        )
    }

    // Phase 06-B2: Gemini as the translation provider. It reads the key only through the Phase 06-A
    // secure storage (read-only access), and only when it translates.
    private val geminiTranslation by lazy {
        GeminiTranslationWiring(
            // The store is only touched inside lookup(), i.e. on the translation thread, never here.
            keys = ApiKeyProvider { StoreApiKeyProvider(geminiKeyStore).lookup() },
            keyIsSaved = { geminiKeySettings.state.status == GeminiKeyStatus.SAVED },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Learn on a background thread whether a key is already saved, so a saved key enables translation.
        geminiKeySettings.refresh()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                CompositionLocalProvider(
                    LocalReaderTranslationActions provides translationController,
                    LocalGeminiKeySettings provides geminiKeySettings,
                ) {
                    BrowserScreen(
                        stateManager, webController, navigation, extraction, extractionState,
                        readerState, readerPositions, readerSettings,
                    )
                }
            }
        }
    }

    override fun onResume() { super.onResume(); webController.onResume() }
    override fun onPause() { webController.onPause(); super.onPause() }
    override fun onDestroy() {
        webController.destroy()
        if (translationExecutorLazy.isInitialized()) translationExecutorLazy.value.shutdownNow()
        // WorkManager keeps its own work running; only this thread (the navigation sync) stops.
        if (backgroundExecutorLazy.isInitialized()) backgroundExecutorLazy.value.shutdownNow()
        if (geminiKeyExecutorLazy.isInitialized()) geminiKeyExecutorLazy.value.shutdownNow()
        super.onDestroy()
    }
}
