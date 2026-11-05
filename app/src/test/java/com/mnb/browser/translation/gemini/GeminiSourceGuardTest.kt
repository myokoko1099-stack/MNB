package com.mnb.browser.translation.gemini

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 06-B1: a scan of the gemini sources. It fails if logging, cleartext HTTP, a key in a URL,
 * or a second reader of the plain key is added. It also checks that the provider is still not
 * wired into the app (that is a later session's job).
 */
class GeminiSourceGuardTest {

    @Test
    fun theGeminiSourcesWereFound() {
        val names = geminiSources().map { it.name }.toSet()
        assertTrue(names.toString(), "DirectGeminiProvider.kt" in names)
        assertTrue(names.toString(), "HttpUrlConnectionGeminiClient.kt" in names)
        assertTrue(names.toString(), "GeminiPromptBuilder.kt" in names)
        assertTrue(names.toString(), "GeminiResponseParser.kt" in names)
    }

    @Test
    fun noLoggingOrPrintingInTheGeminiCode() {
        val forbidden = listOf(
            "android.util.Log", "Log.d(", "Log.e(", "Log.w(", "Log.i(", "Log.v(", "Log.wtf(",
            "println(", "print(", "printStackTrace", "System.out", "System.err",
            "Timber", "java.util.logging", "Logger.",
        )
        val problems = mutableListOf<String>()
        for (file in geminiSources()) {
            val text = file.readText()
            for (token in forbidden) {
                if (text.contains(token)) problems += "${file.name} contains $token"
            }
        }
        assertTrue(problems.toString(), problems.isEmpty())
    }

    @Test
    fun noCleartextHttpAddressInTheGeminiCode() {
        for (file in geminiSources()) {
            assertFalse(file.name, file.readText().contains("http://", ignoreCase = true))
        }
    }

    @Test
    fun theKeyIsNeverPutInAUrl() {
        for (file in geminiSources()) {
            val text = file.readText()
            assertFalse(file.name, text.contains("?key=", ignoreCase = true))
            assertFalse(file.name, text.contains("&key=", ignoreCase = true))
            assertFalse(file.name, text.contains("addQueryParameter", ignoreCase = true))
        }
    }

    @Test
    fun onlyTheProviderReadsThePlainKey() {
        val readers = geminiSources().filter { it.readText().contains(".reveal()") }.map { it.name }
        assertEquals(listOf("DirectGeminiProvider.kt"), readers)
    }

    @Test
    fun theProviderDoesNotChangeOrDeleteTheStoredKey() {
        val provider = File(geminiRoot(), "DirectGeminiProvider.kt").readText()
        assertFalse(provider.contains("ApiKeyStore"))
        assertFalse(provider.contains(".save("))
        assertFalse(provider.contains(".update("))
        assertFalse(provider.contains(".delete("))
    }

    @Test
    fun noSharedOrHardcodedKeyInTheGeminiCode() {
        for (file in geminiSources()) {
            val text = file.readText()
            assertFalse(file.name, text.contains("AIza"))
            assertFalse(file.name, text.contains("BuildConfig"))
        }
    }

    @Test
    fun theProviderIsNotWiredIntoTheAppYet() {
        val root = mainSourceRoot()
        val users = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.parentFile.name != "gemini" }
            .filter { file ->
                val text = file.readText()
                text.contains("DirectGeminiProvider") || text.contains("translation.gemini")
            }
            .map { it.name }
            .toList()
        assertTrue("unexpected users of the Gemini provider: $users", users.isEmpty())
    }

    // ================= helpers =================

    private fun mainSourceRoot(): File {
        val candidates = listOf("src/main/java", "app/src/main/java").map { File(it) }
        return candidates.firstOrNull { it.isDirectory }
            ?: error("main sources not found from ${File(".").absoluteFile.normalize()}")
    }

    private fun geminiRoot(): File = File(mainSourceRoot(), "com/mnb/browser/translation/gemini")

    private fun geminiSources(): List<File> =
        geminiRoot().walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
}
