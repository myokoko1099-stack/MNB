// Derived from Hydrogen Browser (MIT), copyright 2022 Liu Zihao, modified for MNB. See THIRD_PARTY_NOTICES.md
package com.mnb.browser.ui.premium

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Tile colours for favorites that are not bundled sites (all dark enough for white text). */
private val fallbackColors = listOf(
    Color(0xFF3F51B5),
    Color(0xFFB3261E),
    Color(0xFF2E7D32),
    Color(0xFFE65100),
    Color(0xFF6A1B9A),
    Color(0xFF00695C),
    Color(0xFF5D4037),
    Color(0xFF37474F),
)

private fun colorForUrl(url: String): Color {
    val index = Math.floorMod(url.hashCode(), fallbackColors.size)
    return fallbackColors[index]
}

private fun firstLetterOf(url: String): String {
    val host = url
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
    return host.firstOrNull()?.uppercase() ?: "?"
}

private val TILE_TARGET_WIDTH: Dp = 84.dp
private val CONTENT_MAX_WIDTH: Dp = 560.dp

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

    // Gentle fade + rise when the page first appears.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val enterProgress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "ntp-enter",
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.TopCenter,
    ) {
        // 3 to 5 tiles per row depending on the screen width (small phones 3, wide screens 5).
        val columns = (maxWidth / TILE_TARGET_WIDTH).toInt().coerceIn(3, 5)
        val topSpace = (maxHeight * 0.14f).coerceAtLeast(24.dp)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = CONTENT_MAX_WIDTH)
                .fillMaxSize()
                .graphicsLayer {
                    alpha = enterProgress
                    translationY = (1f - enterProgress) * 24.dp.toPx()
                }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(topSpace))

            Text(
                text = "MNB",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(20.dp))

            SearchEntry(onClick = onSearch)

            Spacer(Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Novel sites",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Long press to edit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))

            // Shortcuts + the "Add" tile, laid out as a grid of equal-width cells.
            val tileCount = shortcuts.size + 1
            (0 until tileCount).chunked(columns).forEach { rowIndexes ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    rowIndexes.forEach { index ->
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            if (index < shortcuts.size) {
                                val shortcut = shortcuts[index]
                                SiteTile(
                                    shortcut = shortcut,
                                    onClick = { onNavigate(shortcut.url) },
                                    onLongClick = { editingIndex = index },
                                )
                            } else {
                                AddTile(onClick = { showAddDialog = true })
                            }
                        }
                    }
                    repeat(columns - rowIndexes.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(24.dp))
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

/** The big rounded search field. Tapping it opens the address overlay. */
@Composable
private fun SearchEntry(onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = AddressInput.SEARCH_HINT,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SiteTile(
    shortcut: Shortcut,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val bundled = DefaultShortcuts.styleFor(shortcut.url)
    val badgeColor = bundled?.let { Color(it.badgeColor) } ?: colorForUrl(shortcut.url)
    val monogram = bundled?.monogram ?: firstLetterOf(shortcut.url)

    NtpTile(
        label = shortcut.name,
        badgeColor = badgeColor,
        clickModifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
        ),
    ) {
        Text(
            text = monogram,
            color = Color.White,
            fontSize = if (monogram.length > 1) 18.sp else 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    NtpTile(
        label = "Add",
        badgeColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        clickModifier = Modifier.clickable(onClick = onClick),
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add favorite",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun NtpTile(
    label: String,
    badgeColor: Color,
    clickModifier: Modifier,
    badge: @Composable () -> Unit,
) {
    val badgeShape = RoundedCornerShape(18.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(clickModifier)
            .padding(vertical = 8.dp, horizontal = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(elevation = 2.dp, shape = badgeShape)
                .clip(badgeShape)
                .background(badgeColor),
            contentAlignment = Alignment.Center,
            content = { badge() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            minLines = 2,
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
