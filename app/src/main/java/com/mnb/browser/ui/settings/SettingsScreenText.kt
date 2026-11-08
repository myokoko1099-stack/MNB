package com.mnb.browser.ui.settings

import com.mnb.browser.translation.SourceLanguage
import com.mnb.browser.translation.TargetLanguage

/**
 * The sections of the Settings screen, in display order (Phase 06-C1).
 * Declaration order is the order on screen.
 */
enum class SettingsSection(val title: String) {
    General("General Settings"),
    TranslationSettings("Translation Settings"),
    TranslationProvider("Translation Provider"),
    GeminiConfiguration("Gemini Configuration"),
    ReaderPreferences("Reader Preferences"),
    BrowserPreferences("Browser Preferences"),
    TranslationBehavior("Translation Behavior"),
    About("About MNB"),
}

/** Text shown on the Settings screen foundation (pure, unit-testable). */
object SettingsScreenText {
    const val SCREEN_TITLE = "Settings"
    const val BACK_DESCRIPTION = "Back"

    // General Settings
    const val APPEARANCE_LABEL = "Appearance"

    // Translation Settings (read-only: the language pairs are fixed in this version)
    const val SOURCE_LANGUAGES_LABEL = "Translate from"
    const val TARGET_LANGUAGE_LABEL = "Translate into"
    const val LANGUAGES_FIXED_NOTE = "These languages are fixed in this version."

    // Translation Provider (read-only)
    const val PROVIDER_LABEL = "Provider"
    const val PROVIDER_NAME = "Gemini (direct)"
    const val PROVIDER_NOTE =
        "Translation requests go straight from this device to Gemini, using your own API key."

    // Placeholders: no controls, no fake status
    const val PLACEHOLDER_BADGE = "Not available yet"
    const val GEMINI_PLACEHOLDER =
        "Gemini API key and model options will be managed here in a later update."
    const val GEMINI_UNAVAILABLE = "Gemini key settings are unavailable in this host."
    const val BROWSER_PREFS_NOTE =
        "Browser shortcut data remains in UiPreferences. Safe Mode only changes selected shortcut groups when that feature is implemented."
    const val SAFE_MODE_UNAVAILABLE =
        "Safe Mode and user-defined shortcut groups are not implemented yet. No content-unlocking behavior is provided."
    const val BEHAVIOR_PLACEHOLDER =
        "Options for how chapters are translated will appear here in a later update."

    // About MNB
    const val APP_NAME = "MNB"
    const val LICENSE_NOTICE =
        "Parts of this interface are derived from Hydrogen Browser " +
            "(MIT License, copyright 2022 Liu Zihao). " +
            "See THIRD_PARTY_NOTICES.md in the source repository."

    fun sourceLanguageLabel(language: SourceLanguage): String = when (language) {
        SourceLanguage.ENGLISH -> "English"
        SourceLanguage.CHINESE -> "Chinese"
        SourceLanguage.KOREAN -> "Korean"
        SourceLanguage.JAPANESE -> "Japanese"
    }

    fun targetLanguageLabel(language: TargetLanguage): String = when (language) {
        TargetLanguage.MYANMAR -> "Myanmar"
    }

    /** "English, Chinese, Korean, Japanese" - every supported source language, in enum order. */
    fun sourceLanguagesSummary(): String =
        SourceLanguage.values().joinToString(", ") { sourceLanguageLabel(it) }

    /** "Myanmar" - every supported target language, in enum order. */
    fun targetLanguagesSummary(): String =
        TargetLanguage.values().joinToString(", ") { targetLanguageLabel(it) }

    fun versionLabel(versionName: String?): String {
        val trimmed = versionName?.trim().orEmpty()
        return if (trimmed.isEmpty()) "Version unknown" else "Version $trimmed"
    }
}
