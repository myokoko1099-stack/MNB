package com.mnb.browser.tabs

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The per-tab session files: tabs must never share or overwrite each other's saved history. */
class TabSessionFilesTest {
    private lateinit var root: File
    private lateinit var dir: File
    private lateinit var files: TabSessionFiles

    @Before fun setUp() {
        root = Files.createTempDirectory("mnb-tabs").toFile()
        dir = File(root, "tab_sessions")
        files = TabSessionFiles { dir }
    }

    @After fun tearDown() {
        root.deleteRecursively()
    }

    @Test fun eachTabHasItsOwnSessionAndNothingIsSharedBetweenTabs() {
        val a = files.storeFor("tab-a")
        val b = files.storeFor("tab-b")
        a.save(byteArrayOf(1, 2, 3))
        b.save(byteArrayOf(9, 8))
        assertArrayEquals(byteArrayOf(1, 2, 3), a.load())
        assertArrayEquals(byteArrayOf(9, 8), b.load())
    }

    @Test fun savingAgainReplacesOnlyThatTabsSession() {
        val a = files.storeFor("tab-a")
        val b = files.storeFor("tab-b")
        a.save(byteArrayOf(1))
        b.save(byteArrayOf(2))
        a.save(byteArrayOf(3, 3))
        assertArrayEquals(byteArrayOf(3, 3), a.load())
        assertArrayEquals(byteArrayOf(2), b.load())
    }

    @Test fun aSessionSurvivesANewStoreObject() {
        files.storeFor("tab-a").save(byteArrayOf(5, 6, 7))
        val again = TabSessionFiles { dir }.storeFor("tab-a")
        assertArrayEquals(byteArrayOf(5, 6, 7), again.load())
    }

    @Test fun aTabWithoutASessionLoadsNothing() {
        assertNull(files.storeFor("never-saved").load())
    }

    @Test fun clearingRemovesOnlyThatTabsSession() {
        val a = files.storeFor("tab-a")
        val b = files.storeFor("tab-b")
        a.save(byteArrayOf(1))
        b.save(byteArrayOf(2))
        a.clear()
        assertNull(a.load())
        assertArrayEquals(byteArrayOf(2), b.load())
    }

    @Test fun anIdCannotPointOutsideTheSessionFolder() {
        val file = files.fileFor("../../evil/../x")
        assertEquals(dir.canonicalFile, file.canonicalFile.parentFile)
        assertTrue(file.name.endsWith(".bin"))
        assertFalse(file.name.contains("/"))
        assertFalse(file.name.contains(".."))
    }

    @Test fun pruneRemovesOnlySessionsOfTabsThatAreNotOpen() {
        files.storeFor("keep-1").save(byteArrayOf(1))
        files.storeFor("keep-2").save(byteArrayOf(2))
        files.storeFor("old-1").save(byteArrayOf(3))
        files.storeFor("old-2").save(byteArrayOf(4))
        files.prune(listOf("keep-1", "keep-2"))
        assertArrayEquals(byteArrayOf(1), files.storeFor("keep-1").load())
        assertArrayEquals(byteArrayOf(2), files.storeFor("keep-2").load())
        assertNull(files.storeFor("old-1").load())
        assertNull(files.storeFor("old-2").load())
    }

    @Test fun pruneWithoutAFolderDoesNotFail() {
        TabSessionFiles { File(root, "missing") }.prune(listOf("a"))
    }
}
