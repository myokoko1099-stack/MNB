package com.mnb.browser

import android.content.ComponentCallbacks2
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.work.WorkManager
import com.mnb.browser.extraction.ExtractionController
import com.mnb.browser.extraction.ExtractionStateManager
import com.mnb.browser.library.BrowserLibrary
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
import com.mnb.browser.tabs.LazyTabPersistence
import com.mnb.browser.tabs.RoomTabPersistence
import com.mnb.browser.tabs.TabManager
import com.mnb.browser.tabs.TabSessionFiles
import com.mnb.browser.translation.TranslationProvider
import com.mnb.browser.translation.background.TranslationWorkEnvironment
import com.mnb.browser.translation.background.WorkManagerTranslationScheduler
import com.mnb.browser.translation.gemini.GeminiTranslationWiring
import com.mnb.browser.translation.db.BrowserDataRepository
import com.mnb.browser.translation.db.MnbDatabase
import com.mnb.browser.ui.BrowserScreen
import com.mnb.browser.ui.LocalGeminiKeySettings
import com.mnb.browser.ui.premium.MnbTheme
import com.mnb.browser.ui.premium.UiPreferences
import com.mnb.browser.web.WebViewController
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val stateManager = BrowserStateManager()

    // Phase 2B: one background thread for everything about tabs that touches disk: the tab list in
    // Room, one WebView session file per tab, and removing session files of closed tabs. Nothing
    // here runs on the main thread. Created on first use.
    private val tabIoExecutorLazy: Lazy<ExecutorService> = lazy {
        Executors.newSingleThreadExecutor { r -> Thread(r, "mnb-tabs").apply { isDaemon = true } }
    }
    private val tabIo = Executor { task -> tabIoExecutorLazy.value.execute(task) }
    // filesDir is resolved lazily, once the Activity is attached.
    private val tabFiles = TabSessionFiles { File(filesDir, "tab_sessions") }
    private val webController = WebViewController(
        stateManager,
        sessionStoreFactory = { tabId -> tabFiles.storeFor(tabId) },
        ioExecutor = tabIo,
    )
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    // Phase 2B: the tab list is stored in the Phase 2A browser_tabs table (database version 4).
    private val tabManager = TabManager(
        persistence = LazyTabPersistence {
            RoomTabPersistence(BrowserDataRepository(MnbDatabase.getInstance(applicationContext)))
        },
        ioExecutor = tabIo,
        mainPoster = { task -> mainHandler.post(task) },
    )
    private val navigation = NavigationController(webController, stateManager)

    // Phase 2E: bookmarks and history. The blocking Room calls of BrowserDataRepository all run on
    // this one background thread, one after another; results come back on the main thread.
    private val libraryIoExecutorLazy: Lazy<ExecutorService> = lazy {
        Executors.newSingleThreadExecutor { r -> Thread(r, "mnb-library").apply { isDaemon = true } }
    }
    private val library = BrowserLibrary(
        repositoryFactory = { BrowserDataRepository(MnbDatabase.getInstance(applicationContext)) },
        ioExecutor = Executor { task -> libraryIoExecutorLazy.value.execute(task) },
        mainPoster = { task -> mainHandler.post(task) },
    )
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
        // Phase 2B: page changes of any tab update that tab's stored address and title; a closed tab
        // loses its WebView and session file; the stored tabs are loaded on the background thread.
        webController.pageListener = { tabId, url, title -> tabManager.updatePage(tabId, url, title) }
        // Phase 2E: a finished page load (without an error) is a history visit.
        webController.visitListener = { tabId, url, title -> library.recordVisit(tabId, url, title) }
        tabManager.onTabClosed = { tabId ->
            webController.closeTab(tabId)
            library.forgetTab(tabId)
        }
        tabManager.onRestored = { openIds -> tabIo.execute { tabFiles.prune(openIds) } }
        tabManager.restore()
        setContent {
            val uiPreferences = remember { UiPreferences(applicationContext) }
            MnbTheme(uiPreferences.themeMode) {
                CompositionLocalProvider(
                    LocalReaderTranslationActions provides translationController,
                    LocalGeminiKeySettings provides geminiKeySettings,
                ) {
                    BrowserScreen(
                        stateManager, webController, navigation, extraction, extractionState,
                        readerState, readerPositions, readerSettings, uiPreferences, tabManager,
                        library,
                    )
                }
            }
        }
    }

    /**
     * Phase 2F: when the running app is short of memory, background tabs give up their WebView. They
     * are rebuilt from their saved state when shown again. Other levels (UI hidden, background) are
     * ignored on purpose: leaving the app must not discard the user's tabs.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW && level < ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            webController.trimBackgroundViews()
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
        // Let the queued tab and session writes finish; no new ones are accepted.
        if (tabIoExecutorLazy.isInitialized()) tabIoExecutorLazy.value.shutdown()
        // Same for queued bookmark and history writes.
        if (libraryIoExecutorLazy.isInitialized()) libraryIoExecutorLazy.value.shutdown()
        super.onDestroy()
    }
}
