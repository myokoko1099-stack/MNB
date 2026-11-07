package com.mnb.browser.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 2F: which background WebViews are unloaded, and when a tab counts as crash-looping. */
class LiveTabPolicyTest {

    @Test fun nothingIsUnloadedWhileTheLimitIsNotExceeded() {
        assertEquals(emptyList<String>(), LiveTabPolicy.tabsToUnload(listOf("a", "b", "c"), "c", maxLive = 3))
        assertEquals(emptyList<String>(), LiveTabPolicy.tabsToUnload(listOf("a"), "a", maxLive = 6))
        assertEquals(emptyList<String>(), LiveTabPolicy.tabsToUnload(emptyList(), null, maxLive = 6))
    }

    @Test fun theLeastRecentlyUsedTabsAreUnloadedFirst() {
        val live = listOf("a", "b", "c", "d", "e")
        assertEquals(listOf("a", "b"), LiveTabPolicy.tabsToUnload(live, "e", maxLive = 3))
    }

    @Test fun theActiveTabIsNeverUnloadedEvenWhenItIsTheOldest() {
        val live = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c"), LiveTabPolicy.tabsToUnload(live, "a", maxLive = 2))
    }

    @Test fun afterUnloadingExactlyTheLimitRemains() {
        val live = (1..10).map { "t$it" }
        val unload = LiveTabPolicy.tabsToUnload(live, "t4", maxLive = LiveTabPolicy.DEFAULT_MAX_LIVE)
        val remaining = live - unload.toSet()
        assertEquals(LiveTabPolicy.DEFAULT_MAX_LIVE, remaining.size)
        assertTrue("t4" in remaining)
    }

    @Test fun aLimitBelowOneStillKeepsTheActiveTab() {
        val live = listOf("a", "b", "c")
        for (limit in listOf(0, -5, 1)) {
            val unload = LiveTabPolicy.tabsToUnload(live, "b", limit)
            assertEquals(listOf("a", "c"), unload)
        }
    }

    @Test fun trimmingToOneKeepsOnlyTheActiveTab() {
        val live = listOf("a", "b", "c", "d")
        val unload = LiveTabPolicy.tabsToUnload(live, "c", maxLive = 1)
        assertEquals(listOf("a", "b", "d"), unload)
    }

    @Test fun duplicatesInTheInputAreCountedOnce() {
        assertEquals(listOf("a"), LiveTabPolicy.tabsToUnload(listOf("a", "b", "b", "c"), "c", maxLive = 2))
    }

    @Test fun anUnknownActiveIdDoesNotBreakTheResult() {
        assertEquals(listOf("a", "b"), LiveTabPolicy.tabsToUnload(listOf("a", "b", "c"), "zzz", maxLive = 1))
        assertEquals(listOf("a"), LiveTabPolicy.tabsToUnload(listOf("a", "b"), null, maxLive = 1))
    }

    // ---- CrashLoopGuard ---------------------------------------------------------------------

    @Test fun aSingleCrashIsNotALoop() {
        val guard = CrashLoopGuard()
        assertFalse(guard.recordCrash("a", 1_000))
    }

    @Test fun aSecondCrashInsideTheWindowIsALoop() {
        val guard = CrashLoopGuard(maxCrashes = 2, windowMillis = 60_000)
        assertFalse(guard.recordCrash("a", 1_000))
        assertTrue(guard.recordCrash("a", 30_000))
    }

    @Test fun aSecondCrashAfterTheWindowIsNotALoop() {
        val guard = CrashLoopGuard(maxCrashes = 2, windowMillis = 60_000)
        assertFalse(guard.recordCrash("a", 1_000))
        assertFalse(guard.recordCrash("a", 62_000))
    }

    @Test fun crashesOfDifferentTabsAreCountedSeparately() {
        val guard = CrashLoopGuard()
        assertFalse(guard.recordCrash("a", 1_000))
        assertFalse(guard.recordCrash("b", 2_000))
        assertTrue(guard.recordCrash("a", 3_000))
        assertTrue(guard.recordCrash("b", 4_000))
    }

    @Test fun aLoopKeepsBeingReportedWhileCrashesContinue() {
        val guard = CrashLoopGuard(maxCrashes = 2, windowMillis = 60_000)
        guard.recordCrash("a", 0)
        assertTrue(guard.recordCrash("a", 10_000))
        assertTrue(guard.recordCrash("a", 20_000))
        assertTrue(guard.recordCrash("a", 30_000))
    }

    @Test fun forgettingATabStartsItsCountAgain() {
        val guard = CrashLoopGuard()
        guard.recordCrash("a", 1_000)
        guard.forget("a")
        assertFalse(guard.recordCrash("a", 2_000))
    }

    @Test fun aHigherLimitNeedsMoreCrashes() {
        val guard = CrashLoopGuard(maxCrashes = 3, windowMillis = 60_000)
        assertFalse(guard.recordCrash("a", 0))
        assertFalse(guard.recordCrash("a", 1_000))
        assertTrue(guard.recordCrash("a", 2_000))
    }
}
