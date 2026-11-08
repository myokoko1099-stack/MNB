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
import androidx.compose.runtime.key
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
import com.mnb.browser.reader.translation.LocalReaderTranslationActions
import com.mnb.browser.reader.translation.ReaderChapterTranslation
import com.mnb.browser.reader.translation.ReaderTranslationDisplay
import com.mnb.browser.reader.translation.ReaderViewMode
import com.mnb.browser.reader.translation.canRequestTranslation
import kotlin.math.roundToInt

/**
 * Full-screen novel reader. List item 0 is the chapter header; paragraphs follow in their
 * original order, so a saved list index maps straight back to the same paragraph.
 *
 * Appearance (font size, margin, paragraph spacing, light/dark) comes from [settingsRepository]
 * and is saved on every change, so it is restored the next time the app opens.
 *
 * Translation display (Phase 04-I10-B): [translation] is the open chapter's translation state
 * (null = no translation support, the reader works exactly as before). The original text is always
 * readable. The translated text is offered only when the state is Completed. The saved reading
 * position belongs to the original text and is never overwritten from the translated view.
 *
 * Instant translation (Phase 04-I10-C1): when a [LocalReaderTranslationActions] is provided, a
 * "ဘာသာပြန်" button asks it to translate the open chapter; it is shown only while the chapter has
 * an identity and its state allows a request. Without the local the reader is unchanged.
 */
@Composable
fun ReaderScreen(
    content: ExtractedContent,
    positions: ReaderPositionRepository,
    settingsRepository: ReaderSettingsRepository,
    onClose: () -> Unit,
    translation: ReaderChapterTranslation? = null,
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

    // Translation view (I10-B): its own list state, never saved, so the original's saved position stays valid.
    val translationListState = rememberLazyListState()
    val chapterKey = translation?.identity?.chapterId ?: content.url
    var requestedMode by remember(chapterKey) { mutableStateOf(ReaderViewMode.ORIGINAL) }
    val display = remember(translation?.state) { ReaderTranslationDisplay.of(translation?.state) }
    val mode = display.effectiveMode(requestedMode)
    val activeListState = if (mode == ReaderViewMode.TRANSLATION) translationListState else listState

    // Instant translation (I10-C1): pick up an already stored translation when a chapter is shown.
    val translationActions = LocalReaderTranslationActions.current
    LaunchedEffect(chapterKey) { translationActions?.onChapterShown() }
    val canRequestTranslation = translationActions != null &&
        translation?.identity != null &&
        translation.state.canRequestTranslation

    val progress by remember(activeListState) {
        derivedStateOf {
            val total = activeListState.layoutInfo.totalItemsCount
            if (total <= 1) 0f
            else (activeListState.firstVisibleItemIndex.toFloat() / (total - 1)).coerceIn(0f, 1f)
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
                            if (canRequestTranslation) {
                                TextButton(onClick = { translationActions?.requestTranslation(content) }) {
                                    Text("ဘာသာပြန်")
                                }
                            }
                            TextButton(onClick = { showSettings = !showSettings }) { Text("Aa") }
                        }
                        if (showSettings) {
                            ReaderSettingsPanel(
                                settings = settings,
                                onChange = ::updateSettings,
                                onReset = { updateSettings(ReaderSettings()) },
                            )
                        }
                        if (display.showControls) {
                            TranslationBar(display, mode, onModeChange = { requestedMode = it })
                        }
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
                }
                SelectionContainer(Modifier.weight(1f)) {
                    key(mode) {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            state = activeListState,
                            contentPadding = PaddingValues(horizontal = settings.marginDp.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(settings.paragraphSpacingDp.dp),
                        ) {
                            item { ChapterHeader(content) }
                            if (mode == ReaderViewMode.TRANSLATION) {
                                items(display.translatedParagraphs) { text ->
                                    ReaderParagraph(ExtractedParagraph(text), settings)
                                }
                            } else {
                                items(content.paragraphs) { paragraph -> ReaderParagraph(paragraph, settings) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Original / translation switch plus the translation status line (Phase 04-I10-B). */
@Composable
private fun TranslationBar(
    display: ReaderTranslationDisplay,
    mode: ReaderViewMode,
    onModeChange: (ReaderViewMode) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == ReaderViewMode.ORIGINAL,
                onClick = { onModeChange(ReaderViewMode.ORIGINAL) },
                label = { Text("မူရင်း") },
            )
            FilterChip(
                selected = mode == ReaderViewMode.TRANSLATION,
                enabled = display.canShowTranslation,
                onClick = { onModeChange(ReaderViewMode.TRANSLATION) },
                label = { Text("မြန်မာဘာသာပြန်") },
            )
        }
        display.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        display.detail?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (display.showProgress) {
            val fraction = display.progress
            if (fraction == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
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
        Text("Reader Quick Controls", style = MaterialTheme.typography.titleSmall)
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
