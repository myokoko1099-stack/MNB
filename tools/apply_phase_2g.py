#!/usr/bin/env python3
"""Applies the Phase 2G edits to existing MNB source files.

Usage (from anywhere):  python3 tools/apply_phase_2g.py ~/MNB

Every edit replaces one exact piece of text that must occur exactly once. All edits are checked
first; if anything does not match, NOTHING is written and the problems are listed.
"""
import pathlib
import sys

ROOT = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else ".").expanduser().resolve()
EDITS = []

MAIN = "app/src/main/java/com/mnb/browser/"
TEST = "app/src/test/java/com/mnb/browser/"


def edit(path, old, new):
    EDITS.append((path, old, new))


# ---------------------------------------------------------------- UrlPolicy
edit(MAIN + "navigation/UrlPolicy.kt",
'''    /**
     * The blank "new tab" address. The new-tab page is drawn on top of it, so it is deliberately
     * not the search engine: changing it would change how tabs are recognised as "new".
     */
    const val HOME_URL = "https://duckduckgo.com/"
''',
'''    /**
     * A real website (Google). It is only a fallback address, e.g. for a tab that crashed repeatedly
     * (Phase 2F); it is NOT the new-tab page, so a tab that shows it is a normal Google page.
     */
    const val HOME_URL = "https://www.google.com/"

    /**
     * Address of the app's own new-tab page. Nothing is ever requested from the network for it:
     * the WebView of such a tab is left empty (or shows about:blank) and the app draws the page.
     * ".invalid" can never resolve, so even a leak could not reach a real site.
     */
    const val NEW_TAB_URL = "https://mnb.invalid/new-tab"

    /** What a WebView reports after the new-tab page was "loaded" with [BLANK_PAGE]. */
    const val BLANK_PAGE = "about:blank"

    /** Old default home address, only used to migrate tabs stored by earlier versions. */
    private const val LEGACY_HOME_URL = "https://duckduckgo.com"

    private const val MAX_LINK_LENGTH = 4096
''')

edit(MAIN + "navigation/UrlPolicy.kt",
'''    /** Decides whether the WebView may follow a link / redirect. */''',
'''    /** True for no address, about:blank and the new-tab address: pages that are never a real website. */
    fun isNewTabAddress(url: String?): Boolean {
        if (url.isNullOrBlank()) return true
        val text = url.trim()
        return text.equals(BLANK_PAGE, ignoreCase = true) ||
            text.trimEnd('/').equals(NEW_TAB_URL, ignoreCase = true)
    }

    /** The address to tell tab and history code about: about:blank is reported as the new-tab address. */
    fun reportedAddress(url: String?): String? =
        if (url != null && url.trim().equals(BLANK_PAGE, ignoreCase = true)) NEW_TAB_URL else url

    /** True for the home address that earlier versions used for new tabs (stored tabs are migrated). */
    fun isLegacyHomeUrl(url: String?): Boolean =
        url != null && url.trim().trimEnd('/').equals(LEGACY_HOME_URL, ignoreCase = true)

    /**
     * The address to open when the user long-presses a link and chooses "Open in new tab", or null
     * when the link must not be opened: only https (http is upgraded), a real host, no whitespace,
     * no new-tab address, and a sane length.
     */
    fun linkTarget(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text.length > MAX_LINK_LENGTH) return null
        if (text.any { it.isWhitespace() }) return null
        val url = when (val decision = checkNavigation(text)) {
            Decision.Allow -> text
            is Decision.Upgrade -> decision.url
            Decision.Block -> return null
        }
        // Host check without java.net.URI, which rejects many valid page addresses (for example "|").
        val authority = url.substring(8).takeWhile { it != '/' && it != '?' && it != '#' }
        val host = authority.substringAfterLast('@').substringBefore(':')
        if (host.isBlank() || isNewTabAddress(url)) return null
        return url
    }

    /** Decides whether the WebView may follow a link / redirect. */''')

# ---------------------------------------------------------------- PageRules
edit(MAIN + "library/PageRules.kt",
'''        return url.trim().trimEnd('/').equals(UrlPolicy.HOME_URL.trimEnd('/'), ignoreCase = true)''',
'''        return UrlPolicy.isNewTabAddress(url)''')

edit(MAIN + "library/PageRules.kt",
'''    /** True for the empty address and for the new-tab (home) address. */''',
'''    /** True for the empty address, about:blank and the new-tab address (never a real page, not even Google's). */''')

