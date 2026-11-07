package com.mnb.browser.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mnb.browser.error.ErrorHandler
import com.mnb.browser.extraction.ExtractionScript
import com.mnb.browser.navigation.UrlPolicy
import com.mnb.browser.state.BrowserError
import com.mnb.browser.state.BrowserStateManager
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/**
 * Owns one WebView per tab, configures every one of them securely, and reports the events of the
 * ACTIVE tab to the state manager.
 *
 * Phase 2B (multi-tab):
 * - Each tab has its own WebView, so each tab has its own URL and its own back/forward history.
 * - A WebView is only ever attached to one parent. [mount] detaches the previous tab's WebView from
 *   the host before attaching the new one; a WebView is destroyed only after it was detached.
 * - Each tab's current page and history are saved to its own session store (when
 *   [sessionStoreFactory] is given) when the app pauses and when the user leaves the tab, and are
 *   restored the next time that tab's WebView is created.
 * - Cookies and web storage are NOT isolated between tabs: all WebViews of the app share one cookie
 *   store. Only the page state and navigation history are per tab.
 * - Events (progress, errors) of tabs in the background never reach the UI state; security
 *   decisions (SSL errors, permissions, URL policy) are applied to every tab.
 *
 * Phase 2F (lifecycle and resilience):
 * - At most [maxLiveViews] WebViews stay alive. When more are open, the least recently used
 *   background tabs are unloaded: their state is saved first and the WebView is rebuilt from it the
 *   next time the tab is shown. The active tab is never unloaded.
 * - When the system asks for memory ([trimBackgroundViews]) every background WebView is unloaded.
 * - A renderer crash or kill ([Client.onRenderProcessGone]) never takes the app down: the dead
 *   WebView is destroyed and the active tab is rebuilt from its saved state. A tab that crashes
 *   again right after that (see [CrashLoopGuard]) is not restored; it opens the home page and shows
 *   an error with "Retry" instead.
 */
