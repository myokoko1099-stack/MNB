package com.mnb.browser.translation.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Phase 04-I6 Context Memory rows. Every row carries a novelId (cascade delete with the novel) so
 * memory of one novel can never leak into another. Enums are stored as Strings, dates as epoch
 * milliseconds. The matching CREATE statements live in MemorySchemaV2 and must stay identical to
 * what Room expects from these classes (the migration test opens a migrated database to prove it).
 */

@Entity(
    tableName = "memory_characters",
    foreignKeys = [ForeignKey(NovelEntity::class, ["novelId"], ["novelId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("novelId")],
)
data class MemoryCharacterEntity(
    @PrimaryKey val characterId: String,
    val novelId: String,
    val originalName: String,
    val myanmarName: String?,
    val gender: String?,
    val personality: String,
    val role: String,
    val speakingStyle: String,
    val pronouns: String,
    val firstAppearanceChapter: Int?,
    val notes: String,
    val status: String,
    val sourceChapter: Int?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

/** One name of a character (the original name is stored here too). A name belongs to one character per novel. */
@Entity(
    tableName = "memory_aliases",
    foreignKeys = [
        ForeignKey(MemoryCharacterEntity::class, ["characterId"], ["characterId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(NovelEntity::class, ["novelId"], ["novelId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["novelId", "normalizedAlias"], unique = true), Index("characterId")],
)
data class MemoryAliasEntity(
    @PrimaryKey(autoGenerate = true) val aliasId: Long,
    val characterId: String,
    val novelId: String,
    val alias: String,
    val normalizedAlias: String,
)

@Entity(
    tableName = "memory_glossary",
    foreignKeys = [ForeignKey(NovelEntity::class, ["novelId"], ["novelId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["novelId", "normalizedTerm"], unique = true)],
)
data class MemoryGlossaryEntity(
    @PrimaryKey val termId: String,
    val novelId: String,
    val sourceTerm: String,
    val normalizedTerm: String,
    val translatedTerm: String,
    val category: String,
    val status: String,
    val sourceChapter: Int?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "memory_relationships",
    foreignKeys = [
        ForeignKey(NovelEntity::class, ["novelId"], ["novelId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(MemoryCharacterEntity::class, ["characterId"], ["fromCharacterId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(MemoryCharacterEntity::class, ["characterId"], ["toCharacterId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("novelId"), Index("fromCharacterId"), Index("toCharacterId")],
)
data class MemoryRelationshipEntity(
    @PrimaryKey val relationshipId: String,
    val novelId: String,
    val fromCharacterId: String,
    val toCharacterId: String,
    val type: String,
    val description: String,
    val status: String,
    val startChapter: Int?,
    val endChapter: Int?,
    val isCurrent: Boolean,
    val createdAtMillis: Long,
)

/** [characterIds] is a comma separated list of memory_characters ids. */
@Entity(
    tableName = "memory_story",
    foreignKeys = [ForeignKey(NovelEntity::class, ["novelId"], ["novelId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["novelId", "sourceChapter"])],
)
data class MemoryStoryEntity(
    @PrimaryKey val entryId: String,
    val novelId: String,
    val kind: String,
    val summary: String,
    val normalizedSummary: String,
    val sourceChapter: Int,
    val characterIds: String,
    val status: String,
    val resolved: Boolean,
    val createdAtMillis: Long,
)

@Entity(
    tableName = "memory_world",
    foreignKeys = [ForeignKey(NovelEntity::class, ["novelId"], ["novelId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["novelId", "kind", "normalizedName"], unique = true)],
)
data class MemoryWorldEntity(
    @PrimaryKey val entryId: String,
    val novelId: String,
    val kind: String,
    val name: String,
    val normalizedName: String,
    val description: String,
    val status: String,
    val sourceChapter: Int?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

/** One style record per novel. */
@Entity(
    tableName = "memory_style",
    foreignKeys = [ForeignKey(NovelEntity::class, ["novelId"], ["novelId"], onDelete = ForeignKey.CASCADE)],
)
data class MemoryStyleEntity(
    @PrimaryKey val novelId: String,
    val writingStyle: String,
    val tone: String,
    val dialogueStyle: String,
    val pronounPreferences: String,
    val namingConventions: String,
    val translationPreferences: String,
    val updatedAtMillis: Long,
)

/** A proposed change that disagrees with stored memory. The stored value is kept until someone resolves it. */
@Entity(
    tableName = "memory_conflicts",
    foreignKeys = [ForeignKey(NovelEntity::class, ["novelId"], ["novelId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["novelId", "status"])],
)
data class MemoryConflictEntity(
    @PrimaryKey(autoGenerate = true) val conflictId: Long,
    val novelId: String,
    val memoryType: String,
    val memoryId: String,
    val fieldName: String,
    val existingValue: String,
    val proposedValue: String,
    val proposedSourceChapter: Int?,
    val status: String,
    val createdAtMillis: Long,
    val resolvedAtMillis: Long?,
)

/** Append-only log of what changed in memory. */
@Entity(
    tableName = "memory_history",
    foreignKeys = [ForeignKey(NovelEntity::class, ["novelId"], ["novelId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["novelId", "memoryType"])],
)
data class MemoryHistoryEntity(
    @PrimaryKey(autoGenerate = true) val historyId: Long,
    val novelId: String,
    val memoryType: String,
    val memoryId: String,
    val action: String,
    val oldValue: String?,
    val newValue: String?,
    val sourceChapter: Int?,
    val atMillis: Long,
)