# ---------------------------------------------------------------- TabManager
edit(MAIN + "tabs/TabManager.kt",
'''    private val homeUrl: String = UrlPolicy.HOME_URL,''',
'''    private val homeUrl: String = UrlPolicy.NEW_TAB_URL,''')

edit(MAIN + "tabs/TabManager.kt",
'''        val cleaned = stored.tabs
            .filter { it.id.isNotBlank() && it.url.isNotBlank() }
            .distinctBy { it.id }
            .take(maxTabs)
            .map { it.copy(url = safeUrl(it.url)) }
''',
'''        // Tabs that earlier versions left on the old home page become fresh new-tab pages. They get a new
        // id so that their saved WebView state (the old home page) is not restored.
        val replacedIds = HashMap<String, String>()
        val cleaned = stored.tabs
            .filter { it.id.isNotBlank() && it.url.isNotBlank() }
            .distinctBy { it.id }
            .take(maxTabs)
            .map { tab ->
                if (UrlPolicy.isLegacyHomeUrl(tab.url)) {
                    val freshId = newId()
                    replacedIds[tab.id] = freshId
                    tab.copy(id = freshId, url = homeUrl, title = "")
                } else {
                    tab.copy(url = safeUrl(tab.url))
                }
            }
''')

edit(MAIN + "tabs/TabManager.kt",
'''            activeTabId = stored.activeTabId?.takeIf { id -> cleaned.any { it.id == id } }''',
'''            val wantedActive = stored.activeTabId?.let { replacedIds[it] ?: it }
            activeTabId = wantedActive?.takeIf { id -> cleaned.any { it.id == id } }''')

# ---------------------------------------------------------------- WebViewController
WVC = MAIN + "web/WebViewController.kt"

edit(WVC,
'''import android.os.Bundle
''',
'''import android.os.Bundle
import android.os.Handler
import android.os.Looper
''')

edit(WVC,
'''    var pageListener: ((tabId: String, url: String?, title: String?) -> Unit)? = null
''',
'''    var pageListener: ((tabId: String, url: String?, title: String?) -> Unit)? = null

    /**
     * Phase 2G: called (main thread) when the user long-presses a link in the ACTIVE tab, with the raw
     * link address from the page. The receiver must validate it (see UrlPolicy.linkTarget).
     */
    var linkLongPressListener: ((tabId: String, url: String) -> Unit)? = null
''')

edit(WVC,
'''    fun load(url: String) { activeView()?.loadUrl(url) }''',
'''    fun load(url: String) {
        // The new-tab page is the app's own page: it is shown by blanking the WebView, never by a request.
        activeView()?.loadUrl(if (UrlPolicy.isNewTabAddress(url)) UrlPolicy.BLANK_PAGE else url)
    }''')

edit(WVC,
'''        } else if (!restoreSession(tabId, view)) {
            view.loadUrl(allowedUrl(initialUrl))
        }
        return view''',
'''        } else if (!restoreSession(tabId, view)) {
            val target = allowedUrl(initialUrl)
            // A new tab starts on the app's own new-tab page: no website is requested for it.
            if (!UrlPolicy.isNewTabAddress(target)) view.loadUrl(target)
        }
        view.setOnLongClickListener { handleLongPress(tabId, view) }
        return view''')

edit(WVC,
'''        UrlPolicy.Decision.Block -> UrlPolicy.HOME_URL
    }
''',
'''        UrlPolicy.Decision.Block -> UrlPolicy.NEW_TAB_URL
    }

    /**
     * Long press on a link (also a link around an image) in the active tab. The link is only reported
     * to [linkLongPressListener]; nothing is loaded here. Returns true when the press was used.
     */
    private fun handleLongPress(tabId: String, view: WebView): Boolean {
        if (tabId != activeId || views[tabId] !== view) return false
        val listener = linkLongPressListener ?: return false
        val result = view.hitTestResult
        when (result.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                val link = result.extra ?: return false
                listener(tabId, link)
                return true
            }
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                // For an image that is a link, "extra" is the image address: ask the page for the link.
                val reply = Handler(Looper.getMainLooper()) { message ->
                    val link = message.data.getString("url")
                    if (link != null && tabId == activeId && views[tabId] === view) listener(tabId, link)
                    true
                }
                view.requestFocusNodeHref(reply.obtainMessage())
                return true
            }
        }
        return false
    }
''')

