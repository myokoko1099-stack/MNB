package com.mnb.browser.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.mnb.browser.reader.ReaderSettings
import com.mnb.browser.reader.ReaderSettingsRepository
import com.mnb.browser.reader.ReaderTheme
import kotlin.math.roundToInt

/**
 * Full-screen novel reader. List item 0 is the chapter header; paragraphs follow in their
 * original order, so a saved list index maps straight back to the same paragraph.
 *
 * Appearance (font size, margin, paragraph spacing, light/dark) comes from [settingsRepository]
 * and is saved on every change, so it is restored the next time the app opens.
 */
@Composable
fun ReaderScreen(
    content: ExtractedContent,
    positions: ReaderPositionRepository,
    settingsRepository: ReaderSettingsRepository,
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

    var settings by remember { mutableStateOf(settingsRepository.load()) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    fun updateSettings(new: ReaderSettings) {
        val clean = new.sanitized()
        settings = clean
        settingsRepository.save(clean)
    }

    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.theme) {
        ReaderTheme.SYSTEM -> systemDark
        ReaderTheme.LIGHT -> false
        ReaderTheme.DARK -> true
    }

    val title = content.chapterTitle?.takeIf { it.isNotEmpty() }
        ?: content.pageTitle.takeIf { it.isNotEmpty() }
        ?: "Reader"

    // The theme applies to the reader only; the browser keeps following the system setting.
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
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
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                            )
                            TextButton(onClick = { showSettings = !showSettings }) { Text("Aa") }
                        }
                        if (showSettings) {
                            ReaderSettingsPanel(
                                settings = settings,
                                onChange = ::updateSettings,
                                onReset = { updateSettings(ReaderSettings()) },
                            )
                        }
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
                }
                SelectionContainer(Modifier.weight(1f)) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(horizontal = settings.marginDp.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(settings.paragraphSpacingDp.dp),
                    ) {
                        item { ChapterHeader(content) }
                        items(content.paragraphs) { paragraph -> ReaderParagraph(paragraph, settings) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderSettingsPanel(
    settings: ReaderSettings,
    onChange: (ReaderSettings) -> Unit,
    onReset: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 340.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SettingSlider("Font size", settings.fontSizeSp, ReaderSettings.FONT_SIZE_RANGE, "sp") {
            onChange(settings.copy(fontSizeSp = it))
        }
        SettingSlider("Margin", settings.marginDp, ReaderSettings.MARGIN_RANGE, "dp") {
            onChange(settings.copy(marginDp = it))
        }
        SettingSlider("Paragraph spacing", settings.paragraphSpacingDp, ReaderSettings.PARAGRAPH_SPACING_RANGE, "dp") {
            onChange(settings.copy(paragraphSpacingDp = it))
        }
        Text("Theme", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeChip("System", ReaderTheme.SYSTEM, settings, onChange)
            ThemeChip("Light", ReaderTheme.LIGHT, settings, onChange)
            ThemeChip("Dark", ReaderTheme.DARK, settings, onChange)
        }
        TextButton(onClick = onReset) { Text("Reset to default") }
    }
}

@Composable
private fun ThemeChip(
    label: String,
    theme: ReaderTheme,
    settings: ReaderSettings,
    onChange: (ReaderSettings) -> Unit,
) {
    FilterChip(
        selected = settings.theme == theme,
        onClick = { onChange(settings.copy(theme = theme)) },
        label = { Text(label) },
    )
}

@Composable
private fun SettingSlider(label: String, value: Int, range: IntRange, unit: String, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("$value $unit", style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
        )
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
private fun ReaderParagraph(paragraph: ExtractedParagraph, settings: ReaderSettings) {
    if (paragraph.isHeading) {
        val style = when (paragraph.headingLevel) {
            1 -> MaterialTheme.typography.headlineSmall
            2 -> MaterialTheme.typography.titleLarge
            else -> MaterialTheme.typography.titleMedium
        }
        Text(paragraph.text, style = style)
    } else {
        // Line height scales with the font size so Myanmar script stacks are not clipped.
        Text(
            paragraph.text,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = settings.fontSizeSp.sp,
                lineHeight = settings.lineHeightSp.sp,
            ),
        )
    }
}
