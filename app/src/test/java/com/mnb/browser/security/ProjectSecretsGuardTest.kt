package com.mnb.browser.security

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 06-B4: a project-wide scan for secrets and risky build settings. The earlier guards only
 * look at the gemini, security and settings sources; this one covers every main source file, the
 * Gradle files, the workflow files and the manifest. It prints file names only, never matched text.
 *
 * It fails if a Google API key or private key block is committed, if a signing password is written
 * into a Gradle file, if a keystore or credential file is in the project, if .gitignore stops
 * excluding them, or if the manifest asks for more than the INTERNET permission, allows cleartext
 * traffic or allows backup (a backup would copy the encrypted key without its Keystore key).
 */
class ProjectSecretsGuardTest {

    private val textExtensions = setOf("kt", "java", "xml", "kts", "gradle", "properties", "json", "txt", "pro", "yml", "yaml")

    @Test
    fun theProjectLayoutWasFound() {
        assertTrue(File(appDir(), "build.gradle.kts").isFile)
        assertTrue(File(rootDir(), "settings.gradle.kts").isFile)
        assertTrue(File(appDir(), "src/main/AndroidManifest.xml").isFile)
        val names = scannedFiles().map { it.name }.toSet()
        assertTrue(names.toString(), "MainActivity.kt" in names)
        assertTrue(names.toString(), "build.gradle.kts" in names)
    }

    @Test
    fun noGoogleApiKeyIsWrittenInTheMainSourcesOrBuildFiles() {
        val googleKey = Regex("AIza[0-9A-Za-z_\\-]{35}")
        val hits = scannedFiles().filter { googleKey.containsMatchIn(it.readText()) }.map { it.name }
        assertTrue("a Google API key pattern was found in: $hits", hits.isEmpty())
    }

    @Test
    fun noPrivateKeyBlockIsWrittenInTheMainSourcesOrBuildFiles() {
        val block = Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----")
        val hits = scannedFiles().filter { block.containsMatchIn(it.readText()) }.map { it.name }
        assertTrue("a private key block was found in: $hits", hits.isEmpty())
    }

    @Test
    fun noSigningPasswordIsWrittenInTheGradleFiles() {
        val quoted = Regex("(storePassword|keyPassword)\\s*=\\s*\"[^\"]+\"")
        val bare = Regex("(?m)^\\s*(storePassword|keyPassword)\\s*=\\s*\\S+")
        val hits = scannedFiles()
            .filter { it.extension in setOf("kts", "gradle", "properties") }
            .filter { file ->
                val text = file.readText()
                quoted.containsMatchIn(text) || (file.extension == "properties" && bare.containsMatchIn(text))
            }
            .map { it.name }
        assertTrue("a signing password was found in: $hits", hits.isEmpty())
    }

    @Test
    fun noKeystoreOrCredentialFileIsInTheProject() {
        val skippedDirs = setOf(".git", ".gradle", "build", ".idea")
        val badExtensions = setOf("jks", "keystore", "p12", "pfx")
        val badNames = setOf("keystore.properties", ".env")
        val root = rootDir()
        val found = root.walkTopDown()
            .onEnter { it.name !in skippedDirs }
            .filter { it.isFile }
            .filter { it.extension.lowercase() in badExtensions || it.name in badNames }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .toList()
        assertTrue("signing or credential files are in the project: $found", found.isEmpty())
    }

    @Test
    fun gitignoreKeepsSigningSecretsOutOfGit() {
        val rules = File(rootDir(), ".gitignore").readLines().map { it.trim() }.toSet()
        for (rule in listOf("*.jks", "*.keystore", "*.p12", "*.pfx", "keystore.properties", ".env", "local.properties")) {
            assertTrue("missing .gitignore rule: $rule", rule in rules)
        }
    }

    @Test
    fun theManifestAsksOnlyForTheInternetPermission() {
        val manifest = File(appDir(), "src/main/AndroidManifest.xml").readText()
        val permissions = Regex("<uses-permission[^>]*android:name=\"([^\"]+)\"")
            .findAll(manifest)
            .map { it.groupValues[1] }
            .toSet()
        assertEquals(setOf("android.permission.INTERNET"), permissions)
    }

    @Test
    fun cleartextTrafficAndBackupAreOff() {
        val manifest = File(appDir(), "src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:usesCleartextTraffic=\"false\""))
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
    }

    // ================= helpers =================

    private fun appDir(): File {
        val here = File(".").absoluteFile.normalize()
        return when {
            File(here, "src/main/AndroidManifest.xml").isFile -> here
            File(here, "app/src/main/AndroidManifest.xml").isFile -> File(here, "app")
            else -> error("app module not found from $here")
        }
    }

    private fun rootDir(): File = appDir().parentFile ?: error("project root not found")

    private fun scannedFiles(): List<File> {
        val main = File(appDir(), "src/main").walkTopDown()
            .filter { it.isFile && it.extension in textExtensions }
        val workflows = File(rootDir(), ".github").walkTopDown()
            .filter { it.isFile && it.extension in textExtensions }
        val buildFiles = listOf(
            File(rootDir(), "build.gradle.kts"),
            File(rootDir(), "settings.gradle.kts"),
            File(rootDir(), "gradle.properties"),
            File(appDir(), "build.gradle.kts"),
            File(appDir(), "proguard-rules.pro"),
        ).filter { it.isFile }
        return (main + workflows + buildFiles).toList()
    }
}
