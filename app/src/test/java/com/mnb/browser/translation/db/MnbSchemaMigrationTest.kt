package com.mnb.browser.translation.db

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Migration 3 -> 4 checked against the exported Room schema JSON with [MigrationTestHelper]: the
 * migrated database must match the version 4 schema exactly.
 *
 * It needs app/schemas/<database class>/3.json and 4.json. Room writes 4.json by itself when the app is
 * built; 3.json is made from it with tools/derive-v3-schema.py (see PHASE-2G-REPORT.md). Until both are
 * committed this test is SKIPPED (not passed): check the test report for skipped tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MnbSchemaMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MnbDatabase::class.java)

    @Before fun requireTheSchemaFiles() {
        assumeTrue("schema JSON for version 3 is not committed yet", schemaAssetExists(3))
        assumeTrue("schema JSON for version 4 is not committed yet", schemaAssetExists(4))
    }

    private fun schemaAssetExists(version: Int): Boolean = try {
        RuntimeEnvironment.getApplication().assets.open("$SCHEMA_DIR/$version.json").close()
        true
    } catch (e: IOException) {
        false
    }

    private fun tableNames(db: SupportSQLiteDatabase): Set<String> {
        val names = mutableSetOf<String>()
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' " +
                "AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%' AND name <> 'room_master_table'",
        ).use { c -> while (c.moveToNext()) names += c.getString(0) }
        return names
    }

    @Test fun version3MigratesToTheExactVersion4SchemaAndKeepsEveryOldTable() {
        val before = helper.createDatabase(DB, 3).use { tableNames(it) }
        helper.runMigrationsAndValidate(DB, 4, true, MnbMigrations.MIGRATION_3_4).use { migrated ->
            val after = tableNames(migrated)
            assertTrue(after.containsAll(before))
            assertEquals(setOf("bookmarks", "browser_history", "browser_tabs"), after - before)
        }
    }

    @Test fun theMigratedDatabaseOpensInRoomAndTheBrowserTablesWork() {
        helper.createDatabase(DB, 3).close()
        val room = Room.databaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MnbDatabase::class.java, DB)
            .addMigrations(*MnbMigrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = BrowserDataRepository(room)
            repo.recordVisit("https://a.example.com/", "A", 10L)
            repo.saveBookmark("https://a.example.com/", "A", 11L)
            assertEquals(1, repo.getRecentHistory().size)
            assertTrue(repo.isBookmarked("https://a.example.com/"))
        } finally {
            room.close()
        }
    }

    private companion object {
        const val DB = "mnb-migration-test"
        val SCHEMA_DIR: String = MnbDatabase::class.java.canonicalName!!
    }
}
