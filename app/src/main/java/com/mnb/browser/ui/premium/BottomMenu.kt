// Derived from Hydrogen Browser (MIT), copyright 2022 Liu Zihao, modified for MNB. See THIRD_PARTY_NOTICES.md
package com.mnb.browser.ui.premium

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
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
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { menuOpen = false; onBack() }, enabled = canGoBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", modifier = Modifier.size(22.dp))
                        }
                        IconButton(onClick = { menuOpen = false; onForward() }, enabled = canGoForward) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "Forward", modifier = Modifier.size(22.dp))
                        }
                        IconButton(onClick = { menuOpen = false; onHome() }) {
                            Icon(Icons.Default.Home, contentDescription = "Home", modifier = Modifier.size(22.dp))
                        }
                        IconButton(onClick = { menuOpen = false; onRefresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(22.dp))
                        }
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { androidx.compose.material3.Text("Settings") },
                        onClick = { menuOpen = false; onSettings() },
                    )
                }
            }
        }
    }
}

/** Square badge with the number of open tabs; opens the tab switcher. */
@Composable
private fun TabCountButton(count: Int, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = "Tabs: $count open" },
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
