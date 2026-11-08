package com.mnb.browser.web

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2G guard: a new tab must never request a website, a long press only reports a link (it never
 * loads one), and the blank page is reported to the tab list as the new-tab address. Scans the source
 * because a real WebView cannot run in a JVM test.
 */
class NewTabWebViewGuardTest {
    private val source: String by lazy { sourceFile("web/WebViewController.kt").readText() }

    @Test fun aNewTabLoadsNothing() {
        val create = source.substringAfter("private fun createWebView(").substringBefore("private fun applySecureSettings")
        assertTrue(create.contains("UrlPolicy.isNewTabAddress(target)"))
        assertTrue(create.contains("if (!UrlPolicy.isNewTabAddress(target)) view.loadUrl(target)"))
        // The only other load in there is the crash-loop fallback of Phase 2F.
        assertEquals(2, Regex("view\\.loadUrl\\(").findAll(create).count())
    }

    @Test fun theNewTabPageIsShownByBlankingTheWebView() {
        val load = source.substringAfter("fun load(url: String)").substringBefore("fun goBack()")
        assertTrue(load.contains("UrlPolicy.BLANK_PAGE"))
        assertTrue(load.contains("UrlPolicy.isNewTabAddress(url)"))
    }

    @Test fun aStoredAddressThatIsNotAllowedBecomesTheNewTabPage() {
        val allowed = source.substringAfter("private fun allowedUrl(").substringBefore("private fun handleLongPress")
        assertTrue(allowed.contains("UrlPolicy.Decision.Block -> UrlPolicy.NEW_TAB_URL"))
    }

    @Test fun aLongPressOnlyReportsTheLink() {
        val press = source.substringAfter("private fun handleLongPress(").substringBefore("private fun destroyView")
        assertTrue(!press.contains("loadUrl("))
        assertTrue(press.contains("listener(tabId, link)"))
        assertTrue("only the active tab reports links", press.contains("tabId != activeId"))
        assertTrue(source.contains("view.setOnLongClickListener { handleLongPress(tabId, view) }"))
    }

    @Test fun theBlankPageIsReportedAsTheNewTabAddress() {
        assertTrue(Regex("pageListener\\?\\.invoke\\(tabId, UrlPolicy\\.reportedAddress\\(").findAll(source).count() >= 2)
    }

    @Test fun theScreenChecksALinkBeforeShowingIt() {
        val screen = sourceFile("ui/BrowserScreen.kt").readText()
        assertTrue(screen.contains("UrlPolicy.linkTarget(url)"))
        assertTrue(screen.contains("UrlPolicy.isNewTabAddress(state.currentUrl)"))
        assertTrue("the old comparison with the home address is gone", !screen.contains("UrlPolicy.HOME_URL"))
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
