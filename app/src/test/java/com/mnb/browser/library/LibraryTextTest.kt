package com.mnb.browser.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryTextTest {
    @Test fun blankTitleShowsTheAddress() {
        assertEquals("https://a.com/", LibraryText.displayTitle("", "https://a.com/"))
        assertEquals("https://a.com/", LibraryText.displayTitle("   ", "https://a.com/"))
        assertEquals("https://a.com/", LibraryText.displayTitle(null, "https://a.com/"))
        assertEquals("Hello", LibraryText.displayTitle(" Hello ", "https://a.com/"))
    }

    @Test fun cleanTitleTrimsAndLimitsLength() {
        assertEquals("", LibraryText.cleanTitle(null))
        assertEquals("", LibraryText.cleanTitle("  \n "))
        assertEquals("Hi", LibraryText.cleanTitle("  Hi  "))
        assertEquals(LibraryText.MAX_TITLE_LENGTH, LibraryText.cleanTitle("x".repeat(1_000)).length)
    }

    @Test fun bookmarkMenuLabelFollowsState() {
        assertEquals(LibraryText.MENU_BOOKMARK_ADD, LibraryText.bookmarkMenuLabel(false))
        assertEquals(LibraryText.MENU_BOOKMARK_REMOVE, LibraryText.bookmarkMenuLabel(true))
    }

    @Test fun timeAgoUsesTheLargestUnit() {
        val now = 10_000_000_000L
        assertEquals("Just now", LibraryText.timeAgo(now, now))
        assertEquals("Just now", LibraryText.timeAgo(now, now + 5_000)) // clock skew: in the future
        assertEquals("Just now", LibraryText.timeAgo(now, now - 59_999))
        assertEquals("1 min ago", LibraryText.timeAgo(now, now - 60_000))
        assertEquals("59 min ago", LibraryText.timeAgo(now, now - 59 * 60_000L))
        assertEquals("1 h ago", LibraryText.timeAgo(now, now - 3_600_000L))
        assertEquals("23 h ago", LibraryText.timeAgo(now, now - 23 * 3_600_000L))
        assertEquals("1 d ago", LibraryText.timeAgo(now, now - 86_400_000L))
        assertEquals("30 d ago", LibraryText.timeAgo(now, now - 30 * 86_400_000L))
    }

    @Test fun entryDisplayTitleUsesTheSameRule() {
        assertEquals("https://a.com/", LibraryEntry(1, "https://a.com/", " ", 0).displayTitle)
        assertEquals("T", LibraryEntry(1, "https://a.com/", "T", 0).displayTitle)
    }
}
