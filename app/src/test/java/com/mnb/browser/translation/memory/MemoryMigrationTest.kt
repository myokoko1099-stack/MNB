package com.mnb.browser.translation.memory

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.mnb.browser.translation.NovelId
import com.mnb.browser.translation.db.MnbDatabase
import com.mnb.browser.translation.db.MnbMigrations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Builds a genuine version 1 database file (the schema before Phase 04-I6), fills it, then opens it
 * with Room and the real migration list. Room validates the migrated schema against the entities
 * and throws if a table, column, index or foreign key differs, so a pass proves the migration SQL
 * matches the Kotlin entities as well as that old data survives.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MemoryMigrationTest {
    private val v1Schema = listOf(
        "CREATE TABLE IF NOT EXISTS `novels` (`novelId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
            "`sourceLanguage` TEXT NOT NULL, `targetLanguage` TEXT NOT NULL, `style` TEXT NOT NULL, " +
            "`createdAtMillis` INTEGER NOT NULL, PRIMARY KEY(`novelId`))",
        "CREATE TABLE IF NOT EXISTS `chapters` (`chapterId` TEXT NOT NULL, `novelId` TEXT NOT NULL, " +
            "`chapterNumber` INTEGER NOT NULL, `originalText` TEXT NOT NULL, `contentHash` TEXT NOT NULL, " +
            "`updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`chapterId`), " +
            "FOREIGN KEY(`novelId`) REFERENCES `novels`(`novelId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_chapters_novelId_chapterNumber` " +
            "ON `chapters` (`novelId`, `chapterNumber`)",
        "CREATE TABLE IF NOT EXISTS `translations` (`chapterId` TEXT NOT NULL, `translatedText` TEXT, " +
            "`status` TEXT NOT NULL, `sourceContentHash` TEXT NOT NULL, `providerId` TEXT NOT NULL, " +
            "`modelId` TEXT, `translationVersion` INTEGER NOT NULL, `isMock` INTEGER NOT NULL, " +
            "`createdAtMillis` INTEGER NOT NULL, `updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`chapterId`), " +
            "FOREIGN KEY(`chapterId`) REFERENCES `chapters`(`chapterId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE TABLE IF NOT EXISTS `translation_chunks` (`chunkId` TEXT NOT NULL, `chapterId` TEXT NOT NULL, " +
            "`chunkIndex` INTEGER NOT NULL, `originalText` TEXT NOT NULL, `contentHash` TEXT NOT NULL, " +
            "`translatedText` TEXT, `status` TEXT NOT NULL, `providerId` TEXT, PRIMARY KEY(`chunkId`), " +
            "FOREIGN KEY(`chapterId`) REFERENCES `chapters`(`chapterId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_translation_chunks_chapterId_chunkIndex` " +
            "ON `translation_chunks` (`chapterId`, `chunkIndex`)",
        "CREATE TABLE IF NOT EXISTS `translation_jobs` (`jobId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`chapterId` TEXT NOT NULL, `status` TEXT NOT NULL, `totalChunks` INTEGER NOT NULL, " +
            "`completedChunks` INTEGER NOT NULL, `errorCode` TEXT, `createdAtMillis` INTEGER NOT NULL, " +
            "`updatedAtMillis` INTEGER NOT NULL, " +
            "FOREIGN KEY(`chapterId`) REFERENCES `chapters`(`chapterId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_translation_jobs_chapterId` ON `translation_jobs` (`chapterId`)",
        "CREATE INDEX IF NOT EXISTS `index_translation_jobs_status` ON `translation_jobs` (`status`)",
    )

    private fun createVersion1Database(name: String) {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val old = SQLiteDatabase.openOrCreateDatabase(file.path, null)
        try {
            v1Schema.forEach { old.execSQL(it) }
            old.execSQL("INSERT INTO novels VALUES ('legacy', 'Old novel', 'zh', 'my', 'STANDARD', 111)")
            old.execSQL("INSERT INTO chapters VALUES ('legacy-c1', 'legacy', 1, 'old text', 'hash', 222)")
            old.version = 1
        } finally {
            old.close()
        }
    }

    @Test fun upgradingFromVersion1KeepsOldDataAndAddsTheMemoryTables() {
        val name = "memory-migration-1-2.db"
        createVersion1Database(name)
        val context = RuntimeEnvironment.getApplication()

        val db = Room.databaseBuilder(context, MnbDatabase::class.java, name)
            .addMigrations(*MnbMigrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(2, db.openHelper.readableDatabase.version)

            // Version 1 rows survived.
            assertEquals("Old novel", db.novelDao().get("legacy")!!.title)
            assertEquals("old text", db.chapterDao().get("legacy-c1")!!.originalText)

            // Every memory table exists.
            val tables = mutableSetOf<String>()
            db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
                while (c.moveToNext()) tables += c.getString(0)
            }
            assertTrue(
                tables.containsAll(
                    listOf(
                        "memory_characters", "memory_aliases", "memory_glossary", "memory_relationships",
                        "memory_story", "memory_world", "memory_style", "memory_conflicts", "memory_history",
                    ),
                ),
            )

            // The migrated database is usable, including for the novel that existed before.
            val repo = MemoryRepository(db)
            val result = repo.saveCharacter(NovelId("legacy"), CharacterDraft(originalName = "Lin Feng", aliases = listOf("Feng")))
            assertTrue(result is CharacterSaveResult.Created)
            assertNotNull(repo.findCharacter(NovelId("legacy"), "feng"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun deletingAnOldNovelAfterMigrationAlsoRemovesItsMemory() {
        val name = "memory-migration-cascade.db"
        createVersion1Database(name)
        val context = RuntimeEnvironment.getApplication()
        val db = Room.databaseBuilder(context, MnbDatabase::class.java, name)
            .addMigrations(*MnbMigrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = MemoryRepository(db)
            repo.saveGlossary(NovelId("legacy"), "Qi", "ချီ", GlossaryCategory.SPECIAL_TERM)
            db.novelDao().delete("legacy")
            assertEquals(0, repo.listGlossary(NovelId("legacy")).size)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun theMigrationListEndsAtTheCurrentVersion() {
        assertEquals(2, MnbMigrations.CURRENT_VERSION)
        assertEquals(listOf(1 to 2), MnbMigrations.ALL.map { it.startVersion to it.endVersion })
    }
}
