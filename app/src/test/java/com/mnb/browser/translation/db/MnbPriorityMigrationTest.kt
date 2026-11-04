package com.mnb.browser.translation.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Migration 2 -> 3 (Phase 04-I9-B1) on a version 2 shaped translation_jobs table. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MnbPriorityMigrationTest {
    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var sqlite: SupportSQLiteDatabase

    @Before fun openVersion2() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE translation_jobs (" +
                            "jobId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "chapterId TEXT NOT NULL, status TEXT NOT NULL, " +
                            "totalChunks INTEGER NOT NULL, completedChunks INTEGER NOT NULL, " +
                            "errorCode TEXT, createdAtMillis INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL)",
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        helper = FrameworkSQLiteOpenHelperFactory().create(config)
        sqlite = helper.writableDatabase
        sqlite.execSQL(
            "INSERT INTO translation_jobs (chapterId, status, totalChunks, completedChunks, errorCode, createdAtMillis, updatedAtMillis) " +
                "VALUES ('c1', 'PROCESSING', 3, 1, NULL, 10, 20)",
        )
    }

    @After fun close() {
        helper.close()
    }

    @Test fun existingJobsKeepTheirDataAndBecomeNormal() {
        MnbMigrations.MIGRATION_2_3.migrate(sqlite)
        sqlite.query(
            "SELECT chapterId, status, totalChunks, completedChunks, createdAtMillis, updatedAtMillis, priority FROM translation_jobs",
        ).use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("c1", c.getString(0))
            assertEquals("PROCESSING", c.getString(1))
            assertEquals(3, c.getInt(2))
            assertEquals(1, c.getInt(3))
            assertEquals(10L, c.getLong(4))
            assertEquals(20L, c.getLong(5))
            assertEquals(1, c.getInt(6)) // NORMAL
        }
    }

    @Test fun rowsInsertedAfterTheMigrationCanCarryAPriority() {
        MnbMigrations.MIGRATION_2_3.migrate(sqlite)
        sqlite.execSQL(
            "INSERT INTO translation_jobs (chapterId, status, totalChunks, completedChunks, errorCode, createdAtMillis, updatedAtMillis, priority) " +
                "VALUES ('c2', 'PENDING', 0, 0, NULL, 30, 30, 2)",
        )
        sqlite.execSQL(
            "INSERT INTO translation_jobs (chapterId, status, totalChunks, completedChunks, errorCode, createdAtMillis, updatedAtMillis) " +
                "VALUES ('c3', 'PENDING', 0, 0, NULL, 40, 40)",
        )
        sqlite.query("SELECT chapterId, priority FROM translation_jobs ORDER BY jobId").use { c ->
            val seen = mutableListOf<Pair<String, Int>>()
            while (c.moveToNext()) seen += c.getString(0) to c.getInt(1)
            assertEquals(listOf("c1" to 1, "c2" to 2, "c3" to 1), seen)
        }
    }
}
