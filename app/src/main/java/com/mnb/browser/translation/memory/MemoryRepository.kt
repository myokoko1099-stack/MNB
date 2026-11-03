package com.mnb.browser.translation.memory

import com.mnb.browser.translation.NovelId
import com.mnb.browser.translation.db.MemoryAliasEntity
import com.mnb.browser.translation.db.MemoryCharacterEntity
import com.mnb.browser.translation.db.MemoryConflictEntity
import com.mnb.browser.translation.db.MemoryGlossaryEntity
import com.mnb.browser.translation.db.MemoryHistoryEntity
import com.mnb.browser.translation.db.MemoryRelationshipEntity
import com.mnb.browser.translation.db.MemoryStoryEntity
import com.mnb.browser.translation.db.MemoryStyleEntity
import com.mnb.browser.translation.db.MemoryWorldEntity
import com.mnb.browser.translation.db.MnbDatabase
import java.util.UUID
import java.util.concurrent.Callable

/**
 * Stores what is known about a novel (characters, glossary, relationships, story, world, style)
 * so later chapters can be translated consistently. Plain storage and lookup only: nothing here
 * calls an AI. Blocking, like the other repositories: call off the main thread. The novel must
 * already exist (save it with NovelRepository first); memory is stored per novel id.
 *
 * Rules the repository enforces:
 * - New facts are UNCERTAIN unless the caller says CONFIRMED; nothing is promoted automatically.
 * - A fact that disagrees with stored memory never overwrites it: it is recorded as an open
 *   [MemoryConflict] and the stored value stays until [resolveConflict] accepts the proposal.
 * - A character is one row; every name is an alias that belongs to exactly one character per novel.
 * - Replaced relationships are kept (isCurrent = false) and every change is logged in [history].
 */