edit(WVC,
'''            pageListener?.invoke(tabId, view.url ?: url, view.title)''',
'''            pageListener?.invoke(tabId, UrlPolicy.reportedAddress(view.url ?: url), view.title)''')

edit(WVC,
'''            pageListener?.invoke(tabId, url, null)''',
'''            pageListener?.invoke(tabId, UrlPolicy.reportedAddress(url), null)''')

edit(WVC,
'''            if (tabId == activeId) stateManager.onTitle(title)
            pageListener?.invoke(tabId, null, title)''',
'''            // A blank page can report its own address as the title; the new-tab page has no title.
            val shown = title?.takeUnless { it.trim().equals(UrlPolicy.BLANK_PAGE, ignoreCase = true) }
            if (tabId == activeId) stateManager.onTitle(shown)
            pageListener?.invoke(tabId, null, shown)''')

edit(WVC,
'''            progress = view.progress,''',
'''            // A new tab that never loaded anything has no progress to show.
            progress = if (view.url == null) 100 else view.progress,''')

# ---------------------------------------------------------------- DAO + repository (history pruning / search)
edit(MAIN + "translation/db/BrowserDataDaos.kt",
r'''    @Query("DELETE FROM browser_history")
    fun clear(): Int
''',
r'''    @Query("DELETE FROM browser_history")
    fun clear(): Int

    /** [pattern] is a LIKE pattern built by BrowserDataRepository.likePattern (backslash is the escape character). */
    @Query(
        "SELECT * FROM browser_history WHERE url LIKE :pattern ESCAPE '\\' OR title LIKE :pattern ESCAPE '\\' " +
            "ORDER BY visitedAtMillis DESC, historyId DESC LIMIT :limit",
    )
    fun search(pattern: String, limit: Int): List<BrowserHistoryEntity>

    @Query("DELETE FROM browser_history WHERE visitedAtMillis < :cutoffMillis")
    fun deleteOlderThan(cutoffMillis: Long): Int

    /** Deletes everything except the [keep] newest visits. */
    @Query(
        "DELETE FROM browser_history WHERE historyId NOT IN (" +
            "SELECT historyId FROM browser_history ORDER BY visitedAtMillis DESC, historyId DESC LIMIT :keep)",
    )
    fun trimToNewest(keep: Int): Int
''')

edit(MAIN + "translation/db/BrowserDataRepository.kt",
r'''    fun clearHistory() { history.clear() }
''',
r'''    fun clearHistory() { history.clear() }

    /**
     * Visits whose title or address contains [query] (plain text, not a pattern), newest first. A blank
     * [query] returns the newest visits, like [getRecentHistory].
     */
    fun searchHistory(query: String, limit: Int = 100): List<BrowserHistoryEntity> {
        require(limit > 0)
        val text = query.trim()
        if (text.isEmpty()) return history.recent(limit)
        return history.search(likePattern(text), limit)
    }

    /**
     * Keeps the history from growing without limit (Phase 2G): removes visits older than [maxAgeMillis],
     * then, if more than [maxEntries] remain, the oldest ones. The newest [maxEntries] visits that are
     * not too old are always kept. One transaction. Returns how many rows were removed.
     */
    fun pruneHistory(
        now: Long,
        maxAgeMillis: Long = HISTORY_MAX_AGE_MILLIS,
        maxEntries: Int = HISTORY_MAX_ENTRIES,
    ): Int {
        require(maxAgeMillis > 0)
        require(maxEntries > 0)
        var removed = 0
        db.runInTransaction(
            Runnable {
                removed += history.deleteOlderThan(now - maxAgeMillis)
                removed += history.trimToNewest(maxEntries)
            },
        )
        return removed
    }

    companion object {
        /** At most this many visits are kept. */
        const val HISTORY_MAX_ENTRIES = 2000

        /** Visits older than this (90 days) are removed. */
        const val HISTORY_MAX_AGE_MILLIS = 90L * 24 * 60 * 60 * 1000

        /** LIKE pattern matching [query] as plain text: percent, underscore and backslash are escaped. */
        fun likePattern(query: String): String {
            val escaped = StringBuilder()
            for (c in query) {
                if (c == '\\' || c == '%' || c == '_') escaped.append('\\')
                escaped.append(c)
            }
            return "%$escaped%"
        }
    }
''')

