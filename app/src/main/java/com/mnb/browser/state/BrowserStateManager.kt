package com.mnb.browser.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Single source of truth for what the browser UI shows. Updated by WebViewController. */
class BrowserStateManager {
var state by mutableStateOf(BrowserState())
private set

fun onPageStarted(url: String?) {
    state = state.copy(
        currentUrl = url ?: state.currentUrl,
        isLoading = true,
        progress = 0,
        error = null,
    )
}

fun onProgress(progress: Int) {
    state = state.copy(progress = progress, isLoading = progress < 100)
}

fun onPageFinished(url: String?) {
    state = state.copy(currentUrl = url ?: state.currentUrl, isLoading = false, progress = 100)
}

fun onTitle(title: String?) {
    state = state.copy(title = title.orEmpty())
}

fun onHistoryChanged(url: String?, canGoBack: Boolean, canGoForward: Boolean) {
    state = state.copy(
        currentUrl = url ?: state.currentUrl,
        canGoBack = canGoBack,
        canGoForward = canGoForward,
    )
}

fun onError(error: BrowserError) {
    state = state.copy(error = error, isLoading = false)
}

fun clearError() {
    state = state.copy(error = null)
}

}
