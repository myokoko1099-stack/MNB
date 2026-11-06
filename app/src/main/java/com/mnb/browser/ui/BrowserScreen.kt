package com.mnb.browser.ui

import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import com.mnb.browser.tabs.TabManager
import com.mnb.browser.web.WebViewController
import com.mnb.browser.ui.premium.AddressBarOverlay
import com.mnb.browser.ui.premium.BottomBar
import com.mnb.browser.ui.premium.NewTabPage
import com.mnb.browser.ui.premium.PremiumSettings
import com.mnb.browser.ui.premium.UiPreferences
import com.mnb.browser.ui.tabs.TabSwitcher

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
    tabManager: TabManager,
) {
    val state = stateManager.state
    val extractionUi = extractionState.state
    val readerContent = readerState.content
    val focus = LocalFocusManager.current
    val addressFocusRequester = remember { FocusRequester() }

    var addressOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var switcherOpen by rememberSaveable { mutableStateOf(false) }
    // Saveable, so the text being edited survives a rotation / process death together with addressOpen.
    var addressValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }

    val readerOpen = readerContent != null
    val extractionOpen = extractionUi !is ExtractionState.Idle
    val overlayOpen = readerOpen || extractionOpen || addressOpen || settingsOpen || switcherOpen
    val isNewTab = state.currentUrl.isBlank() || state.currentUrl.trimEnd('/') == UrlPolicy.HOME_URL.trimEnd('/')
    // On the new-tab page the blank home address is an implementation detail: never show or edit it.
    val shownUrl = if (isNewTab) "" else state.currentUrl
    val activeTab = tabManager.activeTab

    fun openAddress(initialText: String) {
        // Keep the whole address and put the cursor at its end. Nothing is pre-selected, so the
        // first key press cannot wipe the URL; the user selects or clears it on purpose.
        addressValue = TextFieldValue(
            text = initialText,
            selection = TextRange(initialText.length),
        )
        addressOpen = true
    }

    fun navigate(raw: String) {
        addressOpen = false
        focus.clearFocus()
        navigation.submit(raw)
    }

    BackHandler(enabled = state.canGoBack && !overlayOpen) { navigation.back() }
    BackHandler(enabled = switcherOpen) { switcherOpen = false }
    BackHandler(enabled = addressOpen) {
        addressOpen = false
        focus.clearFocus()
    }
    BackHandler(enabled = settingsOpen) { settingsOpen = false }
    BackHandler(enabled = extractionOpen) { extraction.close() }
    BackHandler(enabled = readerOpen) { readerState.close() }

    // Focus is requested by AddressBarOverlay itself, once its text field exists. Requesting it
    // from here could run while the overlay is hidden (e.g. the reader is open) and would crash.

    Scaffold(
        bottomBar = {
            if (!overlayOpen) {
                BottomBar(
                    currentUrl = shownUrl,
                    canGoBack = state.canGoBack,
                    canGoForward = state.canGoForward,
                    extractEnabled = state.currentUrl.isNotBlank() && !state.isLoading && state.error == null,
                    onAddressClick = { openAddress(shownUrl) },
                    onBack = { navigation.back() },
                    onForward = { navigation.forward() },
                    onHome = { navigation.home() },
                    onRefresh = { navigation.refresh() },
                    onExtract = { extraction.extract() },
                    onSettings = { settingsOpen = true },
                    tabCount = tabManager.tabs.size,
                    onTabs = { switcherOpen = true },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            // One host view for every tab. The controller detaches the previous tab's WebView from
            // it before attaching the next one, so a WebView never has two parents.
            if (activeTab != null) {
                val activeId = activeTab.id
                val activeUrl = activeTab.url
                AndroidView(
                    factory = { context -> FrameLayout(context) },
                    update = { host -> webController.mount(host, activeId, activeUrl) },
                    modifier = Modifier.fillMaxSize(),
                )
            }

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
                    currentUrl = shownUrl,
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

            AnimatedVisibility(
                visible = switcherOpen && !readerOpen && !extractionOpen,
                enter = fadeIn() + scaleIn(initialScale = 0.94f),
                exit = fadeOut() + scaleOut(targetScale = 0.94f),
            ) {
                TabSwitcher(
                    tabs = tabManager.tabs,
                    activeTabId = tabManager.activeTabId,
                    canOpenNewTab = tabManager.canOpenNewTab,
                    onSelect = { id ->
                        tabManager.switchTo(id)
                        switcherOpen = false
                    },
                    onClose = { id -> tabManager.close(id) },
                    onNewTab = {
                        if (tabManager.newTab() != null) switcherOpen = false
                    },
                    onDismiss = { switcherOpen = false },
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