class MemoryRepository(
    private val db: MnbDatabase,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val characterDao = db.memoryCharacterDao()
    private val aliasDao = db.memoryAliasDao()
    private val glossaryDao = db.memoryGlossaryDao()
    private val relationshipDao = db.memoryRelationshipDao()
    private val storyDao = db.memoryStoryDao()
    private val worldDao = db.memoryWorldDao()
    private val styleDao = db.memoryStyleDao()
    private val conflictDao = db.memoryConflictDao()
    private val historyDao = db.memoryHistoryDao()

    private fun <T> tx(block: () -> T): T = db.runInTransaction(Callable<T> { block() })

    // ---------------------------------------------------------------- characters

    /**
     * Creates the character, or merges into the existing one when any of the draft's names already
     * belongs to a character of this novel (so the same person is never stored twice). Merging
     * fills blank fields and adds new aliases; a different non-blank value becomes a conflict.
     * When the names belong to several characters nothing is changed ([CharacterSaveResult.Ambiguous]).
     */
    fun saveCharacter(novelId: NovelId, draft: CharacterDraft): CharacterSaveResult = tx {
        val nid = novelId.value
        val names = namesOf(draft)
        val owners = names
            .mapNotNull { aliasDao.findOwner(nid, MemoryText.normalize(it)) }
            .map { it.characterId }
            .distinct()
        when (owners.size) {
            0 -> CharacterSaveResult.Created(createCharacter(nid, draft, names))
            1 -> mergeCharacter(nid, owners[0], draft, names)
            else -> CharacterSaveResult.Ambiguous(owners)
        }
    }

    fun getCharacter(novelId: NovelId, characterId: String): CharacterMemory? =
        loadCharacter(novelId.value, characterId)

    /** Finds a character by original name or any alias (case, spacing and Unicode form are ignored). */
    fun findCharacter(novelId: NovelId, name: String): CharacterMemory? {
        val owner = aliasDao.findOwner(novelId.value, MemoryText.normalize(name)) ?: return null
        return loadCharacter(novelId.value, owner.characterId)
    }

    fun listCharacters(novelId: NovelId): List<CharacterMemory> {
        val nid = novelId.value
        val aliasesByCharacter = aliasDao.listForNovel(nid).groupBy { it.characterId }
        return characterDao.listForNovel(nid).map { it.toMemory(aliasesByCharacter[it.characterId].orEmpty()) }
    }

    /** Adds another name for an existing character, unless the name already belongs to a different one. */
    fun addAlias(novelId: NovelId, characterId: String, alias: String): AliasResult = tx {
        val nid = novelId.value
        if (alias.isBlank()) return@tx AliasResult(AliasOutcome.BLANK)
        if (characterDao.get(nid, characterId) == null) return@tx AliasResult(AliasOutcome.CHARACTER_NOT_FOUND)
        val key = MemoryText.normalize(alias)
        val owner = aliasDao.findOwner(nid, key)
        when {
            owner == null -> {
                aliasDao.insert(MemoryAliasEntity(0, characterId, nid, alias.trim(), key))
                log(nid, MemoryType.CHARACTER, characterId, HistoryAction.UPDATED, null, "alias: ${alias.trim()}", null)
                AliasResult(AliasOutcome.ADDED, characterId)
            }
            owner.characterId == characterId -> AliasResult(AliasOutcome.ALREADY_PRESENT, characterId)
            else -> AliasResult(AliasOutcome.BELONGS_TO_OTHER_CHARACTER, owner.characterId)
        }
    }

    /** Marks a character CONFIRMED. Returns false when it does not exist in this novel. */
    fun confirmCharacter(novelId: NovelId, characterId: String): Boolean = tx {
        val nid = novelId.value
        val row = characterDao.get(nid, characterId) ?: return@tx false
        if (row.status != MemoryStatus.CONFIRMED.name) {
            characterDao.update(row.copy(status = MemoryStatus.CONFIRMED.name, updatedAtMillis = clock()))
            log(nid, MemoryType.CHARACTER, characterId, HistoryAction.CONFIRMED, row.status, MemoryStatus.CONFIRMED.name, null)
        }
        true
    }

    private fun namesOf(draft: CharacterDraft): List<String> =
        (listOf(draft.originalName) + draft.aliases)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { MemoryText.normalize(it) }

    private fun createCharacter(nid: String, d: CharacterDraft, names: List<String>): CharacterMemory {
        val now = clock()
        val row = MemoryCharacterEntity(
            characterId = newId(),
            novelId = nid,
            originalName = d.originalName.trim(),
            myanmarName = d.myanmarName?.trim(),
            gender = d.gender?.name,
            personality = d.personality.trim(),
            role = d.role.trim(),
            speakingStyle = d.speakingStyle.trim(),
            pronouns = d.pronouns.trim(),
            firstAppearanceChapter = d.firstAppearanceChapter,
            notes = d.notes.trim(),
            status = d.status.name,
            sourceChapter = d.sourceChapter,
            createdAtMillis = now,
            updatedAtMillis = now,
        )
        characterDao.insert(row)
        names.forEach { aliasDao.insert(MemoryAliasEntity(0, row.characterId, nid, it, MemoryText.normalize(it))) }
        log(nid, MemoryType.CHARACTER, row.characterId, HistoryAction.CREATED, null, row.originalName, d.sourceChapter)
        return checkNotNull(loadCharacter(nid, row.characterId)) { "Character could not be read back" }
    }

    private fun mergeCharacter(nid: String, id: String, d: CharacterDraft, names: List<String>): CharacterSaveResult {
        val existing = checkNotNull(characterDao.get(nid, id)) { "Alias points at a missing character" }
        var row = existing
        var conflicts = 0

        fun propose(
            field: String,
            old: String?,
            proposedRaw: String?,
            set: (MemoryCharacterEntity, String) -> MemoryCharacterEntity,
        ) {
            val proposed = proposedRaw?.trim().orEmpty()
            if (proposed.isEmpty()) return
            if (old.isNullOrBlank()) {
                row = set(row, proposed)
            } else if (MemoryText.normalize(old) != MemoryText.normalize(proposed)) {
                recordConflict(nid, MemoryType.CHARACTER, id, field, old, proposed, d.sourceChapter)
                conflicts++
            }
        }

        propose("myanmarName", existing.myanmarName, d.myanmarName) { r, v -> r.copy(myanmarName = v) }
        propose("gender", existing.gender, d.gender?.name) { r, v -> r.copy(gender = v) }
        propose("personality", existing.personality, d.personality) { r, v -> r.copy(personality = v) }
        propose("role", existing.role, d.role) { r, v -> r.copy(role = v) }
        propose("speakingStyle", existing.speakingStyle, d.speakingStyle) { r, v -> r.copy(speakingStyle = v) }
        propose("pronouns", existing.pronouns, d.pronouns) { r, v -> r.copy(pronouns = v) }
        propose("notes", existing.notes, d.notes) { r, v -> r.copy(notes = v) }

        val promoted = existing.status == MemoryStatus.UNCERTAIN.name && d.status == MemoryStatus.CONFIRMED
        row = row.copy(
            firstAppearanceChapter = listOfNotNull(existing.firstAppearanceChapter, d.firstAppearanceChapter).minOrNull(),
            sourceChapter = existing.sourceChapter ?: d.sourceChapter,
            status = if (promoted) MemoryStatus.CONFIRMED.name else existing.status,
        )
        val newNames = names.filter { aliasDao.findOwner(nid, MemoryText.normalize(it)) == null }
        newNames.forEach { aliasDao.insert(MemoryAliasEntity(0, id, nid, it, MemoryText.normalize(it))) }
        if (row != existing || newNames.isNotEmpty()) {
            characterDao.update(row.copy(updatedAtMillis = clock()))
            log(nid, MemoryType.CHARACTER, id, HistoryAction.UPDATED, null, "merged duplicate draft", d.sourceChapter)
        }
        val merged = checkNotNull(loadCharacter(nid, id)) { "Character could not be read back" }
        return CharacterSaveResult.Merged(merged, conflicts)
    }

    private fun loadCharacter(nid: String, id: String): CharacterMemory? {
        val row = characterDao.get(nid, id) ?: return null
        return row.toMemory(aliasDao.listForCharacter(id))
    }

    // ---------------------------------------------------------------- glossary

    /**
     * Stores a term and its Myanmar translation. A term (compared ignoring case and spacing) has
     * exactly one translation: saving the same term with a different translation records a
     * conflict and keeps the stored one, so a term cannot silently change between chapters.
     */
    fun saveGlossary(
        novelId: NovelId,
        sourceTerm: String,
        translatedTerm: String,
        category: GlossaryCategory,
        status: MemoryStatus = MemoryStatus.UNCERTAIN,
        sourceChapter: Int? = null,
    ): GlossarySaveResult = tx {
        require(sourceTerm.isNotBlank()) { "sourceTerm must not be blank" }
        require(translatedTerm.isNotBlank()) { "translatedTerm must not be blank" }
        require(sourceChapter == null || sourceChapter >= 0) { "chapter must not be negative" }
        val nid = novelId.value
        val key = MemoryText.normalize(sourceTerm)
        val existing = glossaryDao.find(nid, key)
        if (existing == null) {
            val now = clock()
            val row = MemoryGlossaryEntity(
                termId = newId(),
                novelId = nid,
                sourceTerm = sourceTerm.trim(),
                normalizedTerm = key,
                translatedTerm = translatedTerm.trim(),
                category = category.name,
                status = status.name,
                sourceChapter = sourceChapter,
                createdAtMillis = now,
                updatedAtMillis = now,
            )
            glossaryDao.insert(row)
            log(nid, MemoryType.GLOSSARY, row.termId, HistoryAction.CREATED, null, "${row.sourceTerm} -> ${row.translatedTerm}", sourceChapter)
            return@tx GlossarySaveResult(SaveOutcome.CREATED, row.toMemory()!!)
        }
        if (MemoryText.normalize(existing.translatedTerm) == MemoryText.normalize(translatedTerm)) {
            if (existing.status == MemoryStatus.UNCERTAIN.name && status == MemoryStatus.CONFIRMED) {
                val updated = existing.copy(status = MemoryStatus.CONFIRMED.name, updatedAtMillis = clock())
                glossaryDao.update(updated)
                log(nid, MemoryType.GLOSSARY, existing.termId, HistoryAction.CONFIRMED, existing.status, updated.status, sourceChapter)
                return@tx GlossarySaveResult(SaveOutcome.UPDATED, updated.toMemory()!!)
            }
            return@tx GlossarySaveResult(SaveOutcome.UNCHANGED, existing.toMemory()!!)
        }
        val conflictId = recordConflict(
            nid, MemoryType.GLOSSARY, existing.termId, "translatedTerm",
            existing.translatedTerm, translatedTerm.trim(), sourceChapter,
        )
        GlossarySaveResult(SaveOutcome.CONFLICT_RECORDED, existing.toMemory()!!, conflictId)
    }

    fun getGlossary(novelId: NovelId, sourceTerm: String): GlossaryMemory? =
        glossaryDao.find(novelId.value, MemoryText.normalize(sourceTerm))?.toMemory()

    /** The stored translation of a term, or null when the term is unknown. */
    fun translationFor(novelId: NovelId, sourceTerm: String): String? = getGlossary(novelId, sourceTerm)?.translatedTerm

    fun listGlossary(novelId: NovelId, category: GlossaryCategory? = null): List<GlossaryMemory> =
        glossaryDao.listForNovel(novelId.value).mapNotNull { it.toMemory() }
            .filter { category == null || it.category == category }

    fun confirmGlossary(novelId: NovelId, sourceTerm: String): Boolean = tx {
        val nid = novelId.value
        val row = glossaryDao.find(nid, MemoryText.normalize(sourceTerm)) ?: return@tx false
        if (row.status != MemoryStatus.CONFIRMED.name) {
            glossaryDao.update(row.copy(status = MemoryStatus.CONFIRMED.name, updatedAtMillis = clock()))
            log(nid, MemoryType.GLOSSARY, row.termId, HistoryAction.CONFIRMED, row.status, MemoryStatus.CONFIRMED.name, null)
        }
        true
    }

    // ---------------------------------------------------------------- relationships

    /**
     * Stores a relationship between two characters of this novel. The same pair and type is not
     * stored twice (symmetric types ignore direction). Use [updateRelationship] when it changed.
     */
    fun saveRelationship(
        novelId: NovelId,
        fromCharacterId: String,
        toCharacterId: String,
        type: RelationshipType,
        description: String = "",
        status: MemoryStatus = MemoryStatus.UNCERTAIN,
        sourceChapter: Int? = null,
    ): RelationshipSaveResult = tx {
        require(sourceChapter == null || sourceChapter >= 0) { "chapter must not be negative" }
        val nid = novelId.value
        if (fromCharacterId == toCharacterId) return@tx RelationshipSaveResult(RelationshipOutcome.SAME_CHARACTER)
        if (characterDao.get(nid, fromCharacterId) == null || characterDao.get(nid, toCharacterId) == null) {
            return@tx RelationshipSaveResult(RelationshipOutcome.CHARACTER_NOT_FOUND)
        }
        val duplicate = relationshipDao.currentBetween(nid, fromCharacterId, toCharacterId).firstOrNull {
            it.type == type.name && (type.symmetric || it.fromCharacterId == fromCharacterId)
        }
        if (duplicate != null) {
            return@tx RelationshipSaveResult(RelationshipOutcome.ALREADY_EXISTS, duplicate.toMemory())
        }
        val row = MemoryRelationshipEntity(
            relationshipId = newId(),
            novelId = nid,
            fromCharacterId = fromCharacterId,
            toCharacterId = toCharacterId,
            type = type.name,
            description = description.trim(),
            status = status.name,
            startChapter = sourceChapter,
            endChapter = null,
            isCurrent = true,
            createdAtMillis = clock(),
        )
        relationshipDao.insert(row)
        log(nid, MemoryType.RELATIONSHIP, row.relationshipId, HistoryAction.CREATED, null, "${type.name}: ${row.description}", sourceChapter)
        RelationshipSaveResult(RelationshipOutcome.CREATED, row.toMemory())
    }

    /**
     * Records that a relationship changed (for example friends became enemies). The old row is not
     * deleted: it is closed (isCurrent = false, endChapter set) and a new current row is added.
     * Returns the new current relationship, the unchanged one when nothing differs, or null when
     * [relationshipId] is not a current relationship of this novel.
     */
    fun updateRelationship(
        novelId: NovelId,
        relationshipId: String,
        newType: RelationshipType,
        newDescription: String,
        sourceChapter: Int?,
        status: MemoryStatus = MemoryStatus.UNCERTAIN,
    ): RelationshipMemory? = tx {
        require(sourceChapter == null || sourceChapter >= 0) { "chapter must not be negative" }
        val nid = novelId.value
        val old = relationshipDao.get(nid, relationshipId)
        if (old == null || !old.isCurrent) return@tx null
        if (old.type == newType.name && MemoryText.normalize(old.description) == MemoryText.normalize(newDescription)) {
            return@tx old.toMemory()
        }
        relationshipDao.update(old.copy(isCurrent = false, endChapter = sourceChapter))
        val fresh = old.copy(
            relationshipId = newId(),
            type = newType.name,
            description = newDescription.trim(),
            status = status.name,
            startChapter = sourceChapter,
            endChapter = null,
            isCurrent = true,
            createdAtMillis = clock(),
        )
        relationshipDao.insert(fresh)
        log(
            nid, MemoryType.RELATIONSHIP, fresh.relationshipId, HistoryAction.SUPERSEDED,
            "${old.type}: ${old.description}", "${fresh.type}: ${fresh.description}", sourceChapter,
        )
        fresh.toMemory()
    }

    /** Relationships of the novel, oldest first. With [characterId] only those involving that character. */
    fun listRelationships(
        novelId: NovelId,
        characterId: String? = null,
        currentOnly: Boolean = true,
    ): List<RelationshipMemory> =
        relationshipDao.listForNovel(novelId.value).mapNotNull { it.toMemory() }.filter {
            (!currentOnly || it.isCurrent) &&
                (characterId == null || it.fromCharacterId == characterId || it.toCharacterId == characterId)
        }

    fun relationshipsBetween(
        novelId: NovelId,
        a: String,
        b: String,
        currentOnly: Boolean = true,
    ): List<RelationshipMemory> =
        listRelationships(novelId, a, currentOnly).filter {
            (it.fromCharacterId == a && it.toCharacterId == b) || (it.fromCharacterId == b && it.toCharacterId == a)
        }

    // ---------------------------------------------------------------- story

    /**
     * Stores an important story fact. [sourceChapter] is required so every fact can be traced to
     * the chapter it came from. The same kind, summary and chapter is stored once.
     */
    fun addStory(
        novelId: NovelId,
        kind: StoryKind,
        summary: String,
        sourceChapter: Int,
        characterIds: List<String> = emptyList(),
        status: MemoryStatus = MemoryStatus.UNCERTAIN,
    ): StoryMemory = tx {
        require(summary.isNotBlank()) { "summary must not be blank" }
        require(sourceChapter >= 0) { "chapter must not be negative" }
        val nid = novelId.value
        characterIds.forEach { require(characterDao.get(nid, it) != null) { "Unknown character $it" } }
        val key = MemoryText.normalize(summary)
        val duplicate = storyDao.findDuplicate(nid, kind.name, key, sourceChapter)
        val row = duplicate ?: MemoryStoryEntity(
            entryId = newId(),
            novelId = nid,
            kind = kind.name,
            summary = summary.trim(),
            normalizedSummary = key,
            sourceChapter = sourceChapter,
            characterIds = characterIds.distinct().joinToString(","),
            status = status.name,
            resolved = false,
            createdAtMillis = clock(),
        ).also {
            storyDao.insert(it)
            log(nid, MemoryType.STORY, it.entryId, HistoryAction.CREATED, null, it.summary, sourceChapter)
        }
        row.toMemory()!!
    }

    /** Marks e.g. an unresolved question or a promise as resolved. The entry is kept. */
    fun markStoryResolved(novelId: NovelId, entryId: String): Boolean = tx {
        val nid = novelId.value
        val row = storyDao.get(nid, entryId) ?: return@tx false
        if (!row.resolved) {
            storyDao.update(row.copy(resolved = true))
            log(nid, MemoryType.STORY, entryId, HistoryAction.UPDATED, "unresolved", "resolved", null)
        }
        true
    }

    /** Story facts in chapter order. [beforeChapter] keeps only facts from earlier chapters. */
    fun listStory(novelId: NovelId, kind: StoryKind? = null, beforeChapter: Int? = null): List<StoryMemory> =
        storyDao.listForNovel(novelId.value).mapNotNull { it.toMemory() }.filter {
            (kind == null || it.kind == kind) && (beforeChapter == null || it.sourceChapter < beforeChapter)
        }

    // ---------------------------------------------------------------- world

    /** Stores a world fact. Same kind + name with a different description becomes a conflict. */
    fun saveWorld(
        novelId: NovelId,
        kind: WorldKind,
        name: String,
        description: String,
        status: MemoryStatus = MemoryStatus.UNCERTAIN,
        sourceChapter: Int? = null,
    ): WorldSaveResult = tx {
        require(name.isNotBlank()) { "name must not be blank" }
        require(sourceChapter == null || sourceChapter >= 0) { "chapter must not be negative" }
        val nid = novelId.value
        val key = MemoryText.normalize(name)
        val existing = worldDao.find(nid, kind.name, key)
        val text = description.trim()
        if (existing == null) {
            val now = clock()
            val row = MemoryWorldEntity(
                entryId = newId(),
                novelId = nid,
                kind = kind.name,
                name = name.trim(),
                normalizedName = key,
                description = text,
                status = status.name,
                sourceChapter = sourceChapter,
                createdAtMillis = now,
                updatedAtMillis = now,
            )
            worldDao.insert(row)
            log(nid, MemoryType.WORLD, row.entryId, HistoryAction.CREATED, null, "${kind.name}: ${row.name}", sourceChapter)
            return@tx WorldSaveResult(SaveOutcome.CREATED, row.toMemory()!!)
        }
        when {
            text.isEmpty() || MemoryText.normalize(existing.description) == MemoryText.normalize(text) ->
                WorldSaveResult(SaveOutcome.UNCHANGED, existing.toMemory()!!)
            existing.description.isBlank() -> {
                val updated = existing.copy(description = text, updatedAtMillis = clock())
                worldDao.update(updated)
                log(nid, MemoryType.WORLD, existing.entryId, HistoryAction.UPDATED, null, text, sourceChapter)
                WorldSaveResult(SaveOutcome.UPDATED, updated.toMemory()!!)
            }
            else -> {
                val conflictId = recordConflict(
                    nid, MemoryType.WORLD, existing.entryId, "description", existing.description, text, sourceChapter,
                )
                WorldSaveResult(SaveOutcome.CONFLICT_RECORDED, existing.toMemory()!!, conflictId)
            }
        }
    }

    fun findWorld(novelId: NovelId, kind: WorldKind, name: String): WorldMemory? =
        worldDao.find(novelId.value, kind.name, MemoryText.normalize(name))?.toMemory()

    fun listWorld(novelId: NovelId, kind: WorldKind? = null): List<WorldMemory> =
        worldDao.listForNovel(novelId.value).mapNotNull { it.toMemory() }.filter { kind == null || it.kind == kind }

    // ---------------------------------------------------------------- style

    /** Sets the novel's translation style. Style is an explicit setting, so saving replaces the previous one (logged). */
    fun saveStyle(novelId: NovelId, style: StyleMemory) = tx {
        val nid = novelId.value
        val old = styleDao.get(nid)
        styleDao.upsert(
            MemoryStyleEntity(
                novelId = nid,
                writingStyle = style.writingStyle.trim(),
                tone = style.tone.name,
                dialogueStyle = style.dialogueStyle.trim(),
                pronounPreferences = style.pronounPreferences.trim(),
                namingConventions = style.namingConventions.trim(),
                translationPreferences = style.translationPreferences.trim(),
                updatedAtMillis = clock(),
            ),
        )
        log(
            nid, MemoryType.STYLE, nid,
            if (old == null) HistoryAction.CREATED else HistoryAction.UPDATED,
            old?.let { "${it.tone}: ${it.writingStyle}" }, "${style.tone.name}: ${style.writingStyle.trim()}", null,
        )
    }

    fun getStyle(novelId: NovelId): StyleMemory? = styleDao.get(novelId.value)?.let {
        StyleMemory(
            writingStyle = it.writingStyle,
            tone = enumOrNull<ToneLevel>(it.tone) ?: ToneLevel.UNSPECIFIED,
            dialogueStyle = it.dialogueStyle,
            pronounPreferences = it.pronounPreferences,
            namingConventions = it.namingConventions,
            translationPreferences = it.translationPreferences,
        )
    }

    // ---------------------------------------------------------------- conflicts and history

    fun listConflicts(novelId: NovelId, openOnly: Boolean = true): List<MemoryConflict> =
        conflictDao.listForNovel(novelId.value).mapNotNull { it.toModel() }
            .filter { !openOnly || it.status == ConflictStatus.OPEN }

    /**
     * Settles an open conflict. Accepting replaces the stored value with the proposed one (the old
     * value stays in [history]); rejecting keeps the stored value. Returns false when the conflict
     * is unknown, already settled, or its target no longer exists.
     */
    fun resolveConflict(novelId: NovelId, conflictId: Long, acceptProposed: Boolean): Boolean = tx {
        val nid = novelId.value
        val conflict = conflictDao.get(nid, conflictId)
        if (conflict == null || conflict.status != ConflictStatus.OPEN.name) return@tx false
        val now = clock()
        if (acceptProposed && !applyProposal(nid, conflict, now)) return@tx false
        val settled = if (acceptProposed) ConflictStatus.ACCEPTED else ConflictStatus.REJECTED
        conflictDao.update(conflict.copy(status = settled.name, resolvedAtMillis = now))
        if (!acceptProposed) {
            log(
                nid, enumOrNull<MemoryType>(conflict.memoryType) ?: MemoryType.CHARACTER, conflict.memoryId,
                HistoryAction.CONFLICT_REJECTED, conflict.existingValue, conflict.proposedValue, conflict.proposedSourceChapter,
            )
        }
        true
    }

    /** Everything that changed in the novel's memory, oldest first. */
    fun history(novelId: NovelId, type: MemoryType? = null): List<MemoryHistoryEntry> =
        historyDao.listForNovel(novelId.value).mapNotNull { it.toModel() }.filter { type == null || it.type == type }

    private fun applyProposal(nid: String, c: MemoryConflictEntity, now: Long): Boolean {
        when (c.memoryType) {
            MemoryType.GLOSSARY.name -> {
                val row = glossaryDao.get(nid, c.memoryId) ?: return false
                glossaryDao.update(row.copy(translatedTerm = c.proposedValue, updatedAtMillis = now))
                log(nid, MemoryType.GLOSSARY, row.termId, HistoryAction.SUPERSEDED, row.translatedTerm, c.proposedValue, c.proposedSourceChapter)
                return true
            }
            MemoryType.WORLD.name -> {
                val row = worldDao.get(nid, c.memoryId) ?: return false
                worldDao.update(row.copy(description = c.proposedValue, updatedAtMillis = now))
                log(nid, MemoryType.WORLD, row.entryId, HistoryAction.SUPERSEDED, row.description, c.proposedValue, c.proposedSourceChapter)
                return true
            }
            MemoryType.CHARACTER.name -> {
                val row = characterDao.get(nid, c.memoryId) ?: return false
                val updated = withCharacterField(row, c.fieldName, c.proposedValue) ?: return false
                characterDao.update(updated.copy(updatedAtMillis = now))
                log(nid, MemoryType.CHARACTER, row.characterId, HistoryAction.SUPERSEDED, c.existingValue, c.proposedValue, c.proposedSourceChapter)
                return true
            }
            else -> return false
        }
    }

    private fun withCharacterField(row: MemoryCharacterEntity, field: String, value: String): MemoryCharacterEntity? =
        when (field) {
            "myanmarName" -> row.copy(myanmarName = value)
            "gender" -> if (enumOrNull<Gender>(value) != null) row.copy(gender = value) else null
            "personality" -> row.copy(personality = value)
            "role" -> row.copy(role = value)
            "speakingStyle" -> row.copy(speakingStyle = value)
            "pronouns" -> row.copy(pronouns = value)
            "notes" -> row.copy(notes = value)
            else -> null
        }

    /** Records a disagreement once (the same open proposal is not duplicated) and returns its id. */
    private fun recordConflict(
        nid: String,
        type: MemoryType,
        memoryId: String,
        field: String,
        existing: String,
        proposed: String,
        sourceChapter: Int?,
    ): Long {
        val open = conflictDao.findOpen(nid, type.name, memoryId, field, proposed)
        if (open != null) return open.conflictId
        val id = conflictDao.insert(
            MemoryConflictEntity(
                conflictId = 0,
                novelId = nid,
                memoryType = type.name,
                memoryId = memoryId,
                fieldName = field,
                existingValue = existing,
                proposedValue = proposed,
                proposedSourceChapter = sourceChapter,
                status = ConflictStatus.OPEN.name,
                createdAtMillis = clock(),
                resolvedAtMillis = null,
            ),
        )
        log(nid, type, memoryId, HistoryAction.CONFLICT_RECORDED, existing, proposed, sourceChapter)
        return id
    }

    private fun log(
        nid: String,
        type: MemoryType,
        memoryId: String,
        action: HistoryAction,
        old: String?,
        new: String?,
        sourceChapter: Int?,
    ) {
        historyDao.insert(
            MemoryHistoryEntity(0, nid, type.name, memoryId, action.name, old, new, sourceChapter, clock()),
        )
    }
}

