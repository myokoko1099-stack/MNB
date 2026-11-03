package com.mnb.browser.translation.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryWorldStyleMemoryTest : MemoryTestBase() {
    // ---- story

    @Test fun storyFactsAreStoredWithTheirSourceChapter() {
        val a = confirmedCharacter(n1, "Lin Feng")
        val s = repo.addStory(n1, StoryKind.PROMISE, "Lin Feng promises to return", 12, listOf(a.id), MemoryStatus.CONFIRMED)
        assertEquals(12, s.sourceChapter)
        assertEquals(StoryKind.PROMISE, s.kind)
        assertEquals(listOf(a.id), s.characterIds)
        assertFalse(s.resolved)
        assertEquals(listOf(s), repo.listStory(n1))
    }

    @Test fun storyIsListedInChapterOrderAndCanBeFiltered() {
        repo.addStory(n1, StoryKind.EVENT, "late event", 20)
        repo.addStory(n1, StoryKind.EVENT, "early event", 2)
        repo.addStory(n1, StoryKind.DISCOVERY, "middle discovery", 10)
        assertEquals(listOf("early event", "middle discovery", "late event"), repo.listStory(n1).map { it.summary })
        assertEquals(listOf("middle discovery"), repo.listStory(n1, kind = StoryKind.DISCOVERY).map { it.summary })
        assertEquals(listOf("early event"), repo.listStory(n1, beforeChapter = 10).map { it.summary })
    }

    @Test fun theSameStoryFactIsNotStoredTwice() {
        val first = repo.addStory(n1, StoryKind.EVENT, "The gate fell", 5)
        val second = repo.addStory(n1, StoryKind.EVENT, "  the GATE fell ", 5)
        assertEquals(first.id, second.id)
        assertEquals(1, repo.listStory(n1).size)
    }

    @Test fun unresolvedQuestionsCanBeMarkedResolvedWithoutBeingDeleted() {
        val q = repo.addStory(n1, StoryKind.UNRESOLVED_QUESTION, "Who sent the letter?", 3)
        assertTrue(repo.markStoryResolved(n1, q.id))
        assertTrue(repo.listStory(n1).single().resolved)
        assertFalse(repo.markStoryResolved(n1, "missing"))
    }

    @Test fun storyRejectsUnknownCharactersAndInvalidInput() {
        try {
            repo.addStory(n1, StoryKind.EVENT, "x", 1, listOf("missing"))
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertEquals(0, repo.listStory(n1).size)
        }
        try {
            repo.addStory(n1, StoryKind.EVENT, " ", 1)
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertEquals(0, repo.listStory(n1).size)
        }
    }

    // ---- world

    @Test fun worldKnowledgeCanBeStoredAndRetrieved() {
        repo.saveWorld(n1, WorldKind.KINGDOM, "Kingdom of Yan", "A northern kingdom", MemoryStatus.CONFIRMED, 1)
        repo.saveWorld(n1, WorldKind.WORLD_RULE, "Qi Limit", "No one exceeds the tenth realm", MemoryStatus.CONFIRMED, 4)
        val kingdom = repo.findWorld(n1, WorldKind.KINGDOM, "kingdom of  yan")!!
        assertEquals("A northern kingdom", kingdom.description)
        assertEquals(1, kingdom.sourceChapter)
        assertEquals(2, repo.listWorld(n1).size)
        assertEquals(listOf("Qi Limit"), repo.listWorld(n1, WorldKind.WORLD_RULE).map { it.name })
        assertNull(repo.findWorld(n1, WorldKind.CITY, "Kingdom of Yan"))
    }

    @Test fun worldEntryWithADifferentDescriptionBecomesAConflict() {
        repo.saveWorld(n1, WorldKind.CITY, "Jade City", "capital of Yan", MemoryStatus.CONFIRMED, 1)
        val clash = repo.saveWorld(n1, WorldKind.CITY, "Jade City", "a port town", MemoryStatus.CONFIRMED, 9)
        assertEquals(SaveOutcome.CONFLICT_RECORDED, clash.outcome)
        assertEquals("capital of Yan", repo.findWorld(n1, WorldKind.CITY, "Jade City")!!.description)
        val blankFill = repo.saveWorld(n1, WorldKind.CITY, "Jade City", "", MemoryStatus.CONFIRMED, 9)
        assertEquals(SaveOutcome.UNCHANGED, blankFill.outcome)
    }

    @Test fun aBlankWorldDescriptionCanBeFilledLater() {
        repo.saveWorld(n1, WorldKind.LAW, "Law of Debt", "")
        val filled = repo.saveWorld(n1, WorldKind.LAW, "Law of Debt", "Debts pass to heirs", sourceChapter = 6)
        assertEquals(SaveOutcome.UPDATED, filled.outcome)
        assertEquals("Debts pass to heirs", repo.findWorld(n1, WorldKind.LAW, "Law of Debt")!!.description)
    }

    // ---- style

    @Test fun translationStyleCanBeStoredAndRetrieved() {
        assertNull(repo.getStyle(n1))
        val style = StyleMemory(
            writingStyle = "literary Myanmar",
            tone = ToneLevel.FORMAL,
            dialogueStyle = "short, direct",
            pronounPreferences = "ကျွန်တော် for the hero",
            namingConventions = "keep Chinese names in Myanmar transliteration",
            translationPreferences = "do not shorten",
        )
        repo.saveStyle(n1, style)
        assertEquals(style, repo.getStyle(n1))
    }

    @Test fun savingStyleAgainReplacesItAndIsLogged() {
        repo.saveStyle(n1, StyleMemory(tone = ToneLevel.FORMAL, writingStyle = "a"))
        repo.saveStyle(n1, StyleMemory(tone = ToneLevel.INFORMAL, writingStyle = "b"))
        assertEquals(ToneLevel.INFORMAL, repo.getStyle(n1)!!.tone)
        val log = repo.history(n1, MemoryType.STYLE)
        assertEquals(listOf(HistoryAction.CREATED, HistoryAction.UPDATED), log.map { it.action })
        assertEquals("FORMAL: a", log[1].oldValue)
    }
}
