package com.mnb.browser.translation.db

import androidx.room.Room
import com.mnb.browser.translation.NovelId
import java.util.concurrent.Callable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.RuntimeEnvironment

class MnbDatabaseTest : DbTestBase() {
    @Test fun databaseIsCreatedAtTheCurrentVersion() {
        assertTrue(db.openHelper.writableDatabase.isOpen)
        assertEquals(4, db.openHelper.readableDatabase.version)
        assertEquals(MnbMigrations.CURRENT_VERSION, db.openHelper.readableDatabase.version)
    }

    @Test fun foreignKeysAreEnforced() {
        db.openHelper.writableDatabase.query("PRAGMA foreign_keys").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
    }

    @Test fun dataSurvivesClosingAndReopeningTheDatabase() {
        val context = RuntimeEnvironment.getApplication()
        val name = "persistence-test.db"
        context.deleteDatabase(name)

        val first = Room.databaseBuilder(context, MnbDatabase::class.java, name).allowMainThreadQueries().build()
        NovelRepository(first).save(novelInfo("persisted"))
        first.close()

        val second = Room.databaseBuilder(context, MnbDatabase::class.java, name).allowMainThreadQueries().build()
        assertNotNull(NovelRepository(second).get(NovelId("persisted")))
        second.close()
        context.deleteDatabase(name)
    }

    @Test fun successfulTransactionIsCommitted() {
        db.runInTransaction(Callable<Unit> { db.novelDao().upsert(novelInfo("n1").toEntity()) })
        assertNotNull(db.novelDao().get("n1"))
    }

    @Test fun failedTransactionIsRolledBack() {
        try {
            db.runInTransaction(
                Callable<Unit> {
                    db.novelDao().upsert(novelInfo("n1").toEntity())
                    throw IllegalStateException("boom")
                },
            )
        } catch (expected: IllegalStateException) {
            // expected
        }
        assertNull(db.novelDao().get("n1"))
    }
}
