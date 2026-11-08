package com.mnb.browser.ui.library

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mnb.browser.library.HistoryGrouping
import com.mnb.browser.library.HistoryText
import com.mnb.browser.library.LibraryEntry
import com.mnb.browser.library.LibraryText
import com.mnb.browser.library.LoadState
import com.mnb.browser.ui.premium.UrlUtils

/** Full-screen list of saved bookmarks. Pure UI: the caller decides what happens on every action. */
@Composable
fun BookmarksScreen(
    entries: List<LibraryEntry>,
    state: LoadState,
    onOpen: (url: String) -> Unit,
    onDelete: (id: Long) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LibraryScaffold(title = LibraryText.BOOKMARKS_TITLE, onClose = onClose, modifier = modifier) {
        LibraryContent(
            entries = entries,
            state = state,
            emptyIcon = Icons.Default.Star,
            emptyTitle = LibraryText.BOOKMARKS_EMPTY_TITLE,
            emptyBody = LibraryText.BOOKMARKS_EMPTY_BODY,
            showTime = false,
            deleteDescription = LibraryText.BOOKMARK_DELETE,
            onOpen = onOpen,
            onDelete = onDelete,
            onRetry = onRetry,
        )
    }
}

/** Full-screen browsing history, newest first, with "Clear all" behind a confirmation dialog. */
@Composable
fun HistoryScreen(
    entries: List<LibraryEntry>,
    state: LoadState,
    onOpen: (url: String) -> Unit,
    onDelete: (id: Long) -> Unit,
    onClearAll: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmClear by remember { mutableStateOf(false) }
    LibraryScaffold(
        title = LibraryText.HISTORY_TITLE,
        onClose = onClose,
        modifier = modifier,
        actions = {
            TextButton(onClick = { confirmClear = true }, enabled = state == LoadState.Ready && entries.isNotEmpty()) {
                Text(LibraryText.CLEAR_ALL)
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            HistorySearchField(query = query, onQueryChange = onQueryChange)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                HistoryContent(
                    entries = entries,
                    state = state,
                    query = query,
                    onOpen = onOpen,
                    onDelete = onDelete,
                    onRetry = onRetry,
                )
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(LibraryText.CLEAR_CONFIRM_TITLE) },
            text = { Text(LibraryText.CLEAR_CONFIRM_BODY) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearAll()
                }) { Text(LibraryText.CLEAR_CONFIRM_ACTION) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(LibraryText.CANCEL) }
            },
        )
    }
}

@Composable
private fun LibraryScaffold(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                actions()
                TextButton(onClick = onClose) { Text(LibraryText.DONE) }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
    }
}

/** Search box of the History screen. */
@Composable
private fun HistorySearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = { onQueryChange(it.take(HistoryText.MAX_QUERY_LENGTH)) },
        singleLine = true,
        placeholder = { Text(HistoryText.SEARCH_HINT) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = HistoryText.SEARCH_CLEAR)
                }
            }
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

/** History list: the states of [LibraryContent], with the visits grouped by date (also while searching). */
@Composable
private fun HistoryContent(
    entries: List<LibraryEntry>,
    state: LoadState,
    query: String,
    onOpen: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onRetry: () -> Unit,
) {
    Crossfade(targetState = state, modifier = Modifier.fillMaxSize(), label = "historyState") { shown ->
        when (shown) {
            LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            LoadState.Error -> CenteredMessage(
                icon = Icons.Default.Warning,
                title = LibraryText.LOAD_ERROR_TITLE,
                body = LibraryText.LOAD_ERROR_BODY,
                isError = true,
            ) {
                Button(onClick = onRetry) { Text(LibraryText.RETRY) }
            }
            LoadState.Ready -> if (entries.isEmpty()) {
                if (query.isBlank()) {
                    CenteredMessage(
                        icon = Icons.Default.DateRange,
                        title = LibraryText.HISTORY_EMPTY_TITLE,
                        body = LibraryText.HISTORY_EMPTY_BODY,
                        isError = false,
                    )
                } else {
                    CenteredMessage(
                        icon = Icons.Default.Search,
                        title = HistoryText.SEARCH_EMPTY_TITLE,
                        body = HistoryText.SEARCH_EMPTY_BODY,
                        isError = false,
                    )
                }
            } else {
                // Taken once per list so the groups and "5 min ago" do not flicker while scrolling.
                val now = remember(entries) { System.currentTimeMillis() }
                val sections = remember(entries, now) { HistoryGrouping.group(entries, now) }
                LazyColumn(Modifier.fillMaxSize()) {
                    for (section in sections) {
                        item(key = "header-${section.group.name}") {
                            Text(
                                section.group.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
                            )
                        }
                        items(section.entries, key = { "entry-${it.id}" }) { entry ->
                            EntryRow(
                                entry = entry,
                                timeText = LibraryText.timeAgo(now, entry.timeMillis),
                                deleteDescription = LibraryText.HISTORY_DELETE,
                                onOpen = { onOpen(entry.url) },
                                onDelete = { onDelete(entry.id) },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Loading, error, empty and list states, with a short cross-fade between them. */
@Composable
private fun LibraryContent(
    entries: List<LibraryEntry>,
    state: LoadState,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptyBody: String,
    showTime: Boolean,
    deleteDescription: String,
    onOpen: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onRetry: () -> Unit,
) {
    Crossfade(targetState = state, modifier = Modifier.fillMaxSize(), label = "libraryState") { shown ->
        when (shown) {
            LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            LoadState.Error -> CenteredMessage(
                icon = Icons.Default.Warning,
                title = LibraryText.LOAD_ERROR_TITLE,
                body = LibraryText.LOAD_ERROR_BODY,
                isError = true,
            ) {
                Button(onClick = onRetry) { Text(LibraryText.RETRY) }
            }
            LoadState.Ready -> if (entries.isEmpty()) {
                CenteredMessage(icon = emptyIcon, title = emptyTitle, body = emptyBody, isError = false)
            } else {
                // Taken once per list so "5 min ago" does not flicker while scrolling.
                val now = remember(entries) { System.currentTimeMillis() }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(entries, key = { it.id }) { entry ->
                        EntryRow(
                            entry = entry,
                            timeText = if (showTime) LibraryText.timeAgo(now, entry.timeMillis) else null,
                            deleteDescription = deleteDescription,
                            onOpen = { onOpen(entry.url) },
                            onDelete = { onDelete(entry.id) },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: LibraryEntry,
    timeText: String?,
    deleteDescription: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                entry.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                UrlUtils.simplifyForDisplay(entry.url),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (timeText != null) {
                Text(
                    timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = deleteDescription, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun CenteredMessage(
    icon: ImageVector,
    title: String,
    body: String,
    isError: Boolean,
    action: @Composable () -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        action()
    }
}
