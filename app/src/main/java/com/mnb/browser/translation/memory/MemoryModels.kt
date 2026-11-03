package com.mnb.browser.translation.memory

import com.mnb.browser.translation.NovelId

/** Whether a fact was confirmed. New facts default to UNCERTAIN and are never promoted automatically. */
enum class MemoryStatus { CONFIRMED, UNCERTAIN }

enum class Gender { MALE, FEMALE, OTHER }

enum class GlossaryCategory {
    CHARACTER_NAME, PLACE_NAME, ORGANIZATION_NAME, TITLE, SPECIAL_TERM, SKILL, WORLD_VOCABULARY, REPEATED_EXPRESSION,
}

/** [symmetric] types read the same from both sides, so (A,B) and (B,A) are the same relationship. */
enum class RelationshipType(val symmetric: Boolean) {
    FAMILY(true),
    FRIENDSHIP(true),
    ENMITY(true),
    MASTER_STUDENT(false),
    POLITICAL(true),
    ORGANIZATION_MEMBERSHIP(false),
    OTHER(true),
}

enum class StoryKind { EVENT, DECISION, DISCOVERY, UNRESOLVED_QUESTION, PROMISE, TIMELINE }

enum class WorldKind { KINGDOM, COUNTRY, CITY, GEOGRAPHY, ORGANIZATION, POLITICAL_SYSTEM, CULTURE, WORLD_RULE, LAW }

enum class ToneLevel { FORMAL, INFORMAL, MIXED, UNSPECIFIED }

enum class MemoryType { CHARACTER, GLOSSARY, RELATIONSHIP, STORY, WORLD, STYLE }

enum class ConflictStatus { OPEN, ACCEPTED, REJECTED }

enum class HistoryAction { CREATED, UPDATED, SUPERSEDED, CONFIRMED, CONFLICT_RECORDED, CONFLICT_REJECTED }

/** What a caller supplies to create (or merge into) a character. Blank myanmarName is not allowed; use null. */
data class CharacterDraft(
    val originalName: String,
    val myanmarName: String? = null,
    val aliases: List<String> = emptyList(),
    val gender: Gender? = null,
    val personality: String = "",
    val role: String = "",
    val speakingStyle: String = "",
    val pronouns: String = "",
    val firstAppearanceChapter: Int? = null,
    val notes: String = "",
    val status: MemoryStatus = MemoryStatus.UNCERTAIN,
    val sourceChapter: Int? = null,
) {
    init {
        require(originalName.isNotBlank()) { "originalName must not be blank" }
        require(myanmarName == null || myanmarName.isNotBlank()) { "myanmarName must be null or not blank" }
        require(firstAppearanceChapter == null || firstAppearanceChapter >= 0) { "chapter must not be negative" }
        require(sourceChapter == null || sourceChapter >= 0) { "chapter must not be negative" }
    }
}

data class CharacterMemory(
    val id: String,
    val novelId: NovelId,
    val originalName: String,
    val myanmarName: String?,
    /** Other names for the same person, not including [originalName]. */
    val aliases: List<String>,
    val gender: Gender?,
    val personality: String,
    val role: String,
    val speakingStyle: String,
    val pronouns: String,
    val firstAppearanceChapter: Int?,
    val notes: String,
    val status: MemoryStatus,
    val sourceChapter: Int?,
) {
    val allNames: List<String> get() = listOf(originalName) + aliases
}

data class GlossaryMemory(
    val id: String,
    val novelId: NovelId,
    val sourceTerm: String,
    val translatedTerm: String,
    val category: GlossaryCategory,
    val status: MemoryStatus,
    val sourceChapter: Int?,
)

data class RelationshipMemory(
    val id: String,
    val novelId: NovelId,
    val fromCharacterId: String,
    val toCharacterId: String,
    val type: RelationshipType,
    val description: String,
    val status: MemoryStatus,
    val startChapter: Int?,
    /** Set when a later update replaced this relationship. The row is kept as history. */
    val endChapter: Int?,
    val isCurrent: Boolean,
)

data class StoryMemory(
    val id: String,
    val novelId: NovelId,
    val kind: StoryKind,
    val summary: String,
    val sourceChapter: Int,
    val characterIds: List<String>,
    val status: MemoryStatus,
    val resolved: Boolean,
)

data class WorldMemory(
    val id: String,
    val novelId: NovelId,
    val kind: WorldKind,
    val name: String,
    val description: String,
    val status: MemoryStatus,
    val sourceChapter: Int?,
)

/** Per-novel translation style. */
data class StyleMemory(
    val writingStyle: String = "",
    val tone: ToneLevel = ToneLevel.UNSPECIFIED,
    val dialogueStyle: String = "",
    val pronounPreferences: String = "",
    val namingConventions: String = "",
    val translationPreferences: String = "",
)

data class MemoryConflict(
    val id: Long,
    val novelId: NovelId,
    val type: MemoryType,
    val memoryId: String,
    val fieldName: String,
    val existingValue: String,
    val proposedValue: String,
    val proposedSourceChapter: Int?,
    val status: ConflictStatus,
)

data class MemoryHistoryEntry(
    val id: Long,
    val novelId: NovelId,
    val type: MemoryType,
    val memoryId: String,
    val action: HistoryAction,
    val oldValue: String?,
    val newValue: String?,
    val sourceChapter: Int?,
    val atMillis: Long,
)

sealed interface CharacterSaveResult {
    data class Created(val character: CharacterMemory) : CharacterSaveResult

    /** The draft matched an existing character by name or alias; blanks were filled, differences became conflicts. */
    data class Merged(val character: CharacterMemory, val conflictsRecorded: Int) : CharacterSaveResult

    /** The draft's names belong to more than one existing character. Nothing was changed. */
    data class Ambiguous(val matchingCharacterIds: List<String>) : CharacterSaveResult
}

enum class AliasOutcome { ADDED, ALREADY_PRESENT, BELONGS_TO_OTHER_CHARACTER, CHARACTER_NOT_FOUND, BLANK }

data class AliasResult(val outcome: AliasOutcome, val ownerCharacterId: String? = null)

enum class SaveOutcome { CREATED, UNCHANGED, UPDATED, CONFLICT_RECORDED }

data class GlossarySaveResult(val outcome: SaveOutcome, val entry: GlossaryMemory, val conflictId: Long? = null)

data class WorldSaveResult(val outcome: SaveOutcome, val entry: WorldMemory, val conflictId: Long? = null)

enum class RelationshipOutcome { CREATED, ALREADY_EXISTS, CHARACTER_NOT_FOUND, SAME_CHARACTER }

data class RelationshipSaveResult(val outcome: RelationshipOutcome, val relationship: RelationshipMemory? = null)
