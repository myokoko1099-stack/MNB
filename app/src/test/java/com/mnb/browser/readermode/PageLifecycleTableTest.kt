package com.mnb.browser.readermode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageLifecycleTableTest {
    private data class Case(
        val name: String,
        val run: (PageSessionManager) -> Unit,
        val verify: (PageSessionManager) -> Unit,
    )

    @Test
    fun lifecycleTablePreservesTheRequiredIdentityRules() {
        val cases = listOf(
            Case(
                "new navigation creates a document",
                run = { it.onPageStarted("tab", "https://site.test/a") },
                verify = { assertEquals(1L, it.current("tab")?.navGeneration) },
            ),
            Case(
                "reload creates a new document",
                run = {
                    it.onPageStarted("tab", "https://site.test/a")
                    it.onReload("tab", "https://site.test/a")
                },
                verify = { assertEquals(2L, it.current("tab")?.navGeneration) },
            ),
            Case(
                "back forward callback without reload stays in document",
                run = {
                    val first = it.onPageStarted("tab", "https://site.test/a")
                    val updated = it.onHistoryUpdated("tab", "https://site.test/b", false)
                    assertTrue(first.sameDocumentAs(updated))
                },
                verify = { assertEquals("https://site.test/b", it.current("tab")?.normalizedUrl) },
            ),
            Case(
                "redirect page start invalidates the redirect predecessor",
                run = {
                    val first = it.onPageStarted("tab", "https://site.test/redirect")
                    val final = it.onPageStarted("tab", "https://site.test/final")
                    assertFalse(first.sameDocumentAs(final))
                },
                verify = { assertEquals(2L, it.current("tab")?.navGeneration) },
            ),
            Case(
                "duplicate page finished is idempotent",
                run = {
                    val first = it.onPageFinished("tab", "https://site.test/a")
                    val second = it.onPageFinished("tab", "https://site.test/a")
                    assertTrue(first.sameDocumentAs(second))
                },
                verify = { assertEquals(1L, it.current("tab")?.navGeneration) },
            ),
            Case(
                "unload invalidates old session",
                run = {
                    it.onPageStarted("tab", "https://site.test/a")
                    it.onWebViewUnloaded("tab")
                },
                verify = { assertEquals(null, it.current("tab")) },
            ),
            Case(
                "renderer crash invalidates old session",
                run = {
                    it.onPageStarted("tab", "https://site.test/a")
                    it.onRendererCrashed("tab")
                },
                verify = { assertEquals(null, it.current("tab")) },
            ),
            Case(
                "tab close clears identity and generation",
                run = {
                    it.onPageStarted("tab", "https://site.test/a")
                    it.closeTab("tab")
                },
                verify = {
                    assertEquals(null, it.current("tab"))
                    assertEquals(1L, it.onPageStarted("tab", "https://site.test/new").navGeneration)
                },
            ),
        )

        cases.forEach { testCase ->
            val manager = PageSessionManager()
            testCase.run(manager)
            testCase.verify(manager)
        }
    }
}
