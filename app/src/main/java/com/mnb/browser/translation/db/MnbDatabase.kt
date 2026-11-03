package com.mnb.browser.translation.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/**
 * Local store for novels, chapters, translations, chunks and jobs. Room turns SQLite foreign key
 * enforcement on for this database. Never add fallbackToDestructiveMigration: schema changes
 * must ship as a [Migration] in [MnbMigrations].
 */
@Database(
    entities = [
        NovelEntity::class,
        ChapterEntity::class,
        TranslationEntity::class,
        TranslationChunkEntity::class,
        TranslationJobEntity::class,
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
    const val CURRENT_VERSION = 1

    val ALL: Array<Migration> = emptyArray()
}
