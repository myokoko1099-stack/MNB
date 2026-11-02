package com.mnb.browser.navigation

import com.mnb.browser.error.ErrorHandler
import com.mnb.browser.state.BrowserStateManager
import com.mnb.browser.web.WebViewController

/** Turns user intents (go, back, forward, home, refresh) into WebView actions. */
class NavigationController(
private val web: WebViewController,
private val stateManager: BrowserStateManager,
) {
fun submit(rawInput: String) {
when (val result = UrlPolicy.resolve(rawInput)) {
UrlPolicy.Input.Empty -> Unit
is UrlPolicy.Input.Blocked -> stateManager.onError(ErrorHandler.blocked(rawInput, result.reason))
is UrlPolicy.Input.Ok -> {
stateManager.clearError()
web.load(result.url)
}
}
}

fun back() {
    stateManager.clearError()
    web.goBack()
}

fun forward() {
    stateManager.clearError()
    web.goForward()
}

fun home() {
    stateManager.clearError()
    web.load(UrlPolicy.HOME_URL)
}

/** Reloads the current page; if the last load failed, retries the failed URL. */
fun refresh() {
    val failing = stateManager.state.error?.failingUrl
    stateManager.clearError()
    if (failing != null) web.load(failing) else web.reload()
}

fun dismissError() = stateManager.clearError()

}
