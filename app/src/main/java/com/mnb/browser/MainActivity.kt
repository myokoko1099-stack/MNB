package com.mnb.browser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import com.mnb.browser.extraction.ExtractionController
import com.mnb.browser.extraction.ExtractionStateManager
import com.mnb.browser.navigation.NavigationController
import com.mnb.browser.reader.ReaderPositionRepository
import com.mnb.browser.reader.ReaderSettingsRepository
import com.mnb.browser.reader.ReaderStateManager
import com.mnb.browser.reader.translation.BoundChapterIdentityResolver
import com.mnb.browser.reader.translation.InstantTranslationWiring
import com.mnb.browser.reader.translation.LocalReaderTranslationActions
import com.mnb.browser.reader.translation.NovelBindingStore
import com.mnb.browser.reader.translation.ReaderInstantTranslationController
import com.mnb.browser.reader.translation.ReaderTranslationStateHolder
import com.mnb.browser.reader.translation.UnavailableReaderTranslationGateway
import com.mnb.browser.reader.SharedPreferencesKeyValueStore
import com.mnb.browser.state.BrowserStateManager
import com.mnb.browser.translation.db.MnbDatabase
import com.mnb.browser.ui.BrowserScreen
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
    private val translationHolder by lazy {
        ReaderTranslationStateHolder(BoundChapterIdentityResolver(novelBindings))
    }
    private val readerState by lazy { ReaderStateManager(translationHolder) }
    private val readerPositions by lazy {
        ReaderPositionRepository(SharedPreferencesKeyValueStore(applicationContext))
    }
    private val readerSettings by lazy {
        ReaderSettingsRepository(SharedPreferencesKeyValueStore(applicationContext))
    }

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
            // No real translation provider exists yet. MockTranslationProvider must NOT be returned
            // here: a null provider keeps every chapter at Unavailable(NOT_CONFIGURED) and the
            // database is not opened. Supply a real, non-mock provider when one is available.
            providerSource = { null },
            backendSource = { provider -> translationWiring.backendFor(provider) },
            executor = translationExecutorLazy.value,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                CompositionLocalProvider(LocalReaderTranslationActions provides translationController) {
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
        super.onDestroy()
    }
}
