package com.mnb.browser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnb.browser.extraction.ExtractedContent
import com.mnb.browser.extraction.ExtractedParagraph
import com.mnb.browser.reader.ReaderPosition
import com.mnb.browser.reader.ReaderPositionRepository

/**
 * Full-screen novel reader. List item 0 is the chapter header; paragraphs follow in their
 * original order, so a saved list index maps straight back to the same paragraph.
 */
@Composable
fun ReaderScreen(
    content: ExtractedContent,
    positions: ReaderPositionRepository,
    onClose: () -> Unit,
) {
    val itemCount = content.paragraphs.size + 1
    val saved = remember(content.url, itemCount) { positions.load(content.url, itemCount) }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = saved?.index ?: 0,
        initialFirstVisibleItemScrollOffset = saved?.offset ?: 0,
    )

    fun savePosition() {
        positions.save(
            content.url,
            ReaderPosition(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, itemCount),
        )
    }

    // Save whenever scrolling comes to rest, and once more when the reader leaves the screen.
    LaunchedEffect(listState, content.url, itemCount) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) savePosition()
        }
    }
    DisposableEffect(content.url, itemCount) { onDispose { savePosition() } }

    val progress by remember(listState) {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            if (total <= 1) 0f
            else (listState.firstVisibleItemIndex.toFloat() / (total - 1)).coerceIn(0f, 1f)
        }
    }

    val title = content.chapterTitle?.takeIf { it.isNotEmpty() }
        ?: content.pageTitle.takeIf { it.isNotEmpty() }
        ?: "Reader"

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Close reader")
                        }
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 16.dp),
                        )
                    }
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
            }
            SelectionContainer(Modifier.weight(1f)) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item { ChapterHeader(content) }
                    items(content.paragraphs) { paragraph -> ReaderParagraph(paragraph) }
                }
            }
        }
    }
}

@Composable
private fun ChapterHeader(content: ExtractedContent) {
    val chapterTitle = content.chapterTitle
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (content.pageTitle.isNotEmpty()) {
            Text(
                content.pageTitle,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Skip it when the text itself already opens with the same heading.
        if (!chapterTitle.isNullOrEmpty() && chapterTitle != content.paragraphs.firstOrNull()?.text) {
            Text(chapterTitle, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun ReaderParagraph(paragraph: ExtractedParagraph) {
    if (paragraph.isHeading) {
        val style = when (paragraph.headingLevel) {
            1 -> MaterialTheme.typography.headlineSmall
            2 -> MaterialTheme.typography.titleLarge
            else -> MaterialTheme.typography.titleMedium
        }
        Text(paragraph.text, style = style)
    } else {
        // Generous line height so Myanmar script stacks are not clipped.
        Text(
            paragraph.text,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 32.sp),
        )
    }
}
