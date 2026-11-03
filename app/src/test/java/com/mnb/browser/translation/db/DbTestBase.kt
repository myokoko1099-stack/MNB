package com.mnb.browser.translation.db

import androidx.room.Room
import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.NovelId
import com.mnb.browser.translation.NovelTranslationInfo
import com.mnb.browser.translation.SourceLanguage
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Runs against a real in-memory SQLite database (Robolectric), so foreign keys and transactions are genuine. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
abstract class DbTestBase {
    protected lateinit var db: MnbDatabase

    @Before fun createDb() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MnbDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After fun closeDb() {
        db.close()
    }

    protected fun novelInfo(id: String = "n1", createdAt: Long = 1000L) =
        NovelTranslationInfo(NovelId(id), "Title $id", SourceLanguage.CHINESE, createdAt)

    /** Stores a novel and one chapter; safe to call repeatedly. */
    protected fun seedChapter(
        novelId: String = "n1",
        chapterId: String = "c1",
        number: Int = 1,
        text: String = "Original text",
        now: Long = 10L,
    ): ChapterId {
        NovelRepository(db).save(novelInfo(novelId))
        ChapterRepository(db).saveOriginal(ChapterId(chapterId), NovelId(novelId), number, text, now)
        return ChapterId(chapterId)
    }
}
