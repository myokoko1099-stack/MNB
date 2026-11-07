package com.mnb.browser.translation.db

import android.database.sqlite.SQLiteConstraintException
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Migration 3 -> 4 (Phase 2A tables) on a version 3 shaped database that already holds user data,
 * run with the real [MnbMigrations.MIGRATION_3_4] on real SQLite. This test needs no schema JSON; the
 * schema based check is [MnbSchemaMigrationTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MnbBrowserMigrationTest {
    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var sqlite: SupportSQLiteDatabase

    private data class Column(val name: String, val type: String, val notNull: Int, val primaryKey: Int)

    @Before fun openVersion3() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE translation_jobs (" +
                            "jobId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "chapterId TEXT NOT NULL, status TEXT NOT NULL, " +
                            "totalChunks INTEGER NOT NULL, completedChunks INTEGER NOT NULL, " +
                            "errorCode TEXT, createdAtMillis INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL, " +
                            "priority INTEGER NOT NULL DEFAULT 1)",
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        helper = FrameworkSQLiteOpenHelperFactory().create(config)
        sqlite = helper.writableDatabase
        sqlite.execSQL(
            "INSERT INTO translation_jobs (chapterId, status, totalChunks, completedChunks, errorCode, createdAtMillis, updatedAtMillis, priority) " +
                "VALUES ('c1', 'PROCESSING', 3, 1, NULL, 10, 20, 2)",
        )
    }

    @After fun close() {
        helper.close()
    }

    private fun columns(table: String): List<Column> {
        val found = mutableListOf<Column>()
        sqlite.query("PRAGMA table_info($table)").use { c ->
            while (c.moveToNext()) found += Column(c.getString(1), c.getString(2), c.getInt(3), c.getInt(5))
        }
        return found
    }

    private fun indexSql(table: String): Map<String, String> {
        val found = mutableMapOf<String, String>()
        sqlite.query("SELECT name, sql FROM sqlite_master WHERE type = 'index' AND tbl_name = '$table' AND sql IS NOT NULL")
            .use { c -> while (c.moveToNext()) found[c.getString(0)] = c.getString(1) }
        return found
    }

    @Test fun existingDataIsKept() {
        MnbMigrations.MIGRATION_3_4.migrate(sqlite)
        sqlite.query("SELECT chapterId, status, totalChunks, completedChunks, createdAtMillis, updatedAtMillis, priority FROM translation_jobs")
            .use { c ->
                assertEquals(1, c.count)
                c.moveToFirst()
                assertEquals("c1", c.getString(0))
                assertEquals("PROCESSING", c.getString(1))
                assertEquals(3, c.getInt(2))
                assertEquals(1, c.getInt(3))
                assertEquals(10L, c.getLong(4))
                assertEquals(20L, c.getLong(5))
                assertEquals(2, c.getInt(6))
            }
    }

    @Test fun theBrowserTablesHaveExactlyTheColumnsRoomExpects() {
        MnbMigrations.MIGRATION_3_4.migrate(sqlite)
        assertEquals(
            listOf(
                Column("bookmarkId", "INTEGER", 1, 1),
                Column("url", "TEXT", 1, 0),
                Column("title", "TEXT", 1, 0),
                Column("createdAtMillis", "INTEGER", 1, 0),
            ),
            columns("bookmarks"),
        )
        assertEquals(
            listOf(
                Column("historyId", "INTEGER", 1, 1),
                Column("url", "TEXT", 1, 0),
                Column("title", "TEXT", 1, 0),
                Column("visitedAtMillis", "INTEGER", 1, 0),
            ),
            columns("browser_history"),
        )
        assertEquals(
            listOf(
                Column("tabId", "TEXT", 1, 1),
                Column("url", "TEXT", 1, 0),
                Column("title", "TEXT", 1, 0),
                Column("position", "INTEGER", 1, 0),
                Column("lastAccessedAtMillis", "INTEGER", 1, 0),
                Column("isActive", "INTEGER", 1, 0),
            ),
            columns("browser_tabs"),
        )
    }

    @Test fun theIndexesRoomExpectsExist() {
        MnbMigrations.MIGRATION_3_4.migrate(sqlite)
        val bookmarks = indexSql("bookmarks")
        assertTrue(bookmarks.getValue("index_bookmarks_url").contains("UNIQUE", ignoreCase = true))
        assertTrue(indexSql("browser_history").containsKey("index_browser_history_visitedAtMillis"))
        assertEquals(
            setOf("index_browser_tabs_position", "index_browser_tabs_isActive"),
            indexSql("browser_tabs").keys,
        )
    }

    @Test fun theNewTablesWorkAndBookmarkAddressesStayUnique() {
        MnbMigrations.MIGRATION_3_4.migrate(sqlite)
        sqlite.execSQL("INSERT INTO bookmarks (url, title, createdAtMillis) VALUES ('https://a.test/', 'A', 1)")
        try {
            sqlite.execSQL("INSERT INTO bookmarks (url, title, createdAtMillis) VALUES ('https://a.test/', 'A again', 2)")
            fail("a second bookmark with the same address must be refused")
        } catch (expected: SQLiteConstraintException) {
            // unique index works
        }
        sqlite.execSQL("INSERT INTO browser_history (url, title, visitedAtMillis) VALUES ('https://a.test/', 'A', 5)")
        sqlite.execSQL(
            "INSERT INTO browser_tabs (tabId, url, title, position, lastAccessedAtMillis, isActive) " +
                "VALUES ('t1', 'https://a.test/', 'A', 0, 5, 1)",
        )
        sqlite.query("SELECT (SELECT COUNT(*) FROM bookmarks), (SELECT COUNT(*) FROM browser_history), (SELECT COUNT(*) FROM browser_tabs)")
            .use { c ->
                c.moveToFirst()
                assertEquals(1, c.getInt(0))
                assertEquals(1, c.getInt(1))
                assertEquals(1, c.getInt(2))
            }
    }

    @Test fun runningTheMigrationTwiceKeepsEverything() {
        MnbMigrations.MIGRATION_3_4.migrate(sqlite)
        sqlite.execSQL("INSERT INTO browser_history (url, title, visitedAtMillis) VALUES ('https://a.test/', 'A', 5)")
        MnbMigrations.MIGRATION_3_4.migrate(sqlite)
        sqlite.query("SELECT COUNT(*) FROM browser_history").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
        sqlite.query("SELECT COUNT(*) FROM translation_jobs").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
    }

    @Test fun theMigrationChainStillEndsAtTheCurrentVersion() {
        assertEquals(4, MnbMigrations.CURRENT_VERSION)
        assertFalse(MnbMigrations.ALL.isEmpty())
        assertEquals(3, MnbMigrations.ALL.last().startVersion)
        assertEquals(4, MnbMigrations.ALL.last().endVersion)
    }
}
