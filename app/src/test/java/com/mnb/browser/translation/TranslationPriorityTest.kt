package com.mnb.browser.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationPriorityTest {
    @Test fun thereAreExactlyThreeLevels() {
        assertEquals(setOf("HIGH", "NORMAL", "LOW"), TranslationPriority.values().map { it.name }.toSet())
    }

    @Test fun highOutranksNormalAndNormalOutranksLow() {
        assertTrue(TranslationPriority.HIGH.rank > TranslationPriority.NORMAL.rank)
        assertTrue(TranslationPriority.NORMAL.rank > TranslationPriority.LOW.rank)
    }

    @Test fun defaultPriorityIsNormal() {
        assertEquals(TranslationPriority.NORMAL, TranslationPriority.DEFAULT)
    }

    @Test fun rankRoundTripsForEveryLevel() {
        for (p in TranslationPriority.values()) assertEquals(p, TranslationPriority.fromRank(p.rank))
    }

    @Test fun unknownRankFallsBackToNormal() {
        assertEquals(TranslationPriority.NORMAL, TranslationPriority.fromRank(99))
        assertEquals(TranslationPriority.NORMAL, TranslationPriority.fromRank(-1))
    }

    @Test fun jobDefaultsToNormalPriority() {
        val job = TranslationJob(jobId = 0L, chapterId = ChapterId("c1"), totalChunks = 0, createdAtMillis = 1L)
        assertEquals(TranslationPriority.NORMAL, job.priority)
    }
}
