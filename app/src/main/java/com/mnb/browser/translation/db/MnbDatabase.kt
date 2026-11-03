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
    const val CURRENT_VERSION = 2

    /** Version 2 (Phase 04-I6): adds the Context Memory tables. Existing tables and rows are untouched. */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MemorySchemaV2.STATEMENTS.forEach { db.execSQL(it) }
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