// ------------------------------------------------------------------ row -> model

private fun statusOf(name: String): MemoryStatus = enumOrNull<MemoryStatus>(name) ?: MemoryStatus.UNCERTAIN

private fun MemoryCharacterEntity.toMemory(aliasRows: List<MemoryAliasEntity>): CharacterMemory {
    val own = MemoryText.normalize(originalName)
    return CharacterMemory(
        id = characterId,
        novelId = NovelId(novelId),
        originalName = originalName,
        myanmarName = myanmarName,
        aliases = aliasRows.filter { it.normalizedAlias != own }.map { it.alias },
        gender = enumOrNull<Gender>(gender),
        personality = personality,
        role = role,
        speakingStyle = speakingStyle,
        pronouns = pronouns,
        firstAppearanceChapter = firstAppearanceChapter,
        notes = notes,
        status = statusOf(status),
        sourceChapter = sourceChapter,
    )
}

private fun MemoryGlossaryEntity.toMemory(): GlossaryMemory? {
    val cat = enumOrNull<GlossaryCategory>(category) ?: return null
    return GlossaryMemory(termId, NovelId(novelId), sourceTerm, translatedTerm, cat, statusOf(status), sourceChapter)
}

private fun MemoryRelationshipEntity.toMemory(): RelationshipMemory? {
    val t = enumOrNull<RelationshipType>(type) ?: return null
    return RelationshipMemory(
        relationshipId, NovelId(novelId), fromCharacterId, toCharacterId, t, description,
        statusOf(status), startChapter, endChapter, isCurrent,
    )
}

