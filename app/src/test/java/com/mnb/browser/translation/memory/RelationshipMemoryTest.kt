package com.mnb.browser.translation.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelationshipMemoryTest : MemoryTestBase() {
    private fun pair() = confirmedCharacter(n1, "Lin Feng") to confirmedCharacter(n1, "Su Mei")

    @Test fun relationshipIsStoredBetweenTwoCharacters() {
        val (a, b) = pair()
        val result = repo.saveRelationship(n1, a.id, b.id, RelationshipType.FRIENDSHIP, "childhood friends", MemoryStatus.CONFIRMED, 2)
        assertEquals(RelationshipOutcome.CREATED, result.outcome)
        val rel = result.relationship!!
        assertEquals(RelationshipType.FRIENDSHIP, rel.type)
        assertEquals("childhood friends", rel.description)
        assertEquals(2, rel.startChapter)
        assertTrue(rel.isCurrent)
        assertEquals(listOf(rel), repo.listRelationships(n1, a.id))
        assertEquals(listOf(rel), repo.relationshipsBetween(n1, b.id, a.id))
    }

    @Test fun sameSymmetricRelationshipIsNotStoredTwiceEvenReversed() {
        val (a, b) = pair()
        repo.saveRelationship(n1, a.id, b.id, RelationshipType.FRIENDSHIP)
        val again = repo.saveRelationship(n1, b.id, a.id, RelationshipType.FRIENDSHIP)
        assertEquals(RelationshipOutcome.ALREADY_EXISTS, again.outcome)
        assertEquals(1, repo.listRelationships(n1).size)
    }

    @Test fun masterStudentKeepsItsDirection() {
        val (a, b) = pair()
        repo.saveRelationship(n1, a.id, b.id, RelationshipType.MASTER_STUDENT)
        val reversed = repo.saveRelationship(n1, b.id, a.id, RelationshipType.MASTER_STUDENT)
        assertEquals(RelationshipOutcome.CREATED, reversed.outcome)
        assertEquals(2, repo.listRelationships(n1).size)
    }

    @Test fun unknownOrIdenticalCharactersAreRejected() {
        val (a, _) = pair()
        assertEquals(RelationshipOutcome.CHARACTER_NOT_FOUND, repo.saveRelationship(n1, a.id, "missing", RelationshipType.FAMILY).outcome)
        assertEquals(RelationshipOutcome.SAME_CHARACTER, repo.saveRelationship(n1, a.id, a.id, RelationshipType.FAMILY).outcome)
        assertEquals(0, repo.listRelationships(n1).size)
    }

    @Test fun aChangedRelationshipIsUpdatedWithoutDeletingTheOldOne() {
        val (a, b) = pair()
        val old = repo.saveRelationship(n1, a.id, b.id, RelationshipType.FRIENDSHIP, "friends", MemoryStatus.CONFIRMED, 2).relationship!!
        val fresh = repo.updateRelationship(n1, old.id, RelationshipType.ENMITY, "betrayal", 30, MemoryStatus.CONFIRMED)!!

        assertEquals(RelationshipType.ENMITY, fresh.type)
        assertTrue(fresh.isCurrent)
        assertEquals(30, fresh.startChapter)

        val all = repo.listRelationships(n1, currentOnly = false)
        assertEquals(2, all.size)
        val closed = all.first { it.id == old.id }
        assertFalse(closed.isCurrent)
        assertEquals(30, closed.endChapter)
        assertEquals(RelationshipType.FRIENDSHIP, closed.type)

        assertEquals(listOf(fresh), repo.listRelationships(n1))
        val log = repo.history(n1, MemoryType.RELATIONSHIP)
        assertTrue(log.any { it.action == HistoryAction.SUPERSEDED && it.oldValue!!.startsWith("FRIENDSHIP") })
    }

    @Test fun updatingToTheSameValuesChangesNothing() {
        val (a, b) = pair()
        val old = repo.saveRelationship(n1, a.id, b.id, RelationshipType.FAMILY, "siblings").relationship!!
        val same = repo.updateRelationship(n1, old.id, RelationshipType.FAMILY, "Siblings", 9)
        assertEquals(old, same)
        assertEquals(1, repo.listRelationships(n1, currentOnly = false).size)
    }

    @Test fun onlyACurrentRelationshipCanBeUpdated() {
        val (a, b) = pair()
        val old = repo.saveRelationship(n1, a.id, b.id, RelationshipType.FRIENDSHIP).relationship!!
        assertNotNull(repo.updateRelationship(n1, old.id, RelationshipType.ENMITY, "", 5))
        assertNull(repo.updateRelationship(n1, old.id, RelationshipType.FAMILY, "", 6))
        assertNull(repo.updateRelationship(n1, "missing", RelationshipType.FAMILY, "", 6))
    }

    @Test fun deletingANovelRemovesItsRelationshipsAndCharacters() {
        val (a, b) = pair()
        repo.saveRelationship(n1, a.id, b.id, RelationshipType.FRIENDSHIP)
        db.novelDao().delete("n1")
        assertEquals(0, db.memoryRelationshipDao().listForNovel("n1").size)
        assertEquals(0, db.memoryCharacterDao().listForNovel("n1").size)
    }
}
