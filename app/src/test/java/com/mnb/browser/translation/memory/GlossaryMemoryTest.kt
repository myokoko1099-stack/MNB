package com.mnb.browser.translation.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GlossaryMemoryTest : MemoryTestBase() {
    @Test fun termsAreStoredWithCategoryAndSourceChapter() {
        val result = repo.saveGlossary(n1, "Azure Sect", "ကောင်းကင်ပြာဂိုဏ်း", GlossaryCategory.ORGANIZATION_NAME, MemoryStatus.CONFIRMED, 3)
        assertEquals(SaveOutcome.CREATED, result.outcome)
        val stored = repo.getGlossary(n1, "Azure Sect")!!
        assertEquals("ကောင်းကင်ပြာဂိုဏ်း", stored.translatedTerm)
        assertEquals(GlossaryCategory.ORGANIZATION_NAME, stored.category)
        assertEquals(MemoryStatus.CONFIRMED, stored.status)
        assertEquals(3, stored.sourceChapter)
        assertEquals(listOf(stored), repo.listGlossary(n1))
        assertEquals(emptyList<GlossaryMemory>(), repo.listGlossary(n1, GlossaryCategory.SKILL))
    }

    @Test fun aTermKeepsTheSameTranslationEverywhere() {
        repo.saveGlossary(n1, "Jade City", "ကျောက်စိမ်းမြို့", GlossaryCategory.PLACE_NAME, MemoryStatus.CONFIRMED, 1)
        assertEquals("ကျောက်စိမ်းမြို့", repo.translationFor(n1, "jade   city"))
        assertEquals("ကျောက်စိမ်းမြို့", repo.translationFor(n1, "JADE CITY"))
        assertNull(repo.translationFor(n1, "Unknown Place"))
    }

    @Test fun savingTheSameTermAndTranslationAgainChangesNothing() {
        repo.saveGlossary(n1, "Qi", "ချီ", GlossaryCategory.SPECIAL_TERM, MemoryStatus.CONFIRMED, 1)
        val again = repo.saveGlossary(n1, "qi", "ချီ", GlossaryCategory.SPECIAL_TERM, MemoryStatus.CONFIRMED, 6)
        assertEquals(SaveOutcome.UNCHANGED, again.outcome)
        assertEquals(1, repo.listGlossary(n1).size)
        assertEquals(1, repo.getGlossary(n1, "Qi")!!.sourceChapter)
    }

    @Test fun aDifferentTranslationNeverReplacesTheStoredOne() {
        repo.saveGlossary(n1, "Qi", "ချီ", GlossaryCategory.SPECIAL_TERM, MemoryStatus.CONFIRMED, 1)
        val clash = repo.saveGlossary(n1, "Qi", "စွမ်းအင်", GlossaryCategory.SPECIAL_TERM, MemoryStatus.CONFIRMED, 8)
        assertEquals(SaveOutcome.CONFLICT_RECORDED, clash.outcome)
        assertNotNull(clash.conflictId)
        assertEquals("ချီ", repo.translationFor(n1, "Qi"))
        assertEquals("ချီ", clash.entry.translatedTerm)
    }

    @Test fun uncertainTermCanBeConfirmedWhenTheTranslationMatches() {
        repo.saveGlossary(n1, "Qi", "ချီ", GlossaryCategory.SPECIAL_TERM)
        assertEquals(MemoryStatus.UNCERTAIN, repo.getGlossary(n1, "Qi")!!.status)
        val confirmed = repo.saveGlossary(n1, "Qi", "ချီ", GlossaryCategory.SPECIAL_TERM, MemoryStatus.CONFIRMED)
        assertEquals(SaveOutcome.UPDATED, confirmed.outcome)
        assertEquals(MemoryStatus.CONFIRMED, repo.getGlossary(n1, "Qi")!!.status)
    }

    @Test fun confirmGlossaryPromotesAnUncertainTerm() {
        repo.saveGlossary(n1, "Qi", "ချီ", GlossaryCategory.SPECIAL_TERM)
        assertEquals(true, repo.confirmGlossary(n1, "QI"))
        assertEquals(MemoryStatus.CONFIRMED, repo.getGlossary(n1, "Qi")!!.status)
        assertEquals(false, repo.confirmGlossary(n1, "missing"))
    }

    @Test fun blankTermsAreRejected() {
        try {
            repo.saveGlossary(n1, " ", "x", GlossaryCategory.TITLE)
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertEquals(0, repo.listGlossary(n1).size)
        }
    }
}
