package com.mnb.browser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mnb.browser.extraction.ExtractionController
import com.mnb.browser.extraction.ExtractionState
import com.mnb.browser.extraction.ExtractionStateManager
import com.mnb.browser.navigation.NavigationController
import com.mnb.browser.state.BrowserError
import com.mnb.browser.state.BrowserStateManager
import com.mnb.browser.web.WebViewController

@Composable
fun BrowserScreen(
stateManager: BrowserStateManager,
webController: WebViewController,
navigation: NavigationController,
extraction: ExtractionController,
extractionState: ExtractionStateManager,
) {
val state = stateManager.state
val extractionUi = extractionState.state
val focus = LocalFocusManager.current
var input by rememberSaveable { mutableStateOf("") }
LaunchedEffect(state.currentUrl) { input = state.currentUrl }

BackHandler(enabled = state.canGoBack) { navigation.back() }
// Registered after the one above, so it wins while the preview is open.
BackHandler(enabled = extractionUi !is ExtractionState.Idle) { extraction.close() }

fun go() {
    focus.clearFocus()
    navigation.submit(input)
}

Scaffold(
    topBar = {
        Surface(tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = state.title.ifBlank { "MNB" },
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        placeholder = { Text("Search or enter address") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { go() }),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { go() }) {
                        Icon(Icons.Default.Search, contentDescription = "Go")
                    }
                }
                if (state.isLoading) {
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Box(Modifier.size(width = 0.dp, height = 4.dp))
                }
            }
        }
    },
    bottomBar = {
        NavigationBar {
            NavigationBarItem(
                selected = false, enabled = state.canGoBack, onClick = { navigation.back() },
                icon = { Icon(Icons.Default.ArrowBack, null) }, label = { Text("Back") },
            )
            NavigationBarItem(
                selected = false, enabled = state.canGoForward, onClick = { navigation.forward() },
                icon = { Icon(Icons.Default.ArrowForward, null) }, label = { Text("Forward") },
            )
            NavigationBarItem(
                selected = false, onClick = { navigation.home() },
                icon = { Icon(Icons.Default.Home, null) }, label = { Text("Home") },
            )
            NavigationBarItem(
                selected = false, onClick = { navigation.refresh() },
                icon = { Icon(Icons.Default.Refresh, null) }, label = { Text("Refresh") },
            )
            NavigationBarItem(
                selected = false,
                enabled = state.currentUrl.isNotBlank() && !state.isLoading && state.error == null,
                onClick = { extraction.extract() },
                icon = { Icon(Icons.Default.List, null) }, label = { Text("Extract") },
            )
        }
    },
) { padding ->
    Box(Modifier.padding(padding).fillMaxSize()) {
        AndroidView(
            factory = { ctx -> webController.createWebView(ctx) },
            modifier = Modifier.fillMaxSize(),
        )
        state.error?.let { error ->
            ErrorOverlay(error, onRetry = { navigation.refresh() }, onDismiss = { navigation.dismissError() })
        }
        if (extractionUi !is ExtractionState.Idle) {
            ExtractionPreviewScreen(
                state = extractionUi,
                onClose = { extraction.close() },
                onRetry = { extraction.extract() },
            )
        }
    }
}

}

@Composable
private fun ErrorOverlay(error: BrowserError, onRetry: () -> Unit, onDismiss: () -> Unit) {
Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
Column(
Modifier.fillMaxSize().padding(32.dp),
horizontalAlignment = Alignment.CenterHorizontally,
verticalArrangement = Arrangement.Center,
) {
Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
Text(error.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
Text(error.message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 12.dp))
Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
OutlinedButton(onClick = onDismiss) { Text("Dismiss") }
if (error.failingUrl != null) Button(onClick = onRetry) { Text("Retry") }
}
}
}
}
