package com.mnb.browser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import com.mnb.browser.navigation.NavigationController
import com.mnb.browser.state.BrowserStateManager
import com.mnb.browser.ui.BrowserScreen
import com.mnb.browser.web.WebViewController

class MainActivity : ComponentActivity() {
private val stateManager = BrowserStateManager()
private val webController = WebViewController(stateManager)
private val navigation = NavigationController(webController, stateManager)

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
        MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
            BrowserScreen(stateManager, webController, navigation)
        }
    }
}

override fun onResume() { super.onResume(); webController.onResume() }
override fun onPause() { webController.onPause(); super.onPause() }
override fun onDestroy() { webController.destroy(); super.onDestroy() }

}
