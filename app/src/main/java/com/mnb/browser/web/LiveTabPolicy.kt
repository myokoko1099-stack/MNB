package com.mnb.browser.web

/**
 * Phase 2F: decides which background tabs lose their live WebView to save memory (pure Kotlin,
 * unit-testable, no Android types).
 *
 * A tab whose WebView is unloaded is not closed: its page and back/forward history are saved first
 * and the WebView is created again, from that saved state, the next time the tab is shown.
 */
object LiveTabPolicy {
    /** How many WebViews stay alive at once (the active tab included). */
    const val DEFAULT_MAX_LIVE = 6

    /**
     * Returns the tabs whose WebView should be unloaded so that at most [maxLive] stay alive.
     *
     * @param liveIdsOldestFirst tabs that currently have a WebView, least recently used first.
     * @param activeId the tab being shown; it is never returned.
     * @param maxLive the most WebViews that may stay alive; values below 1 count as 1.
     */
    fun tabsToUnload(liveIdsOldestFirst: List<String>, activeId: String?, maxLive: Int): List<String> {
        val limit = maxLive.coerceAtLeast(1)
        val live = liveIdsOldestFirst.distinct()
        val excess = live.size - limit
        if (excess <= 0) return emptyList()
        return live.filter { it != activeId }.take(excess)
    }
}

/**
 * Phase 2F: tells a tab that keeps crashing its renderer apart from one that crashed once.
 *
 * The first crash is recovered silently (the tab is rebuilt from its saved state). A second crash of
 * the same tab within [windowMillis] means that rebuilding it would crash again, so the tab must not
 * be restored automatically. Main thread only; it keeps only times, never addresses.
 */
class CrashLoopGuard(
    private val maxCrashes: Int = DEFAULT_MAX_CRASHES,
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
) {
    private val crashesByTab = HashMap<String, ArrayDeque<Long>>()

    /** Records a crash of [tabId] at [nowMillis]. Returns true when the tab is crash-looping. */
    fun recordCrash(tabId: String, nowMillis: Long): Boolean {
        val times = crashesByTab.getOrPut(tabId) { ArrayDeque() }
        while (times.isNotEmpty() && nowMillis - times.first() > windowMillis) times.removeFirst()
        times.addLast(nowMillis)
        while (times.size > maxCrashes) times.removeFirst()
        return times.size >= maxCrashes
    }

    /** Forgets a closed tab. */
    fun forget(tabId: String) {
        crashesByTab.remove(tabId)
    }

    companion object {
        const val DEFAULT_MAX_CRASHES = 2
        const val DEFAULT_WINDOW_MILLIS = 60_000L
    }
}
