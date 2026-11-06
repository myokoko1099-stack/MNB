// Derived from Hydrogen Browser (MIT), copyright 2022 Liu Zihao, modified for MNB. See THIRD_PARTY_NOTICES.md
package com.mnb.browser.ui.premium

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val shortcutColors = listOf(
    Color(0xFF4285F4),
    Color(0xFFEA4335),
    Color(0xFFFBBC05),
    Color(0xFF34A853),
    Color(0xFFFF6D00),
    Color(0xFF9C27B0),
    Color(0xFF00BCD4),
    Color(0xFF795548),
)

private fun colorForUrl(url: String): Color {
    val index = Math.floorMod(url.hashCode(), shortcutColors.size)
    return shortcutColors[index]
}

private fun firstLetterOf(url: String): String {
    val host = url
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
    return host.firstOrNull()?.uppercase() ?: "?"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NewTabPage(
    onSearch: () -> Unit,
    onNavigate: (String) -> Unit,
    prefs: UiPreferences,
) {
    var shortcuts by remember(prefs) { mutableStateOf(prefs.getShortcuts()) }
    var editingIndex by remember { mutableIntStateOf(-1) }
    var showAddDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClick = onSearch)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Search or enter address",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Favorites",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Long press to edit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(shortcuts, key = { index, item -> "$index:${item.url}" }) { index, shortcut ->
                    NtpTile(
                        label = shortcut.name,
                        badgeColor = colorForUrl(shortcut.url),
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        clickModifier = Modifier.combinedClickable(
                            onClick = { onNavigate(shortcut.url) },
                            onLongClick = { editingIndex = index },
                        ),
                    ) {
                        Text(
                            text = firstLetterOf(shortcut.url),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                item(key = "add-favorite") {
                    NtpTile(
                        label = "Add",
                        badgeColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        clickModifier = Modifier.clickable { showAddDialog = true },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add favorite",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }

    val editing = shortcuts.getOrNull(editingIndex)
    if (editing != null) {
        AddEditShortcutDialog(
            isEdit = true,
            initialName = editing.name,
            initialUrl = editing.url,
            onDismiss = { editingIndex = -1 },
            onSave = { name, url ->
                val updated = shortcuts.toMutableList()
                updated[editingIndex] = Shortcut(name, url)
                shortcuts = updated
                prefs.saveShortcuts(updated)
                editingIndex = -1
            },
            onDelete = {
                val updated = shortcuts.toMutableList()
                updated.removeAt(editingIndex)
                shortcuts = updated
                prefs.saveShortcuts(updated)
                editingIndex = -1
            },
        )
    }

    if (showAddDialog) {
        AddEditShortcutDialog(
            isEdit = false,
            initialName = "",
            initialUrl = "",
            onDismiss = { showAddDialog = false },
            onSave = { name, url ->
                val updated = shortcuts + Shortcut(name, url)
                shortcuts = updated
                prefs.saveShortcuts(updated)
                showAddDialog = false
            },
            onDelete = null,
        )
    }
}

@Composable
private fun NtpTile(
    label: String,
    badgeColor: Color,
    labelColor: Color,
    clickModifier: Modifier,
    badge: @Composable () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(clickModifier)
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(badgeColor),
            contentAlignment = Alignment.Center,
            content = { badge() },
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AddEditShortcutDialog(
    isEdit: Boolean,
    initialName: String,
    initialUrl: String,
    onDismiss: () -> Unit,
    onSave: (name: String, url: String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit favorite" else "Add favorite") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val safeName = name.trim().ifBlank { url.trim() }
                    if (url.isNotBlank()) onSave(safeName, url.trim())
                },
                enabled = url.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            if (isEdit && onDelete != null) {
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
