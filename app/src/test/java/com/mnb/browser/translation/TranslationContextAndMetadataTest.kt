package com.mnb.browser.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationContextAndMetadataTest {
    private val novel = NovelId("n1")

    @Test fun contextDefaultsAreEmpty() {
        val ctx = TranslationContext(novel)
        assertTrue(ctx.isEmpty)
        assertEquals("", ctx.storyContext)
        assertEquals("", ctx.instructions)
    }

    @Test fun contextWithDataIsNotEmptyAndFindsGlossaryTerms() {
        val ctx = TranslationContext(
            novelId = novel,
            characters = listOf(CharacterInfo("林风", translatedName = "လင်ဖုန်း")),
            glossary = listOf(GlossaryEntry("灵气", "စိတ်ဓာတ်စွမ်းအင်")),
        )
        assertFalse(ctx.isEmpty)
        assertEquals("စိတ်ဓာတ်စွမ်းအင်", ctx.findGlossary("灵气")?.translatedTerm)
        assertNull(ctx.findGlossary("missing"))
    }

    @Test fun contextRejectsBadEntries() {
        assertThrows(IllegalArgumentException::class.java) { CharacterInfo(" ") }
        assertThrows(IllegalArgumentException::class.java) { CharacterInfo("A", translatedName = " ") }
        assertThrows(IllegalArgumentException::class.java) { GlossaryEntry("", "x") }
        assertThrows(IllegalArgumentException::class.java) { GlossaryEntry("x", " ") }
    }

    @Test fun duplicateGlossaryTermsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            TranslationContext(novel, glossary = listOf(GlossaryEntry("a", "x"), GlossaryEntry("a", "y")))
        }
    }

    @Test fun metadataDefaults() {
        val m = TranslationMetadata("openai", createdAtMillis = 100L)
        assertEquals(100L, m.updatedAtMillis)
        assertNull(m.modelId)
        assertEquals(1, m.translationVersion)
        assertFalse(m.isMock)
    }

    @Test fun metadataRejectsBadData() {
        assertThrows(IllegalArgumentException::class.java) { TranslationMetadata(" ", 0L) }
        assertThrows(IllegalArgumentException::class.java) { TranslationMetadata("p", 0L, modelId = " ") }
        assertThrows(IllegalArgumentException::class.java) { TranslationMetadata("p", 0L, translationVersion = 0) }
        assertThrows(IllegalArgumentException::class.java) { TranslationMetadata("p", -1L) }
        assertThrows(IllegalArgumentException::class.java) { TranslationMetadata("p", 100L, updatedAtMillis = 50L) }
    }

    @Test fun touchedMovesUpdatedDateOnly() {
        val m = TranslationMetadata("p", 100L).touched(200L)
        assertEquals(100L, m.createdAtMillis)
        assertEquals(200L, m.updatedAtMillis)
        assertThrows(IllegalArgumentException::class.java) { m.touched(10L) }
    }
}
