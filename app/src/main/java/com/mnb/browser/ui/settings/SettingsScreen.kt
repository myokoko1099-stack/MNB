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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mnb.browser.reader.ReaderSettings
import com.mnb.browser.reader.ReaderSettingsRepository
import com.mnb.browser.reader.ReaderTheme
import com.mnb.browser.settings.GeminiKeySettingsController
import com.mnb.browser.ui.GeminiApiKeySection
import com.mnb.browser.ui.premium.SettingsText
import com.mnb.browser.ui.premium.UiPreferences
import kotlin.math.roundToInt

/** The single settings entry point used by the browser flow. */
@Composable
fun SettingsScreen(
    prefs: UiPreferences,
    readerSettingsRepository: ReaderSettingsRepository,
    geminiKeySettings: GeminiKeySettingsController?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    versionName: String? = null,
) {
    var readerSettings by remember(readerSettingsRepository) {
        mutableStateOf(readerSettingsRepository.load())
    }
    fun updateReaderSettings(newSettings: ReaderSettings) {
        val clean = newSettings.sanitized()
        readerSettings = clean
        readerSettingsRepository.save(clean)
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        SettingsTopBar(onBack)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                GeneralSection(prefs)
                TranslationSettingsSection()
                TranslationProviderSection()
                GeminiConfigurationSection(geminiKeySettings)
                ReaderSettingsSection(readerSettings, ::updateReaderSettings)
                BrowserSettingsSection(prefs)
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
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = SettingsScreenText.BACK_DESCRIPTION,
            )
        }
        Text(
            SettingsScreenText.SCREEN_TITLE,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun GeneralSection(prefs: UiPreferences) {
    SettingsSectionCard(SettingsSection.General.title) {
        Text(
            SettingsScreenText.APPEARANCE_LABEL,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.selectableGroup()) {
            SettingsText.themeChoices.forEach { choice ->
                ThemeRadioRow(choice, prefs.themeMode == choice.mode) { prefs.updateThemeMode(choice.mode) }
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
        SettingsInfoRow(SettingsScreenText.SOURCE_LANGUAGES_LABEL, SettingsScreenText.sourceLanguagesSummary())
        SettingsInfoRow(SettingsScreenText.TARGET_LANGUAGE_LABEL, SettingsScreenText.targetLanguagesSummary())
        SettingsNote(SettingsScreenText.LANGUAGES_FIXED_NOTE)
    }
}

@Composable
private fun TranslationProviderSection() {
    SettingsSectionCard(SettingsSection.TranslationProvider.title) {
        SettingsInfoRow(SettingsScreenText.PROVIDER_LABEL, SettingsScreenText.PROVIDER_NAME)
        SettingsNote(SettingsScreenText.PROVIDER_NOTE)
    }
}

@Composable
private fun GeminiConfigurationSection(controller: GeminiKeySettingsController?) {
    SettingsSectionCard(SettingsSection.GeminiConfiguration.title) {
        if (controller == null) {
            SettingsPlaceholder(
                SettingsScreenText.PLACEHOLDER_BADGE,
                SettingsScreenText.GEMINI_UNAVAILABLE,
            )
        } else {
            GeminiApiKeySection(controller)
        }
    }
}

@Composable
private fun ReaderSettingsSection(settings: ReaderSettings, onChange: (ReaderSettings) -> Unit) {
    SettingsSectionCard(SettingsSection.ReaderPreferences.title) {
        ReaderSlider(
            "Font size",
            settings.fontSizeSp,
            ReaderSettings.FONT_SIZE_RANGE,
            "sp",
        ) { onChange(settings.copy(fontSizeSp = it)) }
        ReaderSlider(
            "Margin",
            settings.marginDp,
            ReaderSettings.MARGIN_RANGE,
            "dp",
        ) { onChange(settings.copy(marginDp = it)) }
        ReaderSlider(
            "Paragraph spacing",
            settings.paragraphSpacingDp,
            ReaderSettings.PARAGRAPH_SPACING_RANGE,
            "dp",
        ) { onChange(settings.copy(paragraphSpacingDp = it)) }
        Text("Reader theme", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReaderThemeChip("System", ReaderTheme.SYSTEM, settings, onChange)
            ReaderThemeChip("Light", ReaderTheme.LIGHT, settings, onChange)
            ReaderThemeChip("Dark", ReaderTheme.DARK, settings, onChange)
        }
    }
}

@Composable
private fun ReaderThemeChip(
    label: String,
    theme: ReaderTheme,
    settings: ReaderSettings,
    onChange: (ReaderSettings) -> Unit,
) {
    FilterChip(
        selected = settings.theme == theme,
        onClick = { onChange(settings.copy(theme = theme)) },
        label = { Text(label) },
    )
}

@Composable
private fun ReaderSlider(
    label: String,
    value: Int,
    range: IntRange,
    unit: String,
    onChange: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("$value $unit", style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
        )
    }
}

@Composable
private fun BrowserSettingsSection(prefs: UiPreferences) {
    SettingsSectionCard(SettingsSection.BrowserPreferences.title) {
        SettingsInfoRow(
            "Novel shortcuts",
            "${prefs.getShortcuts().size} saved",
        )
        SettingsNote(SettingsScreenText.BROWSER_PREFS_NOTE)
        SettingsPlaceholder(
            SettingsScreenText.PLACEHOLDER_BADGE,
            SettingsScreenText.SAFE_MODE_UNAVAILABLE,
        )
    }
}

@Composable
private fun TranslationBehaviorSection() {
    SettingsSectionCard(SettingsSection.TranslationBehavior.title) {
        SettingsPlaceholder(
            SettingsScreenText.PLACEHOLDER_BADGE,
            SettingsScreenText.BEHAVIOR_PLACEHOLDER,
        )
    }
}

@Composable
private fun AboutSection(versionName: String?) {
    SettingsSectionCard(SettingsSection.About.title) {
        Text(SettingsScreenText.APP_NAME, style = MaterialTheme.typography.titleMedium)
        Text(
            SettingsScreenText.versionLabel(versionName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsNote(SettingsScreenText.LICENSE_NOTICE)
    }
}
