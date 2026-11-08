package com.mnb.browser.security

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2G guard: MNB uses Google only. DuckDuckGo may not appear in the app's sources, except in the two
 * places that recognise OLD stored data and replace it: the old home address of saved tabs (UrlPolicy)
 * and the old default shortcut list (DefaultShortcuts).
 */
class NoDuckDuckGoGuardTest {
    private val appDir: File by lazy {
        val here = File(".").absoluteFile.normalize()
        when {
            File(here, "src/main/AndroidManifest.xml").isFile -> here
            File(here, "app/src/main/AndroidManifest.xml").isFile -> File(here, "app")
            else -> error("app module not found from $here")
        }
    }

    private val migrationFiles = setOf("UrlPolicy.kt", "DefaultShortcuts.kt")
    private val textExtensions = setOf("kt", "java", "xml", "json", "txt", "properties", "pro")

    private fun mentions(file: File) = Regex("duckduckgo", RegexOption.IGNORE_CASE).findAll(file.readText()).count()

    @Test fun noSourceOrResourceMentionsDuckDuckGoExceptTheTwoMigrationHelpers() {
        val offenders = File(appDir, "src/main").walkTopDown()
            .filter { it.isFile && it.extension in textExtensions && it.name !in migrationFiles }
            .filter { mentions(it) > 0 }
            .map { it.relativeTo(appDir).path }
            .toList()
        assertTrue("DuckDuckGo found in: $offenders", offenders.isEmpty())
    }

    @Test fun theBuildFilesDoNotMentionIt() {
        assertEquals(0, mentions(File(appDir, "build.gradle.kts")))
    }

    @Test fun urlPolicyMentionsItOnlyInTheOldHomeAddressConstant() {
        val file = File(appDir, "src/main/java/com/mnb/browser/navigation/UrlPolicy.kt")
        assertEquals(1, mentions(file))
        assertTrue(file.readText().contains("private const val LEGACY_HOME_URL"))
    }
}
