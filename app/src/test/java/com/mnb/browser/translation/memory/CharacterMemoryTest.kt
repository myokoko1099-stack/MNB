package com.mnb.browser.translation.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterMemoryTest : MemoryTestBase() {
    private fun linFeng(status: MemoryStatus = MemoryStatus.CONFIRMED) = CharacterDraft(
        originalName = "Lin Feng",
        myanmarName = "လင်ဖုန်း",
        aliases = listOf("Young Master Lin", "Feng"),
        gender = Gender.MALE,
        personality = "calm",
        role = "protagonist",
        speakingStyle = "formal",
        pronouns = "ငါ",
        firstAppearanceChapter = 1,
        notes = "from the north",
        status = status,
        sourceChapter = 1,
    )

    @Test fun creatingACharacterStoresEveryField() {
        val c = created(repo.saveCharacter(n1, linFeng()))
        assertEquals("id1", c.id)
        assertEquals(n1, c.novelId)
        assertEquals("Lin Feng", c.originalName)
        assertEquals("လင်ဖုန်း", c.myanmarName)
        assertEquals(listOf("Young Master Lin", "Feng"), c.aliases)
        assertEquals(Gender.MALE, c.gender)
        assertEquals("calm", c.personality)
        assertEquals("protagonist", c.role)
        assertEquals("formal", c.speakingStyle)
        assertEquals("ငါ", c.pronouns)
        assertEquals(1, c.firstAppearanceChapter)
        assertEquals("from the north", c.notes)
        assertEquals(MemoryStatus.CONFIRMED, c.status)
        assertEquals(1, c.sourceChapter)
    }

    @Test fun genderIsOnlyStoredWhenKnown() {
        val c = created(repo.saveCharacter(n1, CharacterDraft(originalName = "Ghost")))
        assertNull(c.gender)
        assertNull(c.myanmarName)
    }

    @Test fun storedCharacterCanBeRetrievedByIdNameAndList() {
        val c = created(repo.saveCharacter(n1, linFeng()))
        assertEquals(c, repo.getCharacter(n1, c.id))
        assertEquals(c, repo.findCharacter(n1, "Lin Feng"))
        assertEquals(listOf(c), repo.listCharacters(n1))
        assertNull(repo.getCharacter(n1, "missing"))
        assertNull(repo.findCharacter(n1, "Nobody"))
    }

    @Test fun sameCharacterUnderAnotherNameIsMergedNotDuplicated() {
        val first = created(repo.saveCharacter(n1, CharacterDraft(originalName = "Lin Feng", aliases = listOf("Feng"))))
        val result = repo.saveCharacter(
            n1,
            CharacterDraft(originalName = "Feng", myanmarName = "ဖုန်း", aliases = listOf("Master Lin"), sourceChapter = 4),
        )
        result as CharacterSaveResult.Merged
        assertEquals(first.id, result.character.id)
        assertEquals("ဖုန်း", result.character.myanmarName)
        assertTrue("Master Lin" in result.character.aliases)
        assertEquals(1, repo.listCharacters(n1).size)
        assertEquals(0, result.conflictsRecorded)
    }

    @Test fun mergeKeepsTheEarliestFirstAppearanceAndOriginalSource() {
        repo.saveCharacter(n1, CharacterDraft(originalName = "Su Mei", firstAppearanceChapter = 5, sourceChapter = 5))
        val merged = repo.saveCharacter(
            n1,
            CharacterDraft(originalName = "su mei", firstAppearanceChapter = 2, sourceChapter = 9),
        ) as CharacterSaveResult.Merged
        assertEquals(2, merged.character.firstAppearanceChapter)
        assertEquals(5, merged.character.sourceChapter)
    }

    @Test fun namesBelongingToTwoCharactersAreAmbiguousAndChangeNothing() {
        val a = confirmedCharacter(n1, "Alice")
        val b = confirmedCharacter(n1, "Bob")
        val result = repo.saveCharacter(n1, CharacterDraft(originalName = "Alice", aliases = listOf("Bob")))
        assertEquals(CharacterSaveResult.Ambiguous(listOf(a.id, b.id)), result)
        assertEquals(2, repo.listCharacters(n1).size)
        assertEquals(emptyList<String>(), repo.getCharacter(n1, a.id)!!.aliases)
    }

    @Test fun aliasMatchingIgnoresCaseSpacingAndOriginalNameIsAnAlias() {
        val c = created(repo.saveCharacter(n1, linFeng()))
        assertEquals(c.id, repo.findCharacter(n1, "  YOUNG   master lin ")?.id)
        assertEquals(c.id, repo.findCharacter(n1, "feng")?.id)
        assertEquals(c.id, repo.findCharacter(n1, "lin feng")?.id)
    }

    @Test fun addAliasReportsAddedPresentAndOwnedByAnother() {
        val a = confirmedCharacter(n1, "Alice")
        val b = confirmedCharacter(n1, "Bob")
        assertEquals(AliasOutcome.ADDED, repo.addAlias(n1, a.id, "Ally").outcome)
        assertEquals(AliasOutcome.ALREADY_PRESENT, repo.addAlias(n1, a.id, "ally").outcome)
        val clash = repo.addAlias(n1, b.id, "Ally")
        assertEquals(AliasOutcome.BELONGS_TO_OTHER_CHARACTER, clash.outcome)
        assertEquals(a.id, clash.ownerCharacterId)
        assertEquals(AliasOutcome.CHARACTER_NOT_FOUND, repo.addAlias(n1, "missing", "X").outcome)
        assertEquals(AliasOutcome.BLANK, repo.addAlias(n1, a.id, "  ").outcome)
        assertEquals(listOf("Ally"), repo.getCharacter(n1, a.id)!!.aliases)
    }

    @Test fun sourceChapterIsTracked() {
        val c = created(repo.saveCharacter(n1, CharacterDraft(originalName = "Wang Hu", sourceChapter = 7)))
        assertEquals(7, repo.getCharacter(n1, c.id)!!.sourceChapter)
    }

    @Test fun newCharactersAreUncertainUntilConfirmed() {
        val c = created(repo.saveCharacter(n1, CharacterDraft(originalName = "Mystery Man", myanmarName = "လျှို့ဝှက်")))
        assertEquals(MemoryStatus.UNCERTAIN, c.status)
        assertTrue(repo.confirmCharacter(n1, c.id))
        assertEquals(MemoryStatus.CONFIRMED, repo.getCharacter(n1, c.id)!!.status)
        assertTrue(!repo.confirmCharacter(n1, "missing"))
    }

    @Test fun anUncertainDraftNeverDowngradesAConfirmedCharacter() {
        val c = confirmedCharacter(n1, "Alice")
        val merged = repo.saveCharacter(n1, CharacterDraft(originalName = "Alice", status = MemoryStatus.UNCERTAIN))
            as CharacterSaveResult.Merged
        assertEquals(MemoryStatus.CONFIRMED, merged.character.status)
        assertEquals(c.id, merged.character.id)
    }

    @Test fun blankNamesAreRejected() {
        try {
            CharacterDraft(originalName = "  ")
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertNotNull(expected.message)
        }
    }
}
