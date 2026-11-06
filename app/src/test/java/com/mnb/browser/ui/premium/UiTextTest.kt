package com.mnb.browser.ui.premium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiTextTest {

    @Test
    fun themeChoices_coverEveryModeOnceInOrder() {
        assertEquals(
            listOf(ThemeMode.System, ThemeMode.Light, ThemeMode.Dark),
            SettingsText.themeChoices.map { it.mode },
        )
        assertEquals(ThemeMode.values().toSet(), SettingsText.themeChoices.map { it.mode }.toSet())
    }

    @Test
    fun themeChoices_haveDistinctNonBlankTexts() {
        val labels = SettingsText.themeChoices.map { it.label }
        assertTrue(labels.all { it.isNotBlank() })
        assertEquals(labels.size, labels.toSet().size)
        assertTrue(SettingsText.themeChoices.all { it.description.isNotBlank() })
    }

    @Test
    fun versionLabel_showsVersionOrUnknown() {
        assertEquals("Version 0.2.0-phase02", SettingsText.versionLabel("0.2.0-phase02"))
        assertEquals("Version 1.0", SettingsText.versionLabel("  1.0 "))
        assertEquals("Version unknown", SettingsText.versionLabel(null))
        assertEquals("Version unknown", SettingsText.versionLabel("   "))
    }

    @Test
    fun aboutTitle_namesTheApp() {
        assertEquals("About MNB", SettingsText.ABOUT_TITLE)
        assertTrue(SettingsText.LICENSE_NOTICE.contains("THIRD_PARTY_NOTICES.md"))
    }

    @Test
    fun tabsDescription_keepsExistingWordingAndIgnoresNegatives() {
        assertEquals("Tabs: 1 open", BottomMenuText.tabsDescription(1))
        assertEquals("Tabs: 12 open", BottomMenuText.tabsDescription(12))
        assertEquals("Tabs: 0 open", BottomMenuText.tabsDescription(-3))
    }
}
