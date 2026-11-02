package com.mnb.browser.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.mnb.browser.error.ErrorHandler
import com.mnb.browser.navigation.UrlPolicy
import com.mnb.browser.state.BrowserStateManager

/** Owns the single WebView, configures it securely, and reports events to the state manager. */
class WebViewController(private val stateManager: BrowserStateManager) {

private var webView: WebView? = null

@SuppressLint("SetJavaScriptEnabled") // required for almost all modern websites
fun createWebView(context: Context): WebView {
    val view = WebView(context)
    view.settings.apply {
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
    view.webViewClient = Client()
    view.webChromeClient = Chrome()
    webView = view
    if (view.url == null) load(UrlPolicy.HOME_URL)
    return view
}

fun load(url: String) { webView?.loadUrl(url) }
fun goBack() { webView?.takeIf { it.canGoBack() }?.goBack() }
fun goForward() { webView?.takeIf { it.canGoForward() }?.goForward() }
fun reload() { webView?.reload() }
fun onPause() { webView?.onPause() }
fun onResume() { webView?.onResume() }

fun destroy() {
    webView?.apply {
        stopLoading()
        webViewClient = WebViewClient()
        webChromeClient = null
        destroy()
    }
    webView = null
}

private inner class Client : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        return when (val decision = UrlPolicy.checkNavigation(url)) {
            UrlPolicy.Decision.Allow -> false
            is UrlPolicy.Decision.Upgrade -> { view.loadUrl(decision.url); true }
            UrlPolicy.Decision.Block -> {
                stateManager.onError(ErrorHandler.blocked(url))
                true
            }
        }
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        stateManager.onPageStarted(url)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        stateManager.onPageFinished(url)
        stateManager.onHistoryChanged(url, view.canGoBack(), view.canGoForward())
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        stateManager.onHistoryChanged(url, view.canGoBack(), view.canGoForward())
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (!request.isForMainFrame) return
        stateManager.onError(
            ErrorHandler.fromWebError(error.errorCode, error.description?.toString(), request.url.toString())
        )
    }

    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
        if (!request.isForMainFrame) return
        stateManager.onError(
            ErrorHandler.fromHttp(errorResponse.statusCode, errorResponse.reasonPhrase, request.url.toString())
        )
    }

    /** Never proceed past an SSL error; certificate validation is not bypassed. */
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
        stateManager.onError(ErrorHandler.fromSsl(error))
    }
}

private inner class Chrome : WebChromeClient() {
    override fun onProgressChanged(view: WebView, newProgress: Int) {
        stateManager.onProgress(newProgress)
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        stateManager.onTitle(title)
    }

    // Camera / mic / etc. are denied: Phase 01 has no permission UI.
    override fun onPermissionRequest(request: PermissionRequest) { request.deny() }

    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
        callback.invoke(origin, false, false)
    }

    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean = true
}

}
