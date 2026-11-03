package com.mnb.browser.translation.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert

/* Blocking DAOs for the Context Memory tables, like the rest of the translation package. */

@Dao
interface MemoryCharacterDao {
    @Insert fun insert(row: MemoryCharacterEntity)

    @Update fun update(row: MemoryCharacterEntity): Int

    @Query("SELECT * FROM memory_characters WHERE novelId = :novelId AND characterId = :characterId")
    fun get(novelId: String, characterId: String): MemoryCharacterEntity?

    @Query("SELECT * FROM memory_characters WHERE novelId = :novelId ORDER BY createdAtMillis, characterId")
    fun listForNovel(novelId: String): List<MemoryCharacterEntity>
}

@Dao
interface MemoryAliasDao {
    /** Strict insert: a name already owned in this novel throws, so callers check [findOwner] first. */
    @Insert fun insert(row: MemoryAliasEntity): Long

    @Query("SELECT * FROM memory_aliases WHERE novelId = :novelId AND normalizedAlias = :normalizedAlias")
    fun findOwner(novelId: String, normalizedAlias: String): MemoryAliasEntity?

    @Query("SELECT * FROM memory_aliases WHERE characterId = :characterId ORDER BY aliasId")
    fun listForCharacter(characterId: String): List<MemoryAliasEntity>

    @Query("SELECT * FROM memory_aliases WHERE novelId = :novelId ORDER BY aliasId")
    fun listForNovel(novelId: String): List<MemoryAliasEntity>
}

@Dao
interface MemoryGlossaryDao {
    @Insert fun insert(row: MemoryGlossaryEntity)

    @Update fun update(row: MemoryGlossaryEntity): Int

    @Query("SELECT * FROM memory_glossary WHERE novelId = :novelId AND termId = :termId")
    fun get(novelId: String, termId: String): MemoryGlossaryEntity?

    @Query("SELECT * FROM memory_glossary WHERE novelId = :novelId AND normalizedTerm = :normalizedTerm")
    fun find(novelId: String, normalizedTerm: String): MemoryGlossaryEntity?

    @Query("SELECT * FROM memory_glossary WHERE novelId = :novelId ORDER BY createdAtMillis, termId")
    fun listForNovel(novelId: String): List<MemoryGlossaryEntity>
}

@Dao
interface MemoryRelationshipDao {
    @Insert fun insert(row: MemoryRelationshipEntity)

    @Update fun update(row: MemoryRelationshipEntity): Int

    @Query("SELECT * FROM memory_relationships WHERE novelId = :novelId AND relationshipId = :relationshipId")
    fun get(novelId: String, relationshipId: String): MemoryRelationshipEntity?

    @Query(
        "SELECT * FROM memory_relationships WHERE novelId = :novelId AND isCurrent = 1 AND " +
            "((fromCharacterId = :a AND toCharacterId = :b) OR (fromCharacterId = :b AND toCharacterId = :a))",
    )
    fun currentBetween(novelId: String, a: String, b: String): List<MemoryRelationshipEntity>

    @Query("SELECT * FROM memory_relationships WHERE novelId = :novelId ORDER BY createdAtMillis, relationshipId")
    fun listForNovel(novelId: String): List<MemoryRelationshipEntity>
}

@Dao
interface MemoryStoryDao {
    @Insert fun insert(row: MemoryStoryEntity)

    @Update fun update(row: MemoryStoryEntity): Int

    @Query("SELECT * FROM memory_story WHERE novelId = :novelId AND entryId = :entryId")
    fun get(novelId: String, entryId: String): MemoryStoryEntity?

    @Query(
        "SELECT * FROM memory_story WHERE novelId = :novelId AND kind = :kind AND " +
            "normalizedSummary = :normalizedSummary AND sourceChapter = :sourceChapter",
    )
    fun findDuplicate(novelId: String, kind: String, normalizedSummary: String, sourceChapter: Int): MemoryStoryEntity?

    @Query("SELECT * FROM memory_story WHERE novelId = :novelId ORDER BY sourceChapter, createdAtMillis, entryId")
    fun listForNovel(novelId: String): List<MemoryStoryEntity>
}

@Dao
interface MemoryWorldDao {
    @Insert fun insert(row: MemoryWorldEntity)

    @Update fun update(row: MemoryWorldEntity): Int

    @Query("SELECT * FROM memory_world WHERE novelId = :novelId AND entryId = :entryId")
    fun get(novelId: String, entryId: String): MemoryWorldEntity?

    @Query("SELECT * FROM memory_world WHERE novelId = :novelId AND kind = :kind AND normalizedName = :normalizedName")
    fun find(novelId: String, kind: String, normalizedName: String): MemoryWorldEntity?

    @Query("SELECT * FROM memory_world WHERE novelId = :novelId ORDER BY createdAtMillis, entryId")
    fun listForNovel(novelId: String): List<MemoryWorldEntity>
}

@Dao
interface MemoryStyleDao {
    @Upsert fun upsert(row: MemoryStyleEntity)

    @Query("SELECT * FROM memory_style WHERE novelId = :novelId")
    fun get(novelId: String): MemoryStyleEntity?
}

@Dao
interface MemoryConflictDao {
    @Insert fun insert(row: MemoryConflictEntity): Long

    @Update fun update(row: MemoryConflictEntity): Int

    @Query("SELECT * FROM memory_conflicts WHERE novelId = :novelId AND conflictId = :conflictId")
    fun get(novelId: String, conflictId: Long): MemoryConflictEntity?

    @Query(
        "SELECT * FROM memory_conflicts WHERE novelId = :novelId AND status = 'OPEN' AND memoryType = :memoryType " +
            "AND memoryId = :memoryId AND fieldName = :fieldName AND proposedValue = :proposedValue",
    )
    fun findOpen(
        novelId: String,
        memoryType: String,
        memoryId: String,
        fieldName: String,
        proposedValue: String,
    ): MemoryConflictEntity?

    @Query("SELECT * FROM memory_conflicts WHERE novelId = :novelId ORDER BY conflictId")
    fun listForNovel(novelId: String): List<MemoryConflictEntity>
}

@Dao
interface MemoryHistoryDao {
    @Insert fun insert(row: MemoryHistoryEntity): Long

    @Query("SELECT * FROM memory_history WHERE novelId = :novelId ORDER BY historyId")
    fun listForNovel(novelId: String): List<MemoryHistoryEntity>
}