# ---------------------------------------------------------------- BrowserLibrary
LIB = MAIN + "library/BrowserLibrary.kt"
edit(LIB,
'''    private var historyLoaded = false
''',
'''    private var historyLoaded = false
    private var historyQuery = ""

    // IO thread only: visits stored since the history was last pruned (the first visit prunes).
    private var visitsSincePrune = PRUNE_EVERY
''')

edit(LIB,
'''            repository.recordVisit(page, cleanTitle, now)
''',
'''            repository.recordVisit(page, cleanTitle, now)
            pruneIfDue(now)
''')

edit(LIB,
'''    /** Loads the newest [HISTORY_LIMIT] history entries, newest first. */
    fun refreshHistory() {
        if (!historyLoaded) historyState = LoadState.Loading
        runIo(onFailure = { historyState = LoadState.Error }) {
            val rows = loadHistoryRows()
            onMain { applyHistory(rows) }
        }
    }
''',
'''    /**
     * Loads the newest [HISTORY_LIMIT] history entries, newest first. A non-blank [query] keeps only the
     * visits whose title or address contains it. Only the answer to the latest query is shown.
     */
    fun refreshHistory(query: String = "") {
        val asked = query.trim()
        historyQuery = asked
        if (!historyLoaded) historyState = LoadState.Loading
        runIo(onFailure = { historyState = LoadState.Error }) {
            val rows = loadHistoryRows(asked)
            onMain { if (historyQuery == asked) applyHistory(rows) }
        }
    }
''')

edit(LIB,
'''        history = history.filterNot { it.id == id }
        runIo(onFailure = { historyState = LoadState.Error }) {
            repository.removeHistoryEntry(id)
            val rows = loadHistoryRows()
            onMain { applyHistory(rows) }
        }
''',
'''        history = history.filterNot { it.id == id }
        val asked = historyQuery
        runIo(onFailure = { historyState = LoadState.Error }) {
            repository.removeHistoryEntry(id)
            val rows = loadHistoryRows(asked)
            onMain { if (historyQuery == asked) applyHistory(rows) }
        }
''')

edit(LIB,
'''            repository.clearHistory()
            val rows = loadHistoryRows()
            onMain { applyHistory(rows) }
''',
'''            repository.clearHistory()
            // Nothing is left, whatever the search was.
            onMain { applyHistory(emptyList()) }
''')

edit(LIB,
'''    private fun loadHistoryRows(): List<LibraryEntry> =
        repository.getRecentHistory(HISTORY_LIMIT).map { it.toEntry() }
''',
'''    private fun loadHistoryRows(query: String): List<LibraryEntry> =
        (if (query.isEmpty()) repository.getRecentHistory(HISTORY_LIMIT) else repository.searchHistory(query, HISTORY_LIMIT))
            .map { it.toEntry() }

    /** IO thread. Runs on the first stored visit, then after every [PRUNE_EVERY] visits (see Phase 2G). */
    private fun pruneIfDue(now: Long) {
        visitsSincePrune++
        if (visitsSincePrune < PRUNE_EVERY) return
        visitsSincePrune = 0
        repository.pruneHistory(now)
    }
''')

edit(LIB,
'''        const val HISTORY_LIMIT = 200
''',
'''        const val HISTORY_LIMIT = 200

        /** The history is pruned on the first stored visit and then after this many stored visits. */
        const val PRUNE_EVERY = 25
''')

# ---------------------------------------------------------------- BrowserScreen
BS = MAIN + "ui/BrowserScreen.kt"
edit(BS,
'''import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
''',
'''import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
''')

edit(BS,
'''    var historyOpen by rememberSaveable { mutableStateOf(false) }
''',
'''    var historyOpen by rememberSaveable { mutableStateOf(false) }
    var historyQuery by rememberSaveable { mutableStateOf("") }
    // Phase 2G: a link the user long-pressed, waiting for "Open in new tab" (already checked by UrlPolicy).
    var pendingLink by rememberSaveable { mutableStateOf<String?>(null) }
''')