private fun MemoryStoryEntity.toMemory(): StoryMemory? {
    val k = enumOrNull<StoryKind>(kind) ?: return null
    return StoryMemory(
        entryId, NovelId(novelId), k, summary, sourceChapter,
        characterIds.split(",").filter { it.isNotEmpty() }, statusOf(status), resolved,
    )
}

private fun MemoryWorldEntity.toMemory(): WorldMemory? {
    val k = enumOrNull<WorldKind>(kind) ?: return null
    return WorldMemory(entryId, NovelId(novelId), k, name, description, statusOf(status), sourceChapter)
}

private fun MemoryConflictEntity.toModel(): MemoryConflict? {
    val t = enumOrNull<MemoryType>(memoryType) ?: return null
    val s = enumOrNull<ConflictStatus>(status) ?: return null
    return MemoryConflict(conflictId, NovelId(novelId), t, memoryId, fieldName, existingValue, proposedValue, proposedSourceChapter, s)
}

private fun MemoryHistoryEntity.toModel(): MemoryHistoryEntry? {
    val t = enumOrNull<MemoryType>(memoryType) ?: return null
    val a = enumOrNull<HistoryAction>(action) ?: return null
    return MemoryHistoryEntry(historyId, NovelId(novelId), t, memoryId, a, oldValue, newValue, sourceChapter, atMillis)
}
