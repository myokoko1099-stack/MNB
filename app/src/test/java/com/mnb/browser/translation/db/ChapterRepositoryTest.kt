package com.mnb.browser.translation.db

import android.database.sqlite.SQLiteConstraintException
import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.ContentHash
import com.mnb.browser.translation.NovelId
import com.mnb.browser.translation.TranslationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterRepositoryTest : DbTestBase() {
    private val repo get() = ChapterRepository(db)
    private val n1 = NovelId("n1")

    @Test fun newChapterIsCreatedAndReadBackAsPending() {
        NovelRepository(db).save(novelInfo("n1"))
        val result = repo.saveOriginal(ChapterId("c1"), n1, 1, "Hello", 5L)
        assertEquals(ChapterSaveResult.CREATED, result)
        val chapter = repo.get(ChapterId("c1"))!!
        assertEquals("Hello", chapter.originalText)
        assertEquals(1, chapter.chapterNumber)
        assertEquals(TranslationStatus.PENDING, chapter.status)
        assertNull(chapter.translatedText)
        assertEquals(ContentHash.of("Hello"), chapter.contentHash)
    }

    @Test fun savingTheSameTextAgainIsUnchanged() {
        seedChapter(text = "Same")
        assertEquals(ChapterSaveResult.UNCHANGED, repo.saveOriginal(ChapterId("c1"), n1, 1, "Same", 99L))
        assertEquals(10L, repo.get(ChapterId("c1"))!!.updatedAtMillis)
    }

    @Test fun changedTextIsDetectedAndUpdated() {
        seedChapter(text = "Old")
        assertEquals(ChapterSaveResult.TEXT_CHANGED, repo.saveOriginal(ChapterId("c1"), n1, 1, "New", 50L))
        val chapter = repo.get(ChapterId("c1"))!!
        assertEquals("New", chapter.originalText)
        assertEquals(50L, chapter.updatedAtMillis)
    }

    @Test fun chapterNeedsAnExistingNovel() {
        assertThrows(SQLiteConstraintException::class.java) {
            repo.saveOriginal(ChapterId("c1"), NovelId("nope"), 1, "Text", 1L)
        }
        assertNull(repo.get(ChapterId("c1")))
    }

    @Test fun twoChaptersCannotShareANumberInOneNovel() {
        seedChapter(chapterId = "c1", number = 1)
        assertThrows(SQLiteConstraintException::class.java) {
            repo.saveOriginal(ChapterId("c2"), n1, 1, "Other", 1L)
        }
        assertEquals(1, repo.listForNovel(n1).size)
    }

    @Test fun sameNumberIsFineInDifferentNovels() {
        seedChapter(novelId = "n1", chapterId = "c1", number = 1)
        seedChapter(novelId = "n2", chapterId = "c2", number = 1)
        assertEquals(1, repo.listForNovel(NovelId("n2")).size)
    }

    @Test fun chapterIdCannotMoveToAnotherNovel() {
        seedChapter(novelId = "n1", chapterId = "c1")
        NovelRepository(db).save(novelInfo("n2"))
        assertThrows(IllegalArgumentException::class.java) {
            repo.saveOriginal(ChapterId("c1"), NovelId("n2"), 1, "Text", 1L)
        }
    }

    @Test fun invalidDataIsRejectedBeforeTouchingTheDatabase() {
        NovelRepository(db).save(novelInfo("n1"))
        assertThrows(IllegalArgumentException::class.java) { repo.saveOriginal(ChapterId("c1"), n1, 1, "  ", 1L) }
        assertThrows(IllegalArgumentException::class.java) { repo.saveOriginal(ChapterId("c1"), n1, -1, "Text", 1L) }
        assertThrows(IllegalArgumentException::class.java) { repo.saveOriginal(ChapterId("c1"), n1, 1, "Text", -1L) }
        assertTrue(repo.listForNovel(n1).isEmpty())
    }

    @Test fun chaptersAreListedInNumberOrder() {
        seedChapter(chapterId = "c3", number = 3)
        seedChapter(chapterId = "c1", number = 1)
        seedChapter(chapterId = "c2", number = 2)
        assertEquals(listOf(1, 2, 3), repo.listForNovel(n1).map { it.chapterNumber })
    }

    @Test fun deletingANovelDeletesItsChapters() {
        seedChapter()
        NovelRepository(db).delete(n1)
        assertNull(repo.get(ChapterId("c1")))
    }

    @Test fun deleteRemovesTheChapter() {
        seedChapter()
        assertTrue(repo.delete(ChapterId("c1")))
        assertNull(repo.get(ChapterId("c1")))
    }
}
