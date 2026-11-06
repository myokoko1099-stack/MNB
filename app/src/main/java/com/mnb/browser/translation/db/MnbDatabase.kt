package com.mnb.browser.translation.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Local store for novels, chapters, translations, chunks, jobs and (since version 2) the Context
 * Memory tables. Room turns SQLite foreign key enforcement on for this database. Never add
 * fallbackToDestructiveMigration: schema changes must ship as a [Migration] in [MnbMigrations].
 */
@Database(
    entities = [
        NovelEntity::class,
        ChapterEntity::class,
        TranslationEntity::class,
        TranslationChunkEntity::class,
        TranslationJobEntity::class,
        MemoryCharacterEntity::class,
        MemoryAliasEntity::class,
        MemoryGlossaryEntity::class,
        MemoryRelationshipEntity::class,
        MemoryStoryEntity::class,
        MemoryWorldEntity::class,
        MemoryStyleEntity::class,
        MemoryConflictEntity::class,
        MemoryHistoryEntity::class,
        BookmarkEntity::class,
        BrowserHistoryEntity::class,
        BrowserTabEntity::class,
    ],
    version = MnbMigrations.CURRENT_VERSION,
    exportSchema = true,
)
abstract class MnbDatabase : RoomDatabase() {
    abstract fun novelDao(): NovelDao
    abstract fun chapterDao(): ChapterDao
    abstract fun translationDao(): TranslationDao
    abstract fun translationChunkDao(): TranslationChunkDao
    abstract fun translationJobDao(): TranslationJobDao
    abstract fun memoryCharacterDao(): MemoryCharacterDao
    abstract fun memoryAliasDao(): MemoryAliasDao
    abstract fun memoryGlossaryDao(): MemoryGlossaryDao
    abstract fun memoryRelationshipDao(): MemoryRelationshipDao
    abstract fun memoryStoryDao(): MemoryStoryDao
    abstract fun memoryWorldDao(): MemoryWorldDao
    abstract fun memoryStyleDao(): MemoryStyleDao
    abstract fun memoryConflictDao(): MemoryConflictDao
    abstract fun memoryHistoryDao(): MemoryHistoryDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun browserHistoryDao(): BrowserHistoryDao
    abstract fun browserTabDao(): BrowserTabDao

    companion object {
        const val NAME = "mnb.db"

        @Volatile private var instance: MnbDatabase? = null

        fun getInstance(context: Context): MnbDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, MnbDatabase::class.java, NAME)
                    .addMigrations(*MnbMigrations.ALL)
                    .build()
                    .also { instance = it }
            }
    }
}

/**
 * Every schema change after version 1 goes here, one [Migration] per version step, and
 * [CURRENT_VERSION] is raised to match. The exported schema JSON for each version stays in git.
 */
object MnbMigrations {
    const val CURRENT_VERSION = 4

    /** Version 2 (Phase 04-I6): adds the Context Memory tables. Existing tables and rows are untouched. */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MemorySchemaV2.STATEMENTS.forEach { db.execSQL(it) }
        }
    }

    /**
     * Version 3 (Phase 04-I9-B1): adds translation_jobs.priority. ADD COLUMN keeps every existing
     * row; they all get the default 1, which is NORMAL.
     */
    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE translation_jobs ADD COLUMN priority INTEGER NOT NULL DEFAULT 1")
        }
    }

    /** Phase 2A: browser bookmarks, visit history and persisted tab metadata. */
    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS bookmarks (" +
                    "bookmarkId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "url TEXT NOT NULL, title TEXT NOT NULL, createdAtMillis INTEGER NOT NULL)"
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_bookmarks_url ON bookmarks(url)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS browser_history (" +
                    "historyId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "url TEXT NOT NULL, title TEXT NOT NULL, visitedAtMillis INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_browser_history_visitedAtMillis " +
                    "ON browser_history(visitedAtMillis)"
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS browser_tabs (" +
                    "tabId TEXT NOT NULL, url TEXT NOT NULL, title TEXT NOT NULL, " +
                    "position INTEGER NOT NULL, lastAccessedAtMillis INTEGER NOT NULL, " +
                    "isActive INTEGER NOT NULL, PRIMARY KEY(tabId))"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_browser_tabs_position ON browser_tabs(position)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_browser_tabs_isActive ON browser_tabs(isActive)")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