edit(BS,
'''    val isNewTab = state.currentUrl.isBlank() || state.currentUrl.trimEnd('/') == UrlPolicy.HOME_URL.trimEnd('/')
    // On the new-tab page the blank home address is an implementation detail: never show or edit it.''',
'''    // Only the app's own new-tab address (or no address) is the new-tab page. A real website, Google
    // included, is always shown as a website.
    val isNewTab = UrlPolicy.isNewTabAddress(state.currentUrl)
    // On the new-tab page its internal address is an implementation detail: never show or edit it.''')

edit(BS,
'''    LaunchedEffect(historyOpen) { if (historyOpen) library.refreshHistory() }
''',
'''    LaunchedEffect(historyOpen) { if (historyOpen) library.refreshHistory(historyQuery) else historyQuery = "" }

    // Phase 2G: long press on a link of the active tab. The address is validated before anything is shown.
    DisposableEffect(webController) {
        webController.linkLongPressListener = { _, url -> UrlPolicy.linkTarget(url)?.let { pendingLink = it } }
        onDispose { webController.linkLongPressListener = null }
    }
''')

edit(BS,
'''extractEnabled = state.currentUrl.isNotBlank() && !state.isLoading && state.error == null,''',
'''extractEnabled = !isNewTab && !state.isLoading && state.error == null,''')

edit(BS,
'''                    state = library.historyState,
''',
'''                    state = library.historyState,
                    query = historyQuery,
                    onQueryChange = { text ->
                        historyQuery = text
                        library.refreshHistory(text)
                    },
''')

edit(BS,
'''onRetry = { library.refreshHistory() },''',
'''onRetry = { library.refreshHistory(historyQuery) },''')

edit(BS,
'''            if (extractionOpen) {
''',
'''            pendingLink?.let { link ->
                LinkDialog(
                    link = link,
                    canOpenNewTab = tabManager.canOpenNewTab,
                    onOpenInNewTab = { if (tabManager.newTab(link) != null) pendingLink = null },
                    onDismiss = { pendingLink = null },
                )
            }

            if (extractionOpen) {
''')

# ---------------------------------------------------------------- History screen
LS = MAIN + "ui/library/LibraryScreens.kt"
edit(LS,
'''import androidx.compose.material.icons.filled.DateRange
''',
'''import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
''')
edit(LS,
'''import androidx.compose.material3.MaterialTheme
''',
'''import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
''')
edit(LS,
'''import com.mnb.browser.library.LibraryEntry
''',
'''import com.mnb.browser.library.HistoryGrouping
import com.mnb.browser.library.HistoryText
import com.mnb.browser.library.LibraryEntry
''')
edit(LS,
'''    onClearAll: () -> Unit,
''',
'''    onClearAll: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
''')
edit(LS,
'''        LibraryContent(
            entries = entries,
            state = state,
            emptyIcon = Icons.Default.DateRange,
            emptyTitle = LibraryText.HISTORY_EMPTY_TITLE,
            emptyBody = LibraryText.HISTORY_EMPTY_BODY,
            showTime = true,
            deleteDescription = LibraryText.HISTORY_DELETE,
            onOpen = onOpen,
            onDelete = onDelete,
            onRetry = onRetry,
        )
''',
'''        Column(Modifier.fillMaxSize()) {
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
''')
edit(LS,
'''/** Loading, error, empty and list states, with a short cross-fade between them. */''',
'''/** Search box of the History screen. */
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

/** Loading, error, empty and list states, with a short cross-fade between them. */''')

# ---------------------------------------------------------------- Gradle
edit("app/build.gradle.kts",
'''    testOptions { unitTests.isIncludeAndroidResources = true }
''',
'''    testOptions { unitTests.isIncludeAndroidResources = true }
    // Phase 2G: the committed Room schema JSON files are test assets for MigrationTestHelper.
    sourceSets["test"].assets.srcDir("$projectDir/schemas")
''')
edit("app/build.gradle.kts",
'''    testImplementation("androidx.work:work-testing:2.9.0")
''',
'''    testImplementation("androidx.work:work-testing:2.9.0")
    // Phase 2G: MigrationTestHelper (schema based migration test, runs under Robolectric)
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("androidx.test:core:1.5.0")
''')
edit("app/build.gradle.kts",
'''ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
''',
'''ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Phase 2G: KSP writes the schema JSON into the same folder the unit tests read as assets, so the
// asset merge must wait for it (otherwise Gradle reports a missing task dependency).
tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("UnitTestAssets")) {
        mustRunAfter(tasks.matching { it.name.startsWith("ksp") })
    }
}
''')

