package com.mnb.browser.translation.db

import com.mnb.browser.translation.NovelId
import com.mnb.browser.translation.SourceLanguage
import com.mnb.browser.translation.TranslationStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelRepositoryTest : DbTestBase() {
    private val repo get() = NovelRepository(db)

    @Test fun insertAndReadBack() {
        val info = novelInfo("n1").copy(sourceLanguage = SourceLanguage.KOREAN, style = TranslationStyle.NATURAL)
        repo.save(info)
        assertEquals(info, repo.get(NovelId("n1")))
    }

    @Test fun unknownNovelIsNull() {
        assertNull(repo.get(NovelId("missing")))
    }

    @Test fun savingTheSameIdUpdatesInsteadOfDuplicating() {
        repo.save(novelInfo("n1"))
        repo.save(novelInfo("n1").copy(novelTitle = "Renamed"))
        assertEquals(1, repo.getAll().size)
        assertEquals("Renamed", repo.get(NovelId("n1"))?.novelTitle)
    }

    @Test fun updatingANovelKeepsItsChapters() {
        seedChapter()
        repo.save(novelInfo("n1").copy(novelTitle = "Renamed"))
        assertEquals(1, db.chapterDao().listForNovel("n1").size)
    }

    @Test fun listIsOldestFirst() {
        repo.save(novelInfo("b", createdAt = 200L))
        repo.save(novelInfo("a", createdAt = 100L))
        assertEquals(listOf("a", "b"), repo.getAll().map { it.novelId.value })
    }

    @Test fun deleteRemovesTheNovelOnce() {
        repo.save(novelInfo("n1"))
        assertTrue(repo.delete(NovelId("n1")))
        assertFalse(repo.delete(NovelId("n1")))
        assertNull(repo.get(NovelId("n1")))
    }

    @Test fun unsupportedStoredLanguageIsSkipped() {
        repo.save(novelInfo("good"))
        db.novelDao().upsert(novelInfo("bad").toEntity().copy(sourceLanguage = "fr"))
        assertEquals(listOf("good"), repo.getAll().map { it.novelId.value })
        assertNull(repo.get(NovelId("bad")))
    }
}
