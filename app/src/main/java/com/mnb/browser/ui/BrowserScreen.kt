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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mnb.browser.extraction.ExtractionController
import com.mnb.browser.extraction.ExtractionState
import com.mnb.browser.extraction.ExtractionStateManager
import com.mnb.browser.navigation.NavigationController
import com.mnb.browser.navigation.UrlPolicy
import com.mnb.browser.reader.ReaderPositionRepository
import com.mnb.browser.reader.ReaderSettingsRepository
import com.mnb.browser.reader.ReaderStateManager
import com.mnb.browser.state.BrowserError
import com.mnb.browser.state.BrowserStateManager
import com.mnb.browser.web.WebViewController
import com.mnb.browser.ui.premium.AddressBarOverlay
import com.mnb.browser.ui.premium.BottomBar
import com.mnb.browser.ui.premium.NewTabPage
import com.mnb.browser.ui.premium.PremiumSettings
import com.mnb.browser.ui.premium.UiPreferences

@Composable
fun BrowserScreen(
    stateManager: BrowserStateManager,
    webController: WebViewController,
    navigation: NavigationController,
    extraction: ExtractionController,
    extractionState: ExtractionStateManager,
    readerState: ReaderStateManager,
    readerPositions: ReaderPositionRepository,
    readerSettings: ReaderSettingsRepository,
    uiPreferences: UiPreferences,
) {
    val state = stateManager.state
    val extractionUi = extractionState.state
    val readerContent = readerState.content
    val focus = LocalFocusManager.current
    val addressFocusRequester = remember { FocusRequester() }

    var addressOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var addressValue by remember { mutableStateOf(TextFieldValue("")) }

    val readerOpen = readerContent != null
    val extractionOpen = extractionUi !is ExtractionState.Idle
    val overlayOpen = readerOpen || extractionOpen || addressOpen || settingsOpen
    val isNewTab = state.currentUrl.isBlank() || state.currentUrl.trimEnd('/') == UrlPolicy.HOME_URL.trimEnd('/')

    fun openAddress(initialText: String) {
        addressValue = TextFieldValue(
            text = initialText,
            selection = TextRange(0, initialText.length),
        )
        addressOpen = true
    }

    fun navigate(raw: String) {
        addressOpen = false
        focus.clearFocus()
        navigation.submit(raw)
    }

    BackHandler(enabled = state.canGoBack && !overlayOpen) { navigation.back() }
    BackHandler(enabled = addressOpen) {
        addressOpen = false
        focus.clearFocus()
    }
    BackHandler(enabled = settingsOpen) { settingsOpen = false }
    BackHandler(enabled = extractionOpen) { extraction.close() }
    BackHandler(enabled = readerOpen) { readerState.close() }

    LaunchedEffect(addressOpen) {
        if (addressOpen) addressFocusRequester.requestFocus()
    }

    Scaffold(
        bottomBar = {
            if (!overlayOpen) {
                BottomBar(
                    currentUrl = state.currentUrl,
                    canGoBack = state.canGoBack,
                    canGoForward = state.canGoForward,
                    extractEnabled = state.currentUrl.isNotBlank() && !state.isLoading && state.error == null,
                    onAddressClick = { openAddress(state.currentUrl) },
                    onBack = { navigation.back() },
                    onForward = { navigation.forward() },
                    onHome = { navigation.home() },
                    onRefresh = { navigation.refresh() },
                    onExtract = { extraction.extract() },
                    onSettings = { settingsOpen = true },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            AndroidView(
                factory = { context -> webController.createWebView(context) },
                modifier = Modifier.fillMaxSize(),
            )

            if (isNewTab && state.error == null && !overlayOpen) {
                NewTabPage(
                    onSearch = { openAddress("") },
                    onNavigate = ::navigate,
                    prefs = uiPreferences,
                )
            }

            if (state.isLoading && !overlayOpen) {
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                )
            }

            state.error?.let { error ->
                ErrorOverlay(
                    error = error,
                    onRetry = { navigation.refresh() },
                    onDismiss = { navigation.dismissError() },
                )
            }

            if (extractionOpen) {
                ExtractionPreviewScreen(
                    state = extractionUi,
                    onClose = { extraction.close() },
                    onRetry = { extraction.extract() },
                    onRead = { readerState.open(it) },
                )
            }

            if (readerContent != null) {
                ReaderScreen(
                    content = readerContent,
                    positions = readerPositions,
                    settingsRepository = readerSettings,
                    onClose = { readerState.close() },
                    translation = readerState.translation?.current,
                )
            }

            if (settingsOpen && !readerOpen && !extractionOpen) {
                PremiumSettings(
                    prefs = uiPreferences,
                    onClose = { settingsOpen = false },
                )
            }

            if (addressOpen && !readerOpen && !extractionOpen && !settingsOpen) {
                AddressBarOverlay(
                    currentUrl = state.currentUrl,
                    pageTitle = state.title,
                    overlayTextValue = addressValue,
                    overlayFocusRequester = addressFocusRequester,
                    onOverlayTextChange = { addressValue = it },
                    onDismiss = {
                        addressOpen = false
                        focus.clearFocus()
                    },
                    onNavigate = ::navigate,
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
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp),
            )
            Text(
                error.title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                error.message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss) { Text("Dismiss") }
                if (error.failingUrl != null) Button(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}
