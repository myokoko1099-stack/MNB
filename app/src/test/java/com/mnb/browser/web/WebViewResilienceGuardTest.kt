package com.mnb.browser.web

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2F guard: the crash recovery and the unloading of background tabs must stay in
 * WebViewController, and must not weaken the security rules (see WebViewSecurityGuardTest). Scans the
 * source because a real WebView cannot run in a JVM test.
 */
class WebViewResilienceGuardTest {

    private val source: String by lazy { sourceFile("web/WebViewController.kt").readText() }

    @Test fun aDeadRendererNeverCrashesTheApp() {
        assertTrue(source.contains("override fun onRenderProcessGone("))
        val body = source.substringAfter("override fun onRenderProcessGone(").substringBefore("override fun onReceivedSslError")
        assertTrue("must return true so the app survives", body.contains("return true"))
        assertTrue(!body.contains("return false"))
    }

    @Test fun aDeadWebViewIsDestroyedAndNeverReused() {
        val handler = source.substringAfter("private fun onRenderGone(").substringBefore("private fun createWebView(")
        assertTrue(handler.contains("views.remove(tabId)"))
        assertTrue(handler.contains("destroyView(view)"))
        // The dead view must not be saved: its state is not valid any more.
        assertTrue(!handler.contains("snapshot(view)"))
        assertTrue(!handler.contains("saveSession("))
    }

    @Test fun aCrashLoopingTabRestartsOnTheHomePageAndNeverRestoresItsState() {
        assertTrue(source.contains("crashGuard.recordCrash(tabId, clock())"))
        val create = source.substringAfter("private fun createWebView(").substringBefore("private fun applySecureSettings")
        assertTrue(create.contains("if (tabId in haltedTabs)"))
        assertTrue(create.contains("view.loadUrl(UrlPolicy.HOME_URL)"))
    }

    @Test fun theRetryAddressOfACrashedPageMustPassTheUrlPolicy() {
        val handler = source.substringAfter("private fun onRenderGone(").substringBefore("private fun createWebView(")
        assertTrue(handler.contains("UrlPolicy.checkNavigation(it) == UrlPolicy.Decision.Allow"))
    }

    @Test fun theActiveTabIsNeverUnloaded() {
        val unload = source.substringAfter("private fun unloadTab(").substringBefore("private fun onRenderGone(")
        assertTrue(unload.contains("if (tabId == activeId) return"))
    }

    @Test fun anUnloadedTabIsSavedBeforeItsWebViewIsDestroyed() {
        val unload = source.substringAfter("private fun unloadTab(").substringBefore("private fun onRenderGone(")
        val save = unload.indexOf("snapshot(view)")
        val destroy = unload.indexOf("destroyView(view)")
        assertTrue(save >= 0 && destroy > save)
        assertTrue(unload.contains("unloadedStates[tabId] = bytes"))
    }

    @Test fun theNewestInMemoryStateWinsWhenAnUnloadedTabComesBack() {
        val restore = source.substringAfter("private fun restoreSession(").substringBefore("private inner class Client")
        assertTrue(restore.contains("unloadedStates.remove(tabId) ?: storeFor(tabId)?.load()"))
    }

    @Test fun closingATabForgetsEverythingAboutIt() {
        val close = source.substringAfter("fun closeTab(").substringBefore("fun trimBackgroundViews")
        for (needle in listOf("useOrder.remove(tabId)", "unloadedStates.remove(tabId)", "haltedTabs.remove(tabId)", "crashGuard.forget(tabId)")) {
            assertTrue("closeTab must contain $needle", close.contains(needle))
        }
    }

    @Test fun everyActivationEnforcesTheLiveViewLimit() {
        val activate = source.substringAfter("private fun activate(").substringBefore("/** Tabs that have a WebView")
        assertTrue(activate.contains("unloadExcessViews()"))
    }

    @Test fun theScreenRemountsTheActiveTabAfterACrash() {
        val screen = sourceFile("ui/BrowserScreen.kt").readText()
        assertTrue(screen.contains("key(webController.recoveryTick)"))
    }

    @Test fun onlyMemoryPressureWhileRunningTrimsTheTabs() {
        val activity = sourceFile("MainActivity.kt").readText()
        val trim = activity.substringAfter("override fun onTrimMemory(").substringBefore("override fun onResume()")
        assertTrue(trim.contains("TRIM_MEMORY_RUNNING_LOW"))
        assertTrue(trim.contains("TRIM_MEMORY_UI_HIDDEN"))
        assertTrue(trim.contains("webController.trimBackgroundViews()"))
    }

    @Test fun theDefaultLimitIsBelowTheTabLimit() {
        assertTrue(LiveTabPolicy.DEFAULT_MAX_LIVE >= 2)
        assertTrue(LiveTabPolicy.DEFAULT_MAX_LIVE < com.mnb.browser.tabs.TabManager.MAX_TABS)
        assertEquals(6, LiveTabPolicy.DEFAULT_MAX_LIVE)
    }

    private fun sourceFile(relative: String): File {
        val here = File(".").absoluteFile.normalize()
        val app = when {
            File(here, "src/main/AndroidManifest.xml").isFile -> here
            File(here, "app/src/main/AndroidManifest.xml").isFile -> File(here, "app")
            else -> error("app module not found from $here")
        }
        return File(app, "src/main/java/com/mnb/browser/$relative")
    }
}
