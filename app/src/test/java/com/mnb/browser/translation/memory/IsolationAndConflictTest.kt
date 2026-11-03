package com.mnb.browser.translation.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IsolationAndConflictTest : MemoryTestBase() {
    // ---- novel isolation

    @Test fun memoryOfOneNovelIsInvisibleToAnother() {
        val c = confirmedCharacter(n1, "Lin Feng", aliases = listOf("Feng"))
        repo.saveGlossary(n1, "Qi", "ချီ", GlossaryCategory.SPECIAL_TERM, MemoryStatus.CONFIRMED)
        repo.saveWorld(n1, WorldKind.CITY, "Jade City", "x")
        repo.addStory(n1, StoryKind.EVENT, "something", 1)
        repo.saveStyle(n1, StyleMemory(writingStyle = "x"))

        assertEquals(emptyList<CharacterMemory>(), repo.listCharacters(n2))
        assertNull(repo.findCharacter(n2, "Lin Feng"))
        assertNull(repo.getCharacter(n2, c.id))
        assertNull(repo.translationFor(n2, "Qi"))
        assertEquals(0, repo.listWorld(n2).size)
        assertEquals(0, repo.listStory(n2).size)
        assertNull(repo.getStyle(n2))
    }

    @Test fun theSameNameCanBeADifferentCharacterInAnotherNovel() {
        val a = confirmedCharacter(n1, "Lin Feng")
        val b = confirmedCharacter(n2, "Lin Feng")
        assertTrue(a.id != b.id)
        assertEquals(a.id, repo.findCharacter(n1, "Lin Feng")!!.id)
        assertEquals(b.id, repo.findCharacter(n2, "Lin Feng")!!.id)
    }

    @Test fun theSameTermCanHaveDifferentTranslationsInDifferentNovels() {
        repo.saveGlossary(n1, "Qi", "ချီ", GlossaryCategory.SPECIAL_TERM)
        val other = repo.saveGlossary(n2, "Qi", "စွမ်းအင်", GlossaryCategory.SPECIAL_TERM)
        assertEquals(SaveOutcome.CREATED, other.outcome)
        assertEquals("ချီ", repo.translationFor(n1, "Qi"))
        assertEquals("စွမ်းအင်", repo.translationFor(n2, "Qi"))
    }

    @Test fun relationshipsCannotLinkCharactersOfAnotherNovel() {
        val a = confirmedCharacter(n1, "Alice")
        val b = confirmedCharacter(n2, "Bob")
        assertEquals(RelationshipOutcome.CHARACTER_NOT_FOUND, repo.saveRelationship(n1, a.id, b.id, RelationshipType.FRIENDSHIP).outcome)
    }

    @Test fun conflictsAreAlsoSeparatedPerNovel() {
        repo.saveGlossary(n1, "Qi", "ချီ", GlossaryCategory.SPECIAL_TERM)
        repo.saveGlossary(n1, "Qi", "အခြား", GlossaryCategory.SPECIAL_TERM)
        assertEquals(1, repo.listConflicts(n1).size)
        assertEquals(0, repo.listConflicts(n2).size)
    }

    // ---- conflicting memory

    @Test fun aConflictKeepsTheOldValueAndRecordsTheProposal() {
        repo.saveGlossary(n1, "Qi", "ချီ", GlossaryCategory.SPECIAL_TERM, MemoryStatus.CONFIRMED, 1)
        val clash = repo.saveGlossary(n1, "Qi", "စွမ်းအင်", GlossaryCategory.SPECIAL_TERM, MemoryStatus.CONFIRMED, 8)
        val conflict = repo.listConflicts(n1).single()
        assertEquals(clash.conflictId, conflict.id)
        assertEquals(MemoryType.GLOSSARY, conflict.type)
        assertEquals("translatedTerm", conflict.fieldName)
        assertEquals("ချီ", conflict.existingValue)
        assertEquals("စွမ်းအင်", conflict.proposedValue)
        assertEquals(8, conflict.proposedSourceChapter)
        assertEquals(ConflictStatus.OPEN, conflict.status)
        assertEquals("ချီ", repo.translationFor(n1, "Qi"))
        assertTrue(repo.history(n1).any { it.action == HistoryAction.CONFLICT_RECORDED })
    }

    @Test fun theSameProposalIsNotRecordedTwice() {
        repo.saveGlossary(n1, "Qi", "ချီ", GlossaryCategory.SPECIAL_TERM)
        val first = repo.saveGlossary(n1, "Qi", "အခြား", GlossaryCategory.SPECIAL_TERM)
        val second = repo.saveGlossary(n1, "Qi", "အခြား", GlossaryCategory.SPECIAL_TERM)
        assertEquals(first.conflictId, second.conflictId)
        assertEquals(1, repo.listConflicts(n1).size)
    }

    @Test fun acceptingAConflictReplacesTheValueAndKeepsTheOldOneInHistory() {
        repo.saveGlossary(n1, "Qi", "ချီ", GlossaryCategory.SPECIAL_TERM, MemoryStatus.CONFIRMED)
        val id = repo.saveGlossary(n1, "Qi", "စွမ်းအင်", GlossaryCategory.SPECIAL_TERM).conflictId!!
        assertTrue(repo.resolveConflict(n1, id, acceptProposed = true))
        assertEquals("စွမ်းအင်", repo.translationFor(n1, "Qi"))
        assertEquals(0, repo.listConflicts(n1).size)
        assertEquals(ConflictStatus.ACCEPTED, repo.listConflicts(n1, openOnly = false).single().status)
        val superseded = repo.history(n1, MemoryType.GLOSSARY).single { it.action == HistoryAction.SUPERSEDED }
        assertEquals("ချီ", superseded.oldValue)
        assertEquals("စွမ်းအင်", superseded.newValue)
    }

    @Test fun rejectingAConflictKeepsTheStoredValue() {
        repo.saveGlossary(n1, "Qi", "ချီ", GlossaryCategory.SPECIAL_TERM, MemoryStatus.CONFIRMED)
        val id = repo.saveGlossary(n1, "Qi", "စွမ်းအင်", GlossaryCategory.SPECIAL_TERM).conflictId!!
        assertTrue(repo.resolveConflict(n1, id, acceptProposed = false))
        assertEquals("ချီ", repo.translationFor(n1, "Qi"))
        assertEquals(ConflictStatus.REJECTED, repo.listConflicts(n1, openOnly = false).single().status)
        assertFalse(repo.resolveConflict(n1, id, acceptProposed = true))
    }

    @Test fun unknownOrOtherNovelConflictsCannotBeResolved() {
        repo.saveGlossary(n1, "Qi", "ချီ", GlossaryCategory.SPECIAL_TERM)
        val id = repo.saveGlossary(n1, "Qi", "အခြား", GlossaryCategory.SPECIAL_TERM).conflictId!!
        assertFalse(repo.resolveConflict(n1, 9999L, true))
        assertFalse(repo.resolveConflict(n2, id, true))
        assertEquals("ချီ", repo.translationFor(n1, "Qi"))
    }

    @Test fun conflictingCharacterNamesAreRecordedAndCanBeAccepted() {
        val c = created(repo.saveCharacter(n1, CharacterDraft(originalName = "Lin Feng", myanmarName = "လင်ဖုန်း")))
        val merged = repo.saveCharacter(n1, CharacterDraft(originalName = "Lin Feng", myanmarName = "လင်ဖေါင်း", sourceChapter = 5))
            as CharacterSaveResult.Merged
        assertEquals(1, merged.conflictsRecorded)
        assertEquals("လင်ဖုန်း", merged.character.myanmarName)

        val conflict = repo.listConflicts(n1).single()
        assertEquals(MemoryType.CHARACTER, conflict.type)
        assertEquals("myanmarName", conflict.fieldName)
        assertTrue(repo.resolveConflict(n1, conflict.id, true))
        assertEquals("လင်ဖေါင်း", repo.getCharacter(n1, c.id)!!.myanmarName)
    }

    @Test fun changesAreLoggedInHistory() {
        val c = confirmedCharacter(n1, "Alice")
        repo.confirmCharacter(n1, c.id)
        val log = repo.history(n1, MemoryType.CHARACTER)
        assertEquals(HistoryAction.CREATED, log.first().action)
        assertEquals(c.id, log.first().memoryId)
        assertNotNull(log.first().atMillis)
    }

    // ---- uncertain information

    @Test fun uncertainFactsStayUncertainUntilConfirmed() {
        repo.saveGlossary(n1, "Qi", "ချီ", GlossaryCategory.SPECIAL_TERM)
        repo.saveWorld(n1, WorldKind.CITY, "Jade City", "x")
        repo.addStory(n1, StoryKind.EVENT, "rumour", 2)
        assertEquals(MemoryStatus.UNCERTAIN, repo.getGlossary(n1, "Qi")!!.status)
        assertEquals(MemoryStatus.UNCERTAIN, repo.findWorld(n1, WorldKind.CITY, "Jade City")!!.status)
        assertEquals(MemoryStatus.UNCERTAIN, repo.listStory(n1).single().status)
    }
}
