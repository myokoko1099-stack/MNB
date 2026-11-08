package com.mnb.browser.library

import java.util.Calendar
import java.util.TimeZone

/** Date groups of the History screen, newest first. */
enum class HistoryGroup(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    LAST_7_DAYS("Last 7 days"),
    LAST_30_DAYS("Last 30 days"),
    OLDER("Older"),
}

/** One date group with its entries (same order as the input list). */
data class HistorySection(val group: HistoryGroup, val entries: List<LibraryEntry>)

/** Splits a history list into date groups (pure Kotlin, unit-testable; no java.time because minSdk is 24). */
object HistoryGrouping {

    /** The group of a visit at [timeMillis], seen from [nowMillis]. A time in the future counts as today. */
    fun groupOf(timeMillis: Long, nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): HistoryGroup {
        val today = startOfDay(nowMillis, zone)
        return when {
            timeMillis >= today -> HistoryGroup.TODAY
            timeMillis >= daysBefore(today, 1, zone) -> HistoryGroup.YESTERDAY
            timeMillis >= daysBefore(today, 7, zone) -> HistoryGroup.LAST_7_DAYS
            timeMillis >= daysBefore(today, 30, zone) -> HistoryGroup.LAST_30_DAYS
            else -> HistoryGroup.OLDER
        }
    }

    /**
     * Groups [entries]. Only groups that have entries are returned, in display order; inside a group the
     * input order is kept, so a newest-first list stays newest-first.
     */
    fun group(
        entries: List<LibraryEntry>,
        nowMillis: Long,
        zone: TimeZone = TimeZone.getDefault(),
    ): List<HistorySection> {
        if (entries.isEmpty()) return emptyList()
        val byGroup = LinkedHashMap<HistoryGroup, MutableList<LibraryEntry>>()
        for (entry in entries) {
            byGroup.getOrPut(groupOf(entry.timeMillis, nowMillis, zone)) { ArrayList() }.add(entry)
        }
        return HistoryGroup.values().mapNotNull { g -> byGroup[g]?.let { HistorySection(g, it) } }
    }

    private fun startOfDay(millis: Long, zone: TimeZone): Long {
        val c = Calendar.getInstance(zone)
        c.timeInMillis = millis
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun daysBefore(dayStartMillis: Long, days: Int, zone: TimeZone): Long {
        val c = Calendar.getInstance(zone)
        c.timeInMillis = dayStartMillis
        c.add(Calendar.DAY_OF_YEAR, -days)
        return c.timeInMillis
    }
}

/** Text of the History search (pure, unit-testable). */
object HistoryText {
    const val SEARCH_HINT = "Search history"
    const val SEARCH_CLEAR = "Clear search"
    const val SEARCH_EMPTY_TITLE = "No matching pages"
    const val SEARCH_EMPTY_BODY = "Try another word from the page title or the address."
    const val MAX_QUERY_LENGTH = 200
}
