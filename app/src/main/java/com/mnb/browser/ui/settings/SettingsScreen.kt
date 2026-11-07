package com.mnb.browser.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mnb.browser.ui.premium.SettingsText
import com.mnb.browser.ui.premium.ThemeMode

/**
 * Settings screen foundation (Phase 06-C1).
 *
 * Stateless: the caller owns the theme mode and the back action. It is not connected to app
 * navigation yet (Phase 06-C2), and it does not contain the Gemini API key UI (Phase 06-C3 / C4;
 * the existing key section of the current settings panel is untouched).
 *
 * Only General Settings (theme) changes anything. Translation Settings and Translation Provider
 * are read-only information, and Gemini Configuration and Translation Behavior are placeholders.
 */
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    versionName: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        SettingsTopBar(onBack = onBack)

        // Content is centered with a maximum width so it stays readable on tablets and in landscape.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Same order as SettingsSection.values().
                GeneralSection(themeMode, onThemeModeChange)
                TranslationSettingsSection()
                TranslationProviderSection()
                GeminiConfigurationSection()
                TranslationBehaviorSection()
                AboutSection(versionName)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = SettingsScreenText.BACK_DESCRIPTION,
            )
        }
        Text(
            text = SettingsScreenText.SCREEN_TITLE,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun GeneralSection(themeMode: ThemeMode, onThemeModeChange: (ThemeMode) -> Unit) {
    SettingsSectionCard(SettingsSection.General.title) {
        Text(
            text = SettingsScreenText.APPEARANCE_LABEL,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.selectableGroup()) {
            SettingsText.themeChoices.forEach { choice ->
                ThemeRadioRow(
                    choice = choice,
                    selected = themeMode == choice.mode,
                    onSelect = { onThemeModeChange(choice.mode) },
                )
            }
        }
    }
}

@Composable
private fun ThemeRadioRow(choice: SettingsText.ThemeChoice, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
    ) {
        // The whole row is the single touch target; the radio itself is not clickable.
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(choice.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                choice.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TranslationSettingsSection() {
    SettingsSectionCard(SettingsSection.TranslationSettings.title) {
        SettingsInfoRow(
            label = SettingsScreenText.SOURCE_LANGUAGES_LABEL,
            value = SettingsScreenText.sourceLanguagesSummary(),
        )
        SettingsInfoRow(
            label = SettingsScreenText.TARGET_LANGUAGE_LABEL,
            value = SettingsScreenText.targetLanguagesSummary(),
        )
        SettingsNote(SettingsScreenText.LANGUAGES_FIXED_NOTE)
    }
}

@Composable
private fun TranslationProviderSection() {
    SettingsSectionCard(SettingsSection.TranslationProvider.title) {
        SettingsInfoRow(
            label = SettingsScreenText.PROVIDER_LABEL,
            value = SettingsScreenText.PROVIDER_NAME,
        )
        SettingsNote(SettingsScreenText.PROVIDER_NOTE)
    }
}

@Composable
private fun GeminiConfigurationSection() {
    SettingsSectionCard(SettingsSection.GeminiConfiguration.title) {
        SettingsPlaceholder(
            badge = SettingsScreenText.PLACEHOLDER_BADGE,
            text = SettingsScreenText.GEMINI_PLACEHOLDER,
        )
    }
}

@Composable
private fun TranslationBehaviorSection() {
    SettingsSectionCard(SettingsSection.TranslationBehavior.title) {
        SettingsPlaceholder(
            badge = SettingsScreenText.PLACEHOLDER_BADGE,
            text = SettingsScreenText.BEHAVIOR_PLACEHOLDER,
        )
    }
}

@Composable
private fun AboutSection(versionName: String?) {
    SettingsSectionCard(SettingsSection.About.title) {
        Text(
            text = SettingsScreenText.APP_NAME,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = SettingsScreenText.versionLabel(versionName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsNote(SettingsScreenText.LICENSE_NOTICE)
    }
}
