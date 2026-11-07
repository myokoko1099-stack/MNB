package com.mnb.browser.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitDeduplicatorTest {
    private val window = 1_000L

    @Test fun firstVisitIsAccepted() {
        assertTrue(VisitDeduplicator(window).accept("t1", "https://a.com/", 0))
    }

    @Test fun sameTabSameUrlInsideWindowIsRejected() {
        val d = VisitDeduplicator(window)
        assertTrue(d.accept("t1", "https://a.com/", 0))
        assertFalse(d.accept("t1", "https://a.com/", 1))
        assertFalse(d.accept("t1", "https://a.com/", window - 1))
    }

    @Test fun sameUrlAfterWindowIsAcceptedAgain() {
        val d = VisitDeduplicator(window)
        assertTrue(d.accept("t1", "https://a.com/", 0))
        assertTrue(d.accept("t1", "https://a.com/", window))
    }

    @Test fun repeatedCallbacksDoNotExtendTheWindow() {
        val d = VisitDeduplicator(window)
        assertTrue(d.accept("t1", "https://a.com/", 0))
        assertFalse(d.accept("t1", "https://a.com/", 900))
        // 1,100 ms after the stored visit: a new visit, even though the last callback was 200 ms ago.
        assertTrue(d.accept("t1", "https://a.com/", 1_100))
    }

    @Test fun differentUrlIsAccepted() {
        val d = VisitDeduplicator(window)
        assertTrue(d.accept("t1", "https://a.com/", 0))
        assertTrue(d.accept("t1", "https://b.com/", 1))
    }

    @Test fun returningToAnEarlierPageIsAccepted() {
        val d = VisitDeduplicator(window)
        assertTrue(d.accept("t1", "https://a.com/", 0))
        assertTrue(d.accept("t1", "https://b.com/", 1))
        assertTrue(d.accept("t1", "https://a.com/", 2))
    }

    @Test fun differentTabsAreIndependent() {
        val d = VisitDeduplicator(window)
        assertTrue(d.accept("t1", "https://a.com/", 0))
        assertTrue(d.accept("t2", "https://a.com/", 1))
        assertFalse(d.accept("t2", "https://a.com/", 2))
    }

    @Test fun fragmentOnlyChangeIsTheSamePage() {
        val d = VisitDeduplicator(window)
        assertTrue(d.accept("t1", "https://a.com/p", 0))
        assertFalse(d.accept("t1", "https://a.com/p#section", 1))
    }

    @Test fun clockGoingBackwardsDoesNotBlockVisits() {
        val d = VisitDeduplicator(window)
        assertTrue(d.accept("t1", "https://a.com/", 5_000))
        assertTrue(d.accept("t1", "https://a.com/", 4_000))
    }

    @Test fun forgetClosedTabAllowsTheSamePageAgain() {
        val d = VisitDeduplicator(window)
        assertTrue(d.accept("t1", "https://a.com/", 0))
        d.forget("t1")
        assertTrue(d.accept("t1", "https://a.com/", 1))
    }
}
