package com.mnb.browser.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow

/** Text of the long-press link dialog (pure, unit-testable). */
object LinkText {
    const val TITLE = "Open link"
    const val OPEN_IN_NEW_TAB = "Open in new tab"
    const val CANCEL = "Cancel"
    const val TAB_LIMIT = "Tab limit reached. Close a tab to open this link."
}

/**
 * Shown after a long press on a link. The caller has already checked the address with
 * `UrlPolicy.linkTarget`, and `TabManager.newTab` checks it and the tab limit again.
 */
@Composable
fun LinkDialog(
    link: String,
    canOpenNewTab: Boolean,
    onOpenInNewTab: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(LinkText.TITLE) },
        text = {
            Text(
                text = if (canOpenNewTab) link else LinkText.TAB_LIMIT + "\n\n" + link,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenInNewTab, enabled = canOpenNewTab) { Text(LinkText.OPEN_IN_NEW_TAB) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(LinkText.CANCEL) } },
    )
}
