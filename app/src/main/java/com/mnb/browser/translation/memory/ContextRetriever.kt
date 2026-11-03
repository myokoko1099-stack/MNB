package com.mnb.browser.translation.memory

import com.mnb.browser.translation.CharacterInfo
import com.mnb.browser.translation.GlossaryEntry
import com.mnb.browser.translation.NovelId
import com.mnb.browser.translation.TranslationContext

/**
 * What a later phase (real AI providers) plugs in to get the context for one chunk. The
 * translation pipeline is not wired to this yet.
 */
interface TranslationContextProvider {
    fun contextFor(novelId: NovelId, chunkText: String, chapterNumber: Int? = null): TranslationContext
}

data class RetrievalOptions(
    /** The chapter the chunk belongs to. When set, story facts from this chapter or later are left out. */
    val chapterNumber: Int? = null,
    /** UNCERTAIN facts are left out unless this is true; when included they are labelled as unconfirmed. */
    val includeUncertain: Boolean = false,
    val maxCharacters: Int = 10,
    val maxGlossary: Int = 50,
    val maxStory: Int = 5,
    val maxWorld: Int = 10,
) {
    init {
        require(chapterNumber == null || chapterNumber >= 0) { "chapterNumber must not be negative" }
        require(maxCharacters >= 0 && maxGlossary >= 0 && maxStory >= 0 && maxWorld >= 0) { "limits must not be negative" }
    }
}

/** The memory selected for one chunk, both as typed lists and as the [TranslationContext] a provider receives. */
data class RetrievedContext(
    val characters: List<CharacterMemory>,
    val glossary: List<GlossaryMemory>,
    val relationships: List<RelationshipMemory>,
    val story: List<StoryMemory>,
    val world: List<WorldMemory>,
    val style: StyleMemory?,
    val context: TranslationContext,
) {
    val isEmpty: Boolean
        get() = characters.isEmpty() && glossary.isEmpty() && relationships.isEmpty() &&
            story.isEmpty() && world.isEmpty() && style == null
}

/**
 * Picks only the memory a chunk needs instead of sending everything. A fact is relevant when the
 * chunk mentions it: characters by name or alias, glossary terms and world names by their text,
 * relationships when both characters appear, story facts when they involve a character in the
 * chunk or mention one of the matched names or terms. The novel's translation style is always
 * included. Memory of other novels is never read.
 */
class ContextRetriever(private val repository: MemoryRepository) : TranslationContextProvider {

    override fun contextFor(novelId: NovelId, chunkText: String, chapterNumber: Int?): TranslationContext =
        retrieve(novelId, chunkText, RetrievalOptions(chapterNumber = chapterNumber)).context

    fun retrieve(
        novelId: NovelId,
        chunkText: String,
        options: RetrievalOptions = RetrievalOptions(),
    ): RetrievedContext {
        val text = MemoryText.normalize(chunkText)
        fun allowed(status: MemoryStatus) = status == MemoryStatus.CONFIRMED || options.includeUncertain

        val characters = repository.listCharacters(novelId)
            .filter { allowed(it.status) && it.allNames.any { n -> MemoryText.containsTerm(text, MemoryText.normalize(n)) } }
            .take(options.maxCharacters)
        val characterIds = characters.map { it.id }.toSet()

        val glossary = repository.listGlossary(novelId)
            .filter { allowed(it.status) && MemoryText.containsTerm(text, MemoryText.normalize(it.sourceTerm)) }
            .take(options.maxGlossary)

        val relationships = repository.listRelationships(novelId, currentOnly = true)
            .filter { allowed(it.status) && it.fromCharacterId in characterIds && it.toCharacterId in characterIds }

        val mentionTerms = characters.flatMap { it.allNames } + glossary.map { it.sourceTerm }
        val story = repository.listStory(novelId, beforeChapter = options.chapterNumber)
            .filter { allowed(it.status) && isRelevantStory(it, characterIds, mentionTerms) }
            .takeLast(options.maxStory)

        val world = repository.listWorld(novelId)
            .filter { allowed(it.status) && MemoryText.containsTerm(text, MemoryText.normalize(it.name)) }
            .take(options.maxWorld)

        val style = repository.getStyle(novelId)

        return RetrievedContext(
            characters, glossary, relationships, story, world, style,
            buildContext(novelId, characters, glossary, relationships, story, world, style),
        )
    }