# ---------------------------------------------------------------- Existing tests that assumed HOME_URL == new tab
edit(TEST + "library/PageRulesTest.kt",
'''        assertFalse(PageRules.isRecordable(UrlPolicy.HOME_URL))
        assertFalse(PageRules.isRecordable(UrlPolicy.HOME_URL.trimEnd('/')))
        assertFalse(PageRules.isRecordable(UrlPolicy.HOME_URL.uppercase()))
        // A search on the same site is a real page.
        assertTrue(PageRules.isRecordable(UrlPolicy.HOME_URL + "?q=kotlin"))
''',
'''        assertFalse(PageRules.isRecordable(UrlPolicy.NEW_TAB_URL))
        assertFalse(PageRules.isRecordable(UrlPolicy.NEW_TAB_URL + "/"))
        assertFalse(PageRules.isRecordable(UrlPolicy.NEW_TAB_URL.uppercase()))
        // The same address with a query is not the new-tab page.
        assertTrue(PageRules.isRecordable(UrlPolicy.NEW_TAB_URL + "?q=kotlin"))
        // Google itself is a real website, not the new-tab page.
        assertTrue(PageRules.isRecordable(UrlPolicy.HOME_URL))
''')
edit(TEST + "library/PageRulesTest.kt",
'''        assertTrue(PageRules.isNewTab(UrlPolicy.HOME_URL))
''',
'''        assertTrue(PageRules.isNewTab(UrlPolicy.NEW_TAB_URL))
        assertTrue(PageRules.isNewTab("about:blank"))
        assertFalse(PageRules.isNewTab(UrlPolicy.HOME_URL))
''')
edit(TEST + "library/BrowserLibraryTest.kt",
'''        library.toggleBookmark(UrlPolicy.HOME_URL, "New tab")''',
'''        library.toggleBookmark(UrlPolicy.NEW_TAB_URL, "New tab")''')
edit(TEST + "library/BrowserLibraryTest.kt",
'''        library.checkBookmarked(UrlPolicy.HOME_URL)''',
'''        library.checkBookmarked(UrlPolicy.NEW_TAB_URL)''')
edit(TEST + "library/BrowserLibraryTest.kt",
'''"https://", UrlPolicy.HOME_URL, UrlPolicy.HOME_URL.trimEnd('/'),''',
'''"https://", UrlPolicy.NEW_TAB_URL, UrlPolicy.NEW_TAB_URL.uppercase(),''')
edit(TEST + "tabs/TabDisplayTest.kt",
'''TabDisplay.label(tab(UrlPolicy.HOME_URL))''',
'''TabDisplay.label(tab(UrlPolicy.NEW_TAB_URL))''')
edit(TEST + "navigation/UrlPolicyTest.kt",
'''    @Test fun homeUrlIsUnchangedBecauseTabsRecogniseNewTabsByIt() =
        assertEquals("https://duckduckgo.com/", UrlPolicy.HOME_URL)
''',
'''    @Test fun homeUrlIsGoogleAndNewTabsHaveTheirOwnAddress() {
        assertEquals("https://www.google.com/", UrlPolicy.HOME_URL)
        assertFalse(UrlPolicy.isNewTabAddress(UrlPolicy.HOME_URL))
        assertTrue(UrlPolicy.isNewTabAddress(UrlPolicy.NEW_TAB_URL))
    }
''')


def main():
    texts = {}
    problems = []
    missing = set()
    for path, old, new in EDITS:
        if path in missing:
            continue
        if path not in texts:
            f = ROOT / path
            if not f.is_file():
                if path not in missing:
                    missing.add(path)
                    problems.append("missing file: " + path)
                continue
            texts[path] = f.read_text(encoding="utf-8")
        count = texts[path].count(old)
        if count != 1:
            first = old.strip().splitlines()[0][:70]
            problems.append("%s: expected 1 match, found %d for: %s" % (path, count, first))
            continue
        texts[path] = texts[path].replace(old, new, 1)
    if problems:
        print("Nothing was changed. Problems:")
        for p in problems:
            print("  - " + p)
        sys.exit(1)
    for path, text in texts.items():
        (ROOT / path).write_text(text, encoding="utf-8")
        print("updated " + path)
    print("Done: %d edits in %d files." % (len(EDITS), len(texts)))


if __name__ == "__main__":
    main()
