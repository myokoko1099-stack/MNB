package com.mnb.browser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import com.mnb.browser.extraction.ExtractionController
import com.mnb.browser.extraction.ExtractionStateManager
import com.mnb.browser.navigation.NavigationController
import com.mnb.browser.reader.ReaderPositionRepository
import com.mnb.browser.reader.ReaderSettingsRepository
import com.mnb.browser.reader.ReaderStateManager
import com.mnb.browser.reader.SharedPreferencesKeyValueStore
import com.mnb.browser.state.BrowserStateManager
import com.mnb.browser.ui.BrowserScreen
import com.mnb.browser.web.FileWebSessionStore
import com.mnb.browser.web.WebViewController
import java.io.File

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
    private val readerState = ReaderStateManager()
    // Lazy: applicationContext is only available after the Activity is attached.
    private val readerPositions by lazy {
        ReaderPositionRepository(SharedPreferencesKeyValueStore(applicationContext))
    }
    private val readerSettings by lazy {
        ReaderSettingsRepository(SharedPreferencesKeyValueStore(applicationContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                BrowserScreen(
                    stateManager, webController, navigation, extraction, extractionState,
                    readerState, readerPositions, readerSettings,
                )
            }
        }
    }

    override fun onResume() { super.onResume(); webController.onResume() }
    override fun onPause() { webController.onPause(); super.onPause() }
    override fun onDestroy() { webController.destroy(); super.onDestroy() }
}
