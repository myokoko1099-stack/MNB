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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.mnb.browser.library.BrowserLibrary
import com.mnb.browser.library.PageRules
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
import com.mnb.browser.ui.library.BookmarksScreen
import com.mnb.browser.ui.library.HistoryScreen
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
    library: BrowserLibrary,
) {
    val state = stateManager.state
    val extractionUi = extractionState.state
    val readerContent = readerState.content
    val focus = LocalFocusManager.current
    val addressFocusRequester = remember { FocusRequester() }

    var addressOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var switcherOpen by rememberSaveable { mutableStateOf(false) }
    var bookmarksOpen by rememberSaveable { mutableStateOf(false) }
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    var historyQuery by rememberSaveable { mutableStateOf("") }
    // Phase 2G: a link the user long-pressed, waiting for "Open in new tab" (already checked by UrlPolicy).
    var pendingLink by rememberSaveable { mutableStateOf<String?>(null) }
    // Saveable, so the text being edited survives a rotation / process death together with addressOpen.
    var addressValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }

    val readerOpen = readerContent != null
    val extractionOpen = extractionUi !is ExtractionState.Idle
    val overlayOpen = readerOpen || extractionOpen || addressOpen || settingsOpen || switcherOpen ||
        bookmarksOpen || historyOpen
    // Only the app's own new-tab address (or no address) is the new-tab page. A real website, Google
    // included, is always shown as a website.
    val isNewTab = UrlPolicy.isNewTabAddress(state.currentUrl)
    // On the new-tab page its internal address is an implementation detail: never show or edit it.
    val shownUrl = if (isNewTab) "" else state.currentUrl
    val activeTab = tabManager.activeTab
    // A page can only be bookmarked once it has loaded without an error (never the new-tab page).
    val canBookmark = PageRules.isRecordable(shownUrl) && state.error == null

    // Phase 2E: keep the "bookmarked" state of the current page up to date, and (re)load a list
    // whenever its screen opens, also after the screen was restored with the Activity.
    LaunchedEffect(shownUrl) { library.checkBookmarked(shownUrl) }
    LaunchedEffect(bookmarksOpen) { if (bookmarksOpen) library.refreshBookmarks() }
    LaunchedEffect(historyOpen) { if (historyOpen) library.refreshHistory(historyQuery) else historyQuery = "" }

    // Phase 2G: long press on a link of the active tab. The address is validated before anything is shown.
    DisposableEffect(webController) {
        webController.linkLongPressListener = { _, url -> UrlPolicy.linkTarget(url)?.let { pendingLink = it } }
        onDispose { webController.linkLongPressListener = null }
    }

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
    BackHandler(enabled = bookmarksOpen) { bookmarksOpen = false }
    BackHandler(enabled = historyOpen) { historyOpen = false }
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
                    extractEnabled = !isNewTab && !state.isLoading && state.error == null,
                    onAddressClick = { openAddress(shownUrl) },
                    onBack = { navigation.back() },
                    onForward = { navigation.forward() },
                    onHome = { navigation.home() },
                    onRefresh = { navigation.refresh() },
                    onExtract = { extraction.extract() },
                    onSettings = { settingsOpen = true },
                    tabCount = tabManager.tabs.size,
                    onTabs = { switcherOpen = true },
                    isBookmarked = library.currentBookmarked,
                    bookmarkEnabled = canBookmark,
                    onToggleBookmark = { library.toggleBookmark(shownUrl, state.title) },
                    onBookmarks = { bookmarksOpen = true },
                    onHistory = { historyOpen = true },
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
                // Phase 2F: after a renderer crash the active tab's WebView is gone. The tick changes
                // then, so the host is built again and the tab is mounted from its saved state.
                key(webController.recoveryTick) {
                    AndroidView(
                        factory = { context -> FrameLayout(context) },
                        update = { host -> webController.mount(host, activeId, activeUrl) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
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

            pendingLink?.let { link ->
                LinkDialog(
                    link = link,
                    canOpenNewTab = tabManager.canOpenNewTab,
                    onOpenInNewTab = { if (tabManager.newTab(link) != null) pendingLink = null },
                    onDismiss = { pendingLink = null },
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

            AnimatedVisibility(
                visible = bookmarksOpen && !readerOpen && !extractionOpen,
                enter = fadeIn() + scaleIn(initialScale = 0.94f),
                exit = fadeOut() + scaleOut(targetScale = 0.94f),
            ) {
                BookmarksScreen(
                    entries = library.bookmarks,
                    state = library.bookmarksState,
                    onOpen = { url ->
                        bookmarksOpen = false
                        navigate(url)
                    },
                    onDelete = { id -> library.removeBookmark(id) },
                    onRetry = { library.refreshBookmarks() },
                    onClose = { bookmarksOpen = false },
                )
            }

            AnimatedVisibility(
                visible = historyOpen && !readerOpen && !extractionOpen,
                enter = fadeIn() + scaleIn(initialScale = 0.94f),
                exit = fadeOut() + scaleOut(targetScale = 0.94f),
            ) {
                HistoryScreen(
                    entries = library.history,
                    state = library.historyState,
                    query = historyQuery,
                    onQueryChange = { text ->
                        historyQuery = text
                        library.refreshHistory(text)
                    },
                    onOpen = { url ->
                        historyOpen = false
                        navigate(url)
                    },
                    onDelete = { id -> library.removeHistoryEntry(id) },
                    onClearAll = { library.clearHistory() },
                    onRetry = { library.refreshHistory(historyQuery) },
                    onClose = { historyOpen = false },
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
