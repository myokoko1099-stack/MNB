// Derived from Hydrogen Browser (MIT), copyright 2022 Liu Zihao, modified for MNB. See THIRD_PARTY_NOTICES.md
package com.mnb.browser.ui.premium

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mnb.browser.tabs.TabDisplay

@Composable
fun BottomBar(
    currentUrl: String,
    canGoBack: Boolean,
    canGoForward: Boolean,
    extractEnabled: Boolean,
    onAddressClick: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onRefresh: () -> Unit,
    onExtract: () -> Unit,
    onSettings: () -> Unit,
    tabCount: Int = 1,
    onTabs: () -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                AddressPill(currentUrl = currentUrl, onClick = onAddressClick, onRefresh = onRefresh)
            }
            TabCountButton(count = tabCount, onClick = onTabs)
            IconButton(onClick = onExtract, enabled = extractEnabled) {
                Icon(Icons.Default.List, contentDescription = "Extract", modifier = Modifier.size(22.dp))
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options", modifier = Modifier.size(22.dp))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Top,
                    ) {
                        MenuAction(Icons.AutoMirrored.Filled.ArrowBack, "Back", canGoBack) {
                            menuOpen = false
                            onBack()
                        }
                        MenuAction(Icons.AutoMirrored.Filled.ArrowForward, "Forward", canGoForward) {
                            menuOpen = false
                            onForward()
                        }
                        MenuAction(Icons.Default.Home, "Home", true) {
                            menuOpen = false
                            onHome()
                        }
                        MenuAction(Icons.Default.Refresh, "Refresh", true) {
                            menuOpen = false
                            onRefresh()
                        }
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        onClick = { menuOpen = false; onSettings() },
                    )
                }
            }
        }
    }
}

/** Icon button (48dp touch target) with a small visible label; the label is hidden from screen readers. */
@Composable
private fun MenuAction(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.widthIn(min = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/** Square badge with the number of open tabs; opens the tab switcher. */
@Composable
private fun TabCountButton(count: Int, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = BottomMenuText.tabsDescription(count) },
    ) {
        Box(
            Modifier
                .size(24.dp)
                .border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(TabDisplay.countBadge(count), style = MaterialTheme.typography.labelSmall)
        }
    }
}
