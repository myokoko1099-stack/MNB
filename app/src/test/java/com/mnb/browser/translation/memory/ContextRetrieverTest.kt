package com.mnb.browser.translation.memory

import com.mnb.browser.translation.GlossaryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ContextRetrieverTest : MemoryTestBase() {
    private lateinit var retriever: ContextRetriever
    private lateinit var lin: CharacterMemory
    private lateinit var su: CharacterMemory
    private lateinit var wang: CharacterMemory

    @Before fun seedStory() {
        retriever = ContextRetriever(repo)
        lin = confirmedCharacter(n1, "Lin Feng", "လင်ဖုန်း", listOf("Young Master Lin"))
        su = confirmedCharacter(n1, "Su Mei", "ဆူမေ")
        wang = confirmedCharacter(n1, "Wang Hu", "ဝမ်ဟူ")

        repo.saveRelationship(n1, lin.id, su.id, RelationshipType.FRIENDSHIP, "childhood friends", MemoryStatus.CONFIRMED, 2)
        repo.saveRelationship(n1, lin.id, wang.id, RelationshipType.ENMITY, "blood feud", MemoryStatus.CONFIRMED, 3)

        repo.saveGlossary(n1, "Azure Sect", "ကောင်းကင်ပြာဂိုဏ်း", GlossaryCategory.ORGANIZATION_NAME, MemoryStatus.CONFIRMED, 1)
        repo.saveGlossary(n1, "Jade City", "ကျောက်စိမ်းမြို့", GlossaryCategory.PLACE_NAME, MemoryStatus.CONFIRMED, 1)

        repo.addStory(n1, StoryKind.PROMISE, "Lin Feng promised Su Mei to return", 4, listOf(lin.id, su.id), MemoryStatus.CONFIRMED)
        repo.addStory(n1, StoryKind.EVENT, "Wang Hu burned the granary", 5, listOf(wang.id), MemoryStatus.CONFIRMED)
        repo.addStory(n1, StoryKind.EVENT, "The Azure Sect closed its gates", 6, emptyList(), MemoryStatus.CONFIRMED)
        repo.addStory(n1, StoryKind.EVENT, "A later twist about Lin Feng", 30, listOf(lin.id), MemoryStatus.CONFIRMED)

        repo.saveWorld(n1, WorldKind.CITY, "Jade City", "capital of Yan", MemoryStatus.CONFIRMED, 1)
        repo.saveWorld(n1, WorldKind.KINGDOM, "Kingdom of Wei", "far away", MemoryStatus.CONFIRMED, 1)

        repo.saveStyle(n1, StyleMemory(tone = ToneLevel.FORMAL, writingStyle = "literary", pronounPreferences = "ကျွန်တော်"))
    }

    private val chunk = "Lin Feng looked at Su Mei and said nothing about the Azure Sect."

    @Test fun retrievesOnlyWhatTheChunkNeeds() {
        val r = retriever.retrieve(n1, chunk, RetrievalOptions(chapterNumber = 10))

        assertEquals(listOf("Lin Feng", "Su Mei"), r.characters.map { it.originalName })
        assertEquals(listOf("Azure Sect"), r.glossary.map { it.sourceTerm })
        assertEquals(listOf(RelationshipType.FRIENDSHIP), r.relationships.map { it.type })
        assertTrue(r.world.isEmpty())
        assertEquals(
            listOf("Lin Feng promised Su Mei to return", "The Azure Sect closed its gates"),
            r.story.map { it.summary },
        )
        assertFalse(r.isEmpty)
    }

    @Test fun contextIsBuiltForTheProvider() {
        val ctx = retriever.retrieve(n1, chunk, RetrievalOptions(chapterNumber = 10)).context

        assertEquals(n1, ctx.novelId)
        assertEquals(listOf("Lin Feng", "Su Mei"), ctx.characters.map { it.name })
        assertEquals(listOf("လင်ဖုန်း", "ဆူမေ"), ctx.characters.map { it.translatedName })
        assertEquals(listOf(GlossaryEntry("Azure Sect", "ကောင်းကင်ပြာဂိုဏ်း")), ctx.glossary)
        assertTrue(ctx.storyContext.contains("Chapter 4 [promise]: Lin Feng promised Su Mei to return"))
        assertTrue(ctx.instructions.contains("Tone: formal"))
        assertTrue(ctx.instructions.contains("Writing style: literary"))
        assertTrue(ctx.instructions.contains("Relationship: Lin Feng - Su Mei: friendship (childhood friends)"))
        assertFalse(ctx.instructions.contains("Wang Hu"))
        assertFalse(ctx.isEmpty)
    }

    @Test fun charactersAreFoundByAliasAndAnotherCharactersFactsAreLeftOut() {
        val r = retriever.retrieve(n1, "Young Master Lin walked alone.")
        assertEquals(listOf("Lin Feng"), r.characters.map { it.originalName })
        assertTrue(r.relationships.isEmpty())
        assertTrue(r.glossary.isEmpty())
        assertTrue(r.story.any { it.summary.startsWith("Lin Feng promised") })
        assertFalse(r.story.any { it.summary.contains("Wang Hu") })
    }

    @Test fun storyFromTheCurrentOrLaterChaptersIsNotSentAsPreviousContext() {
        val early = retriever.retrieve(n1, "Lin Feng smiled.", RetrievalOptions(chapterNumber = 5))
        assertEquals(listOf("Lin Feng promised Su Mei to return"), early.story.map { it.summary })
        val late = retriever.retrieve(n1, "Lin Feng smiled.", RetrievalOptions(chapterNumber = 31))
        assertTrue(late.story.any { it.summary.startsWith("A later twist") })
    }

    @Test fun limitsCapTheAmountOfContext() {
        val r = retriever.retrieve(n1, "Lin Feng, Su Mei and Wang Hu met.", RetrievalOptions(maxCharacters = 2, maxStory = 1))
        assertEquals(2, r.characters.size)
        assertEquals(1, r.story.size)
    }

    @Test fun worldKnowledgeAppearsWhenTheChunkMentionsIt() {
        val r = retriever.retrieve(n1, "They rode into Jade City at dusk.")
        assertEquals(listOf("Jade City"), r.world.map { it.name })
        assertEquals(listOf("Jade City"), r.glossary.map { it.sourceTerm })
        assertTrue(r.context.instructions.contains("World: Jade City (city): capital of Yan"))
    }

    @Test fun styleIsAlwaysIncludedEvenWhenNothingElseMatches() {
        val r = retriever.retrieve(n1, "The wind rose over the empty hills.")
        assertTrue(r.characters.isEmpty() && r.glossary.isEmpty() && r.story.isEmpty() && r.world.isEmpty())
        assertEquals(ToneLevel.FORMAL, r.style!!.tone)
        assertTrue(r.context.instructions.contains("Pronouns: ကျွန်တော်"))
        assertFalse(r.isEmpty)
    }

    @Test fun uncertainMemoryIsLeftOutByDefaultAndLabelledWhenIncluded() {
        repo.saveCharacter(n1, CharacterDraft(originalName = "Mystery Man", myanmarName = "လျှို့ဝှက်"))
        repo.saveGlossary(n1, "Hidden Vale", "ဝှက်ထားသောချိုင့်", GlossaryCategory.PLACE_NAME)
        val text = "Mystery Man entered the Hidden Vale."

        val strict = retriever.retrieve(n1, text)
        assertTrue(strict.characters.isEmpty())
        assertTrue(strict.glossary.isEmpty())

        val loose = retriever.retrieve(n1, text, RetrievalOptions(includeUncertain = true))
        val info = loose.context.characters.single()
        assertNull(info.translatedName)
        assertTrue(info.description.contains("unconfirmed"))
        assertTrue(loose.context.glossary.isEmpty())
        assertTrue(loose.context.instructions.contains("Unconfirmed term (do not treat as settled): Hidden Vale"))
    }

    @Test fun anUpdatedRelationshipOnlySendsTheCurrentOne() {
        val old = repo.relationshipsBetween(n1, lin.id, su.id).single()
        repo.updateRelationship(n1, old.id, RelationshipType.ENMITY, "betrayal", 40, MemoryStatus.CONFIRMED)
        val r = retriever.retrieve(n1, "Lin Feng met Su Mei.")
        assertEquals(listOf(RelationshipType.ENMITY), r.relationships.map { it.type })
    }

    @Test fun otherNovelsMemoryIsNeverRetrieved() {
        val r = retriever.retrieve(n2, chunk)
        assertTrue(r.isEmpty)
    }

    @Test fun emptyMemoryGivesAnEmptyContext() {
        val r = retriever.retrieve(n2, "Anything at all")
        assertTrue(r.isEmpty)
        assertTrue(r.context.isEmpty)
        assertEquals(n2, r.context.novelId)
        val blank = retriever.retrieve(n1, "   ")
        assertTrue(blank.characters.isEmpty() && blank.glossary.isEmpty() && blank.story.isEmpty())
    }

    @Test fun theProviderInterfaceReturnsTheSameContext() {
        val provider: TranslationContextProvider = retriever
        val viaInterface = provider.contextFor(n1, chunk, 10)
        assertEquals(retriever.retrieve(n1, chunk, RetrievalOptions(chapterNumber = 10)).context, viaInterface)
    }
}
