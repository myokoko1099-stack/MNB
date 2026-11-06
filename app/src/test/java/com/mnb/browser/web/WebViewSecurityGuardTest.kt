package com.mnb.browser.web

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2B guard: the WebView security rules must still be in WebViewController after it became
 * multi-tab, and every tab's WebView must be built through the one secured path. It scans the source
 * (like ProjectSecretsGuardTest) because WebView settings cannot be read back reliably in a JVM test.
 */
class WebViewSecurityGuardTest {

    private val source: String by lazy { sourceFile().readText() }

    @Test fun theControllerSourceWasFound() {
        assertTrue(source.contains("class WebViewController"))
    }

    @Test fun theSecureSettingsAreStillSet() {
        val required = listOf(
            "allowFileAccess = false",
            "allowContentAccess = false",
            "mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW",
            "javaScriptCanOpenWindowsAutomatically = false",
            "setSupportMultipleWindows(false)",
            "setGeolocationEnabled(false)",
            "safeBrowsingEnabled = true",
        )
        for (line in required) assertTrue("missing: $line", source.contains(line))
    }

    @Test fun nothingWeakensTheSettings() {
        val forbidden = listOf(
            "allowFileAccess = true",
            "allowContentAccess = true",
            "allowFileAccessFromFileURLs = true",
            "allowUniversalAccessFromFileURLs = true",
            "MIXED_CONTENT_ALWAYS_ALLOW",
            "MIXED_CONTENT_COMPATIBILITY_MODE",
            "setGeolocationEnabled(true)",
            "safeBrowsingEnabled = false",
            "setSupportMultipleWindows(true)",
        )
        for (line in forbidden) assertTrue("must not appear: $line", !source.contains(line))
    }

    @Test fun sslErrorsAreCancelledAndNeverBypassed() {
        assertTrue(source.contains("handler.cancel()"))
        assertTrue(!source.contains("handler.proceed"))
    }

    @Test fun webPermissionsAndGeolocationAreDenied() {
        assertTrue(source.contains("request.deny()"))
        assertTrue(source.contains("callback.invoke(origin, false, false)"))
        assertTrue(!source.contains("request.grant"))
    }

    @Test fun theUrlPolicyIsStillChecked() {
        assertTrue(source.contains("UrlPolicy.checkNavigation(url)"))
    }

    @Test fun everyTabWebViewIsBuiltThroughTheSecuredPath() {
        assertEquals("exactly one place may construct a WebView", 1, Regex("WebView\\(context\\)").findAll(source).count())
        assertEquals("settings are applied in exactly one function", 1, Regex("applySecureSettings\\(view\\.settings\\)").findAll(source).count())
        val create = source.substringAfter("private fun createWebView(").substringBefore("private fun applySecureSettings")
        assertTrue(create.contains("applySecureSettings(view.settings)"))
        assertTrue(create.contains("view.webViewClient = Client(tabId)"))
        assertTrue(create.contains("view.webChromeClient = Chrome(tabId)"))
    }

    @Test fun aWebViewIsDetachedBeforeItIsDestroyed() {
        val destroy = source.substringAfter("private fun destroyView(").substringBefore("private fun storeFor")
        val detach = destroy.indexOf("removeView(view)")
        val destroyCall = destroy.indexOf("view.destroy()")
        assertTrue(detach >= 0 && destroyCall > detach)
    }

    private fun sourceFile(): File {
        val here = File(".").absoluteFile.normalize()
        val app = when {
            File(here, "src/main/AndroidManifest.xml").isFile -> here
            File(here, "app/src/main/AndroidManifest.xml").isFile -> File(here, "app")
            else -> error("app module not found from $here")
        }
        return File(app, "src/main/java/com/mnb/browser/web/WebViewController.kt")
    }
}
