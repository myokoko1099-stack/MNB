package com.mnb.browser.translation.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryTextTest {
    @Test fun normalizeIgnoresCaseAndSpacing() {
        assertEquals("lin feng", MemoryText.normalize("  Lin   FENG "))
    }

    @Test fun normalizeUnifiesUnicodeForms() {
        assertEquals(MemoryText.normalize("e\u0301"), MemoryText.normalize("\u00e9"))
    }

    @Test fun latinTermsMatchOnlyAsWholeWords() {
        assertTrue(MemoryText.containsTerm("al went home", "al"))
        assertFalse(MemoryText.containsTerm("she also left", "al"))
        assertTrue(MemoryText.containsTerm("said lin feng.", "lin feng"))
    }

    @Test fun nonLatinTermsMatchAsSubstrings() {
        assertTrue(MemoryText.containsTerm("林风看着苏梅", "苏梅"))
        assertTrue(MemoryText.containsTerm("မောင်မောင်က ပြောသည်", "မောင်မောင်"))
        assertFalse(MemoryText.containsTerm("林风", "苏梅"))
    }

    @Test fun emptyTermNeverMatches() {
        assertFalse(MemoryText.containsTerm("anything", ""))
        assertFalse(MemoryText.containsTerm("", "term"))
    }
}
