package com.mnb.browser.ui.settings

import com.mnb.browser.translation.SourceLanguage
import com.mnb.browser.translation.TargetLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsScreenTextTest {

    @Test
    fun sections_areTheConsolidatedRequiredOnesInOrder() {
        assertEquals(
            listOf(
                "General Settings",
                "Translation Settings",
                "Translation Provider",
                "Gemini Configuration",
                "Reader Preferences",
                "Browser Preferences",
                "Translation Behavior",
                "About MNB",
            ),
            SettingsSection.values().map { it.title },
        )
    }

    @Test
    fun sectionTitles_areDistinctAndNotBlank() {
        val titles = SettingsSection.values().map { it.title }
        assertTrue(titles.all { it.isNotBlank() })
        assertEquals(titles.size, titles.toSet().size)
    }

    @Test
    fun sourceLanguagesSummary_listsEverySupportedLanguageInOrder() {
        assertEquals("English, Chinese, Korean, Japanese", SettingsScreenText.sourceLanguagesSummary())
        SourceLanguage.values().forEach {
            assertTrue(SettingsScreenText.sourceLanguageLabel(it).isNotBlank())
        }
    }

    @Test
    fun targetLanguagesSummary_listsMyanmar() {
        assertEquals("Myanmar", SettingsScreenText.targetLanguagesSummary())
        TargetLanguage.values().forEach {
            assertTrue(SettingsScreenText.targetLanguageLabel(it).isNotBlank())
        }
    }

    @Test
    fun placeholders_doNotClaimToBeWorking() {
        val texts = listOf(SettingsScreenText.GEMINI_PLACEHOLDER, SettingsScreenText.BEHAVIOR_PLACEHOLDER)
        assertTrue(texts.all { it.contains("later update") })
        assertEquals("Not available yet", SettingsScreenText.PLACEHOLDER_BADGE)
        // The foundation screen must not show any key status or key text.
        assertFalse(texts.any { it.contains("saved", ignoreCase = true) })
    }

    @Test
    fun providerText_describesDirectGeminiWithoutAGateway() {
        assertEquals("Gemini (direct)", SettingsScreenText.PROVIDER_NAME)
        assertFalse(SettingsScreenText.PROVIDER_NOTE.contains("gateway", ignoreCase = true))
    }

    @Test
    fun versionLabel_showsVersionOrUnknown() {
        assertEquals("Version 1.2.3", SettingsScreenText.versionLabel("1.2.3"))
        assertEquals("Version 1.0", SettingsScreenText.versionLabel("  1.0 "))
        assertEquals("Version unknown", SettingsScreenText.versionLabel(null))
        assertEquals("Version unknown", SettingsScreenText.versionLabel("   "))
    }

    @Test
    fun licenseNotice_pointsToThirdPartyNotices() {
        assertTrue(SettingsScreenText.LICENSE_NOTICE.contains("THIRD_PARTY_NOTICES.md"))
    }
}
