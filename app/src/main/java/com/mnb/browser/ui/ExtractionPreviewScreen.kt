package com.mnb.browser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnb.browser.extraction.ExtractedContent
import com.mnb.browser.extraction.ExtractedParagraph
import com.mnb.browser.extraction.ExtractionState

/** Full-screen preview of extracted text. Shows loading, success, empty and error states. */
@Composable
fun ExtractionPreviewScreen(
    state: ExtractionState,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onRead: (ExtractedContent) -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close preview") }
                    Text("Extracted text", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    if (state is ExtractionState.Success) {
                        TextButton(onClick = { onRead(state.content) }) { Text("Read") }
                    }
                }
            }
            when (state) {
                ExtractionState.Idle -> Unit
                ExtractionState.Loading -> CenteredMessage {
                    CircularProgressIndicator()
                    Text("Extracting text…", modifier = Modifier.padding(top = 16.dp))
                }
                is ExtractionState.Success -> ContentList(state.content)
                is ExtractionState.Empty -> CenteredMessage {
                    Icon(Icons.Default.Info, null, modifier = Modifier.size(48.dp))
                    Text(
                        "No readable text found",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Text(
                        "This page has no main text to extract. It may still be loading, or the content may require signing in.",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    ActionRow(onClose, onRetry)
                }
                is ExtractionState.Error -> CenteredMessage {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                    Text(
                        "Extraction failed",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Text(state.message, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 12.dp))
                    ActionRow(onClose, onRetry)
                }
            }
        }
    }
}

@Composable
private fun ActionRow(onClose: () -> Unit, onRetry: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onClose) { Text("Close") }
        Button(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}

@Composable
private fun ContentList(content: ExtractedContent) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (content.pageTitle.isNotEmpty()) {
                    Text(content.pageTitle, style = MaterialTheme.typography.labelLarge)
                }
                if (!content.chapterTitle.isNullOrEmpty()) {
                    Text(content.chapterTitle, style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    content.url,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${content.paragraphs.size} paragraphs", style = MaterialTheme.typography.bodySmall)
            }
        }
        items(content.paragraphs) { paragraph -> ParagraphView(paragraph) }
    }
}

@Composable
private fun ParagraphView(paragraph: ExtractedParagraph) {
    if (paragraph.isHeading) {
        val style = when (paragraph.headingLevel) {
            1 -> MaterialTheme.typography.headlineSmall
            2 -> MaterialTheme.typography.titleLarge
            else -> MaterialTheme.typography.titleMedium
        }
        Text(paragraph.text, style = style)
    } else {
        // Generous line height so Myanmar script stacks are not clipped.
        Text(paragraph.text, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 30.sp))
    }
}
