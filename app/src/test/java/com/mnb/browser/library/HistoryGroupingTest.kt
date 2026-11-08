package com.mnb.browser.library

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryGroupingTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val yangon = TimeZone.getTimeZone("Asia/Yangon")

    private fun at(zone: TimeZone, y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Long {
        val c = Calendar.getInstance(zone)
        c.clear()
        c.set(y, m - 1, d, h, min, 0)
        return c.timeInMillis
    }

    private fun entry(id: Long, t: Long) = LibraryEntry(id, "https://e.test/$id", "t$id", t)

    // "now" is 8 Nov 2026, 15:00 UTC.
    private val now = at(utc, 2026, 11, 8, 15)

    private fun groupOf(t: Long) = HistoryGrouping.groupOf(t, now, utc)

    @Test fun todayStartsAtMidnight() {
        assertEquals(HistoryGroup.TODAY, groupOf(at(utc, 2026, 11, 8, 0, 0)))
        assertEquals(HistoryGroup.TODAY, groupOf(now))
        assertEquals(HistoryGroup.YESTERDAY, groupOf(at(utc, 2026, 11, 7, 23, 59)))
    }

    @Test fun aTimeInTheFutureCountsAsToday() {
        assertEquals(HistoryGroup.TODAY, groupOf(now + 5 * 24 * 3_600_000L))
    }

    @Test fun yesterdayIsTheWholePreviousDay() {
        assertEquals(HistoryGroup.YESTERDAY, groupOf(at(utc, 2026, 11, 7, 0, 0)))
        assertEquals(HistoryGroup.LAST_7_DAYS, groupOf(at(utc, 2026, 11, 6, 23, 59)))
    }

    @Test fun lastSevenDaysReachBackSevenDaysFromToday() {
        assertEquals(HistoryGroup.LAST_7_DAYS, groupOf(at(utc, 2026, 11, 1, 0, 0)))
        assertEquals(HistoryGroup.LAST_30_DAYS, groupOf(at(utc, 2026, 10, 31, 23, 59)))
    }

    @Test fun lastThirtyDaysReachBackThirtyDaysFromToday() {
        assertEquals(HistoryGroup.LAST_30_DAYS, groupOf(at(utc, 2026, 10, 9, 0, 0)))
        assertEquals(HistoryGroup.OLDER, groupOf(at(utc, 2026, 10, 8, 23, 59)))
        assertEquals(HistoryGroup.OLDER, groupOf(0L))
    }

    @Test fun midnightFollowsTheGivenTimeZone() {
        val localNow = at(yangon, 2026, 11, 8, 0, 30)
        assertEquals(HistoryGroup.TODAY, HistoryGrouping.groupOf(at(yangon, 2026, 11, 8, 0, 10), localNow, yangon))
        assertEquals(HistoryGroup.YESTERDAY, HistoryGrouping.groupOf(at(yangon, 2026, 11, 7, 23, 50), localNow, yangon))
    }

    @Test fun emptyListGivesNoSections() {
        assertTrue(HistoryGrouping.group(emptyList(), now, utc).isEmpty())
    }

    @Test fun onlyGroupsWithEntriesAreReturnedInDisplayOrder() {
        val entries = listOf(
            entry(1, at(utc, 2026, 11, 8, 14)),
            entry(2, at(utc, 2026, 11, 8, 9)),
            entry(3, at(utc, 2026, 11, 3, 9)),
            entry(4, at(utc, 2026, 8, 1, 9)),
        )
        val sections = HistoryGrouping.group(entries, now, utc)
        assertEquals(listOf(HistoryGroup.TODAY, HistoryGroup.LAST_7_DAYS, HistoryGroup.OLDER), sections.map { it.group })
        assertEquals(listOf(1L, 2L), sections[0].entries.map { it.id })
        assertEquals(listOf(3L), sections[1].entries.map { it.id })
        assertEquals(listOf(4L), sections[2].entries.map { it.id })
    }

    @Test fun everyEntryAppearsExactlyOnceAndKeepsItsOrder() {
        val entries = (1L..40L).map { entry(it, now - it * 20L * 3_600_000L) } // newest first
        val sections = HistoryGrouping.group(entries, now, utc)
        assertEquals(entries.map { it.id }, sections.flatMap { s -> s.entries.map { it.id } })
    }

    @Test fun groupingWorksOnASearchResult() {
        val all = listOf(
            entry(1, at(utc, 2026, 11, 8, 10)),
            entry(2, at(utc, 2026, 11, 7, 10)),
            entry(3, at(utc, 2026, 9, 7, 10)),
        )
        val filtered = all.filter { it.id != 2L }
        val sections = HistoryGrouping.group(filtered, now, utc)
        assertEquals(listOf(HistoryGroup.TODAY, HistoryGroup.OLDER), sections.map { it.group })
    }
}
