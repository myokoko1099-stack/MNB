package com.mnb.browser.ui.premium

/** Text shown on the Settings screen (pure, unit-testable). */
object SettingsText {
    const val SETTINGS_TITLE = "Settings"
    const val APPEARANCE_TITLE = "Appearance"
    const val ABOUT_TITLE = "About MNB"
    const val APP_NAME = "MNB"
    const val LICENSE_NOTICE =
        "Parts of this interface are derived from Hydrogen Browser " +
            "(MIT License, copyright 2022 Liu Zihao). " +
            "See THIRD_PARTY_NOTICES.md in the source repository."

    /** One row of the Appearance section. */
    data class ThemeChoice(val mode: ThemeMode, val label: String, val description: String)

    /** Every [ThemeMode] exactly once, in display order: System, Light, Dark. */
    val themeChoices: List<ThemeChoice> = listOf(
        ThemeChoice(ThemeMode.System, "System default", "Follow your device setting"),
        ThemeChoice(ThemeMode.Light, "Light", "Always use the light theme"),
        ThemeChoice(ThemeMode.Dark, "Dark", "Always use the dark theme"),
    )

    fun versionLabel(versionName: String?): String {
        val trimmed = versionName?.trim().orEmpty()
        return if (trimmed.isEmpty()) "Version unknown" else "Version $trimmed"
    }
}

/** Accessibility text for the bottom bar (pure, unit-testable). */
object BottomMenuText {
    fun tabsDescription(count: Int): String = "Tabs: ${count.coerceAtLeast(0)} open"
}