    private fun isRelevantStory(entry: StoryMemory, characterIds: Set<String>, mentionTerms: List<String>): Boolean {
        if (entry.characterIds.any { it in characterIds }) return true
        val summary = MemoryText.normalize(entry.summary)
        return mentionTerms.any { MemoryText.containsTerm(summary, MemoryText.normalize(it)) }
    }

    private fun buildContext(
        novelId: NovelId,
        characters: List<CharacterMemory>,
        glossary: List<GlossaryMemory>,
        relationships: List<RelationshipMemory>,
        story: List<StoryMemory>,
        world: List<WorldMemory>,
        style: StyleMemory?,
    ): TranslationContext {
        val nameById = characters.associate { it.id to it.originalName }

        val characterInfos = characters.map { c ->
            val confirmed = c.status == MemoryStatus.CONFIRMED
            val notes = listOfNotNull(
                c.role.ifBlank { null },
                c.personality.ifBlank { null },
                c.notes.ifBlank { null },
                if (confirmed) null else "unconfirmed" + (c.myanmarName?.let { " (proposed Myanmar name: $it)" } ?: ""),
            )
            val speech = listOfNotNull(
                c.speakingStyle.ifBlank { null },
                c.pronouns.ifBlank { null }?.let { "pronouns: $it" },
            )
            CharacterInfo(
                name = c.originalName,
                translatedName = if (confirmed) c.myanmarName else null,
                description = notes.joinToString("; "),
                speechStyle = speech.joinToString("; "),
            )
        }

        val glossaryEntries = glossary
            .filter { it.status == MemoryStatus.CONFIRMED }
            .map { GlossaryEntry(it.sourceTerm, it.translatedTerm) }

        val storyText = story.joinToString("\n") { s ->
            "Chapter ${s.sourceChapter} [${s.kind.name.lowercase()}]: ${s.summary}" +
                (if (s.status == MemoryStatus.CONFIRMED) "" else " (unconfirmed)") +
                (if (s.resolved) " (resolved)" else "")
        }

        val lines = mutableListOf<String>()
        if (style != null) {
            if (style.tone != ToneLevel.UNSPECIFIED) lines += "Tone: ${style.tone.name.lowercase()}"
            if (style.writingStyle.isNotBlank()) lines += "Writing style: ${style.writingStyle}"
            if (style.dialogueStyle.isNotBlank()) lines += "Dialogue style: ${style.dialogueStyle}"
            if (style.pronounPreferences.isNotBlank()) lines += "Pronouns: ${style.pronounPreferences}"
            if (style.namingConventions.isNotBlank()) lines += "Naming conventions: ${style.namingConventions}"
            if (style.translationPreferences.isNotBlank()) lines += "Preferences: ${style.translationPreferences}"
        }
        relationships.forEach { r ->
            val from = nameById[r.fromCharacterId] ?: return@forEach
            val to = nameById[r.toCharacterId] ?: return@forEach
            lines += "Relationship: $from - $to: ${r.type.name.lowercase()}" +
                (if (r.description.isBlank()) "" else " (${r.description})") +
                (if (r.status == MemoryStatus.CONFIRMED) "" else " (unconfirmed)")
        }
        world.forEach { w ->
            lines += "World: ${w.name} (${w.kind.name.lowercase()})" +
                (if (w.description.isBlank()) "" else ": ${w.description}") +
                (if (w.status == MemoryStatus.CONFIRMED) "" else " (unconfirmed)")
        }
        glossary.filter { it.status != MemoryStatus.CONFIRMED }.forEach {
            lines += "Unconfirmed term (do not treat as settled): ${it.sourceTerm} -> ${it.translatedTerm}"
        }

        return TranslationContext(
            novelId = novelId,
            characters = characterInfos,
            glossary = glossaryEntries,
            storyContext = storyText,
            instructions = lines.joinToString("\n"),
        )
    }
}
