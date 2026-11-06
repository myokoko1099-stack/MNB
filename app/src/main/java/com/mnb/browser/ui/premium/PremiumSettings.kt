package com.mnb.browser.ui.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mnb.browser.ui.GeminiApiKeySection
import com.mnb.browser.ui.LocalGeminiKeySettings

@Composable
fun PremiumSettings(prefs: UiPreferences, onClose: () -> Unit) {
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
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SectionTitle("Appearance")
            ThemeOption("System default", ThemeMode.System, prefs)
            ThemeOption("Light", ThemeMode.Light, prefs)
            ThemeOption("Dark", ThemeMode.Dark, prefs)


            LocalGeminiKeySettings.current?.let { GeminiApiKeySection(it) }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            SectionTitle("About")
            Text(
                text = "Parts of this interface are derived from Hydrogen Browser " +
                    "(MIT License, copyright 2022 Liu Zihao). " +
                    "See THIRD_PARTY_NOTICES.md in the source repository.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun ThemeOption(label: String, mode: ThemeMode, prefs: UiPreferences) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { prefs.updateThemeMode(mode) }
            .padding(vertical = 2.dp)
    ) {
        RadioButton(
            selected = prefs.themeMode == mode,
            onClick = { prefs.updateThemeMode(mode) }
        )
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