class WebViewController(
    private val stateManager: BrowserStateManager,
    private val sessionStoreFactory: ((tabId: String) -> WebSessionStore)? = null,
    private val ioExecutor: Executor = Executor { task -> task.run() },
    private val maxLiveViews: Int = LiveTabPolicy.DEFAULT_MAX_LIVE,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val crashGuard: CrashLoopGuard = CrashLoopGuard(),
) {

    /**
     * Changes every time the active tab's WebView had to be thrown away and must be mounted again
     * (renderer crash). The screen reads it, so it recomposes and calls [mount]. Main thread.
     */
    var recoveryTick by mutableStateOf(0)
        private set

    /** Receives address/title changes of any tab, also tabs in the background. Main thread. */
    var pageListener: ((tabId: String, url: String?, title: String?) -> Unit)? = null

    /**
     * Phase 2E: called once per finished page load of any tab, but only when the load had no
     * main-frame error (network, HTTP or SSL). May be called more than once for the same page
     * (redirects, repeated callbacks), so the receiver must de-duplicate. Main thread.
     */
    var visitListener: ((tabId: String, url: String?, title: String?) -> Unit)? = null

    private val views = HashMap<String, WebView>()
    private val stores = HashMap<String, WebSessionStore>()
    private var activeId: String? = null

    /** Tabs whose current main-frame load failed; such a load is not a visit. Main thread. */
    private val failedLoads = HashSet<String>()

    /** Tabs that have a WebView, least recently shown first. Main thread. */
    private val useOrder = ArrayList<String>()

    /**
     * State of tabs that were unloaded, kept until the tab is shown again, so a fast switch back
     * never reads an older file while the background write is still queued. Main thread.
     */
    private val unloadedStates = HashMap<String, ByteArray>()

    /**
     * Crash-looping tabs: they restart on the home page, and the error is shown once that page has
     * finished loading (starting a page clears errors, so showing it earlier would lose it). Main thread.
     */
    private val haltedTabs = HashMap<String, BrowserError>()

    /**
     * Shows the WebView of [tabId] inside [host] (creating or restoring it when needed). Safe to call
     * on every recomposition: when the tab is already shown it does nothing.
     */
    fun mount(host: ViewGroup, tabId: String, initialUrl: String) {
        val view = views[tabId] ?: createWebView(host.context, tabId, initialUrl)
        if (activeId != tabId) activate(tabId, view, initialUrl)
        if (view.parent !== host) {
            // Detach whatever was shown before, and the view from any other parent, then attach.
            host.removeAllViews()
            (view.parent as? ViewGroup)?.removeView(view)
            host.addView(
                view,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
        }
    }

    /** Detaches, then destroys, the WebView of a closed tab and deletes its saved session. */
    fun closeTab(tabId: String) {
        views.remove(tabId)?.let { destroyView(it) }
        if (activeId == tabId) activeId = null
        stores.remove(tabId)
        failedLoads.remove(tabId)
        useOrder.remove(tabId)
        unloadedStates.remove(tabId)
        haltedTabs.remove(tabId)
        crashGuard.forget(tabId)
        sessionStoreFactory?.invoke(tabId)?.let { store -> runIo { store.clear() } }
    }

    /**
     * The system is short of memory: unloads the WebView of every tab except the active one. Each
     * of them is saved first and comes back, with its history, when the tab is shown again.
     */
    fun trimBackgroundViews() {
        for (id in LiveTabPolicy.tabsToUnload(liveIdsOldestFirst(), activeId, maxLive = 1)) unloadTab(id)
    }

    fun load(url: String) { activeView()?.loadUrl(url) }
    fun goBack() { activeView()?.takeIf { it.canGoBack() }?.goBack() }
    fun goForward() { activeView()?.takeIf { it.canGoForward() }?.goForward() }
    fun reload() { activeView()?.reload() }

    /**
     * Runs the extraction script on the current page of the active tab. [onResult] receives the raw
     * JSON text (or null on timeout) and the page URL, always on the main thread, exactly once.
     * Returns false when there is no WebView to run it in.
     */
    fun runExtraction(onResult: (rawJson: String?, url: String?) -> Unit): Boolean {
        val view = activeView() ?: return false
        var delivered = false
        val timeout = Runnable {
            if (!delivered) {
                delivered = true
                onResult(null, view.url)
            }
        }
        view.postDelayed(timeout, EXTRACTION_TIMEOUT_MS)
        view.evaluateJavascript(ExtractionScript.SOURCE) { raw ->
            if (!delivered) {
                delivered = true
                view.removeCallbacks(timeout)
                onResult(raw, view.url)
            }
        }
        return true
    }

    fun onPause() {
        for ((id, view) in views) saveSession(id, view)
        CookieManager.getInstance().flush()
        activeView()?.onPause()
    }

    fun onResume() { activeView()?.onResume() }

    fun destroy() {
        for (view in views.values) destroyView(view)
        views.clear()
        stores.clear()
        failedLoads.clear()
        useOrder.clear()
        unloadedStates.clear()
        haltedTabs.clear()
        activeId = null
    }

    private fun activeView(): WebView? = activeId?.let { views[it] }

    private fun activate(tabId: String, view: WebView, fallbackUrl: String) {
        val previousId = activeId
        if (previousId != null) {
            views[previousId]?.let { previous ->
                saveSession(previousId, previous)
                previous.onPause()
            }
        }
        activeId = tabId
        useOrder.remove(tabId)
        useOrder.add(tabId)
        view.onResume()
        stateManager.onTabSwitched(
            url = view.url ?: fallbackUrl,
            title = view.title.orEmpty(),
            canGoBack = view.canGoBack(),
            canGoForward = view.canGoForward(),
            progress = view.progress,
        )
        unloadExcessViews()
    }

    /** Tabs that have a WebView, least recently shown first. */
    private fun liveIdsOldestFirst(): List<String> = views.keys.sortedBy { useOrder.indexOf(it) }

    private fun unloadExcessViews() {
        for (id in LiveTabPolicy.tabsToUnload(liveIdsOldestFirst(), activeId, maxLiveViews)) unloadTab(id)
    }

    /**
     * Throws away the WebView of a background tab but keeps the tab: its state is saved (in memory
     * at once, on disk in the background) and the next [mount] of it rebuilds the WebView from that.
     */
    private fun unloadTab(tabId: String) {
        if (tabId == activeId) return
        val view = views.remove(tabId) ?: return
        useOrder.remove(tabId)
        failedLoads.remove(tabId)
        val bytes = snapshot(view)
        if (bytes != null) {
            unloadedStates[tabId] = bytes
            storeFor(tabId)?.let { store -> runIo { store.save(bytes) } }
        }
        destroyView(view)
    }

    /**
     * The renderer behind [view] crashed or was killed by the system. Such a WebView can never be
     * used again: it is detached and destroyed. If it belonged to the active tab, the tab is built
     * again (see [recoveryTick]); a tab in the background is simply rebuilt when it is shown next.
     */
    private fun onRenderGone(tabId: String, view: WebView, didCrash: Boolean) {
        if (views[tabId] !== view) return // already replaced or unloaded
        val wasActive = tabId == activeId
        val pageUrl = try { view.url } catch (e: Exception) { null }
        views.remove(tabId)
        useOrder.remove(tabId)
        failedLoads.remove(tabId)
        destroyView(view)
        val looping = didCrash && crashGuard.recordCrash(tabId, clock())
        if (looping) {
            // Rebuilding the same page would crash again: forget its state and start on the home page.
            unloadedStates.remove(tabId)
            storeFor(tabId)?.let { store -> runIo { store.clear() } }
            val retryUrl = pageUrl?.takeIf { UrlPolicy.checkNavigation(it) == UrlPolicy.Decision.Allow }
            haltedTabs[tabId] = ErrorHandler.renderGone(retryUrl)
        }
        if (wasActive) {
            activeId = null
            recoveryTick++
        }
    }

    private fun createWebView(context: Context, tabId: String, initialUrl: String): WebView {
        val view = WebView(context)
        applySecureSettings(view.settings)
        view.webViewClient = Client(tabId)
        view.webChromeClient = Chrome(tabId)
        views[tabId] = view
        if (tabId in haltedTabs) {
            view.loadUrl(UrlPolicy.HOME_URL)
        } else if (!restoreSession(tabId, view)) {
            view.loadUrl(allowedUrl(initialUrl))
        }
        return view
    }

    /** The one place where WebView security settings are set; every tab goes through it. */
    @SuppressLint("SetJavaScriptEnabled") // required for almost all modern websites
    private fun applySecureSettings(settings: WebSettings) {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            setGeolocationEnabled(false)
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
        }
    }

    /** A stored address is data from disk: it must pass the URL policy before it is loaded. */
    private fun allowedUrl(url: String): String = when (val decision = UrlPolicy.checkNavigation(url)) {
        UrlPolicy.Decision.Allow -> url
        is UrlPolicy.Decision.Upgrade -> decision.url
        UrlPolicy.Decision.Block -> UrlPolicy.HOME_URL
    }

    private fun destroyView(view: WebView) {
        (view.parent as? ViewGroup)?.removeView(view)
        view.stopLoading()
        view.webViewClient = WebViewClient()
        view.webChromeClient = null
        view.destroy()
    }

    private fun storeFor(tabId: String): WebSessionStore? {
        val factory = sessionStoreFactory ?: return null
        return stores.getOrPut(tabId) { factory(tabId) }
    }

    private fun runIo(task: () -> Unit) {
        try {
            ioExecutor.execute { task() }
        } catch (e: RejectedExecutionException) {
            // The app is shutting down; the session is simply not saved.
        }
    }

    /** The page and history of [view] as bytes, or null when there is nothing worth saving. */
    private fun snapshot(view: WebView): ByteArray? {
        if (view.url == null) return null
        val state = Bundle()
        if (view.saveState(state) == null) return null
        val parcel = Parcel.obtain()
        return try {
            parcel.writeBundle(state)
            parcel.marshall()
        } catch (e: Exception) {
            null
        } finally {
            parcel.recycle()
        }
    }

    private fun saveSession(tabId: String, view: WebView) {
        val store = storeFor(tabId) ?: return
        val bytes = snapshot(view) ?: return
        runIo { store.save(bytes) }
    }

    private fun restoreSession(tabId: String, view: WebView): Boolean {
        // A tab that was just unloaded has its newest state in memory; the file may not be written yet.
        val bytes = unloadedStates.remove(tabId) ?: storeFor(tabId)?.load() ?: return false
        val parcel = Parcel.obtain()
        return try {
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            val state = parcel.readBundle(WebView::class.java.classLoader)
            state != null && view.restoreState(state) != null
        } catch (e: Exception) {
            false
        } finally {
            parcel.recycle()
        }
    }

    private inner class Client(private val tabId: String) : WebViewClient() {
        private fun isActive() = tabId == activeId

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            return when (val decision = UrlPolicy.checkNavigation(url)) {
                UrlPolicy.Decision.Allow -> false
                is UrlPolicy.Decision.Upgrade -> { view.loadUrl(decision.url); true }
                UrlPolicy.Decision.Block -> {
                    if (isActive()) stateManager.onError(ErrorHandler.blocked(url))
                    true
                }
            }
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            failedLoads.remove(tabId)
            if (isActive()) stateManager.onPageStarted(url)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            // The first finished page of a crash-looping tab is the home page it restarted on.
            val haltNotice = haltedTabs.remove(tabId)
            if (isActive()) {
                stateManager.onPageFinished(url)
                stateManager.onHistoryChanged(url, view.canGoBack(), view.canGoForward())
                if (haltNotice != null) stateManager.onError(haltNotice)
            }
            pageListener?.invoke(tabId, view.url ?: url, view.title)
            if (tabId !in failedLoads) visitListener?.invoke(tabId, view.url ?: url, view.title)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            if (isActive()) stateManager.onHistoryChanged(url, view.canGoBack(), view.canGoForward())
            pageListener?.invoke(tabId, url, null)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) failedLoads.add(tabId)
            if (!request.isForMainFrame || !isActive()) return
            stateManager.onError(
                ErrorHandler.fromWebError(error.errorCode, error.description?.toString(), request.url.toString())
            )
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
            if (request.isForMainFrame) failedLoads.add(tabId)
            if (!request.isForMainFrame || !isActive()) return
            stateManager.onError(
                ErrorHandler.fromHttp(errorResponse.statusCode, errorResponse.reasonPhrase, request.url.toString())
            )
        }

        /**
         * The renderer behind this WebView died. Returning true keeps the app alive; returning false
         * (the default) would crash it. The WebView is unusable and is replaced, see [onRenderGone].
         */
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            onRenderGone(tabId, view, detail.didCrash())
            return true
        }

        /** Never continue past an SSL error, in any tab; certificate validation is not bypassed. */
        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel()
            failedLoads.add(tabId)
            if (isActive()) stateManager.onError(ErrorHandler.fromSsl(error))
        }
    }

    private inner class Chrome(private val tabId: String) : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            if (tabId == activeId) stateManager.onProgress(newProgress)
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            if (tabId == activeId) stateManager.onTitle(title)
            pageListener?.invoke(tabId, null, title)
        }

        // Camera / mic / etc. are denied: there is no permission UI.
        override fun onPermissionRequest(request: PermissionRequest) { request.deny() }

        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            callback.invoke(origin, false, false)
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean = true
    }

    private companion object {
        const val EXTRACTION_TIMEOUT_MS = 15_000L
    }
}
