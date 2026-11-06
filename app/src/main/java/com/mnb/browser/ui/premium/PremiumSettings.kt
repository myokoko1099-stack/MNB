package com.mnb.browser.ui.premium

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mnb.browser.ui.GeminiApiKeySection
import com.mnb.browser.ui.LocalGeminiKeySettings

@Composable
fun PremiumSettings(prefs: UiPreferences, onClose: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember(context) { readVersionName(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Text(
                text = SettingsText.SETTINGS_TITLE,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SectionTitle(SettingsText.APPEARANCE_TITLE)
            Column(Modifier.selectableGroup()) {
                SettingsText.themeChoices.forEach { choice ->
                    ThemeOption(choice, prefs)
                }
            }

            // Existing Gemini key section (Phase 06-A2); it draws its own leading divider and title.
            LocalGeminiKeySettings.current?.let { GeminiApiKeySection(it) }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            SectionTitle(SettingsText.ABOUT_TITLE)
            Text(
                text = SettingsText.APP_NAME,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = SettingsText.versionLabel(versionName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = SettingsText.LICENSE_NOTICE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    // Same style as the title inside GeminiApiKeySection, so all sections look alike.
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier
            .padding(vertical = 8.dp)
            .semantics { heading() }
    )
}

@Composable
private fun ThemeOption(choice: SettingsText.ThemeChoice, prefs: UiPreferences) {
    val selected = prefs.themeMode == choice.mode
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(
                selected = selected,
                onClick = { prefs.updateThemeMode(choice.mode) },
                role = Role.RadioButton
            )
            .padding(horizontal = 4.dp)
    ) {
        // The whole row is the single touch target; the radio itself is not clickable.
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(choice.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                choice.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Suppress("DEPRECATION")
private fun readVersionName(context: Context): String? = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
} catch (_: Exception) {
    null
}
