package com.mnb.browser.translation.memory

import androidx.room.Room
import com.mnb.browser.translation.NovelId
import com.mnb.browser.translation.db.MnbDatabase
import com.mnb.browser.translation.db.NovelRepository
import com.mnb.browser.translation.NovelTranslationInfo
import com.mnb.browser.translation.SourceLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MemoryPersistenceTest {
    @Test fun memorySurvivesClosingAndReopeningTheDatabase() {
        val context = RuntimeEnvironment.getApplication()
        val name = "memory-persistence-test.db"
        context.deleteDatabase(name)
        val novel = NovelId("persisted")

        val first = Room.databaseBuilder(context, MnbDatabase::class.java, name).allowMainThreadQueries().build()
        NovelRepository(first).save(NovelTranslationInfo(novel, "Persisted", SourceLanguage.CHINESE, 1L))
        val repo1 = MemoryRepository(first)
        val character = (repo1.saveCharacter(
            novel,
            CharacterDraft(originalName = "Lin Feng", myanmarName = "လင်ဖုန်း", aliases = listOf("Feng"), status = MemoryStatus.CONFIRMED, sourceChapter = 1),
        ) as CharacterSaveResult.Created).character
        repo1.saveGlossary(novel, "Qi", "ချီ", GlossaryCategory.SPECIAL_TERM, MemoryStatus.CONFIRMED, 2)
        repo1.saveRelationship(
            novel, character.id, repo1.saveCharacter(novel, CharacterDraft(originalName = "Su Mei")).let {
                (it as CharacterSaveResult.Created).character.id
            },
            RelationshipType.FRIENDSHIP,
        )
        repo1.addStory(novel, StoryKind.EVENT, "The gate fell", 3, listOf(character.id))
        repo1.saveWorld(novel, WorldKind.CITY, "Jade City", "capital")
        repo1.saveStyle(novel, StyleMemory(tone = ToneLevel.FORMAL))
        first.close()

        val second = Room.databaseBuilder(context, MnbDatabase::class.java, name).allowMainThreadQueries().build()
        val repo2 = MemoryRepository(second)
        assertEquals(character.id, repo2.findCharacter(novel, "feng")!!.id)
        assertEquals("ချီ", repo2.translationFor(novel, "Qi"))
        assertEquals(1, repo2.listRelationships(novel).size)
        assertEquals(1, repo2.listStory(novel).size)
        assertNotNull(repo2.findWorld(novel, WorldKind.CITY, "Jade City"))
        assertEquals(ToneLevel.FORMAL, repo2.getStyle(novel)!!.tone)
        assertEquals(2, repo2.listCharacters(novel).size)
        second.close()
        context.deleteDatabase(name)
    }
}
