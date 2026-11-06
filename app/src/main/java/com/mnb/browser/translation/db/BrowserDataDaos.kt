package com.mnb.browser.translation.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface BookmarkDao {
    @Upsert fun save(row: BookmarkEntity)

    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    fun findByUrl(url: String): BookmarkEntity?

    @Query("SELECT * FROM bookmarks ORDER BY createdAtMillis DESC, bookmarkId DESC")
    fun listAll(): List<BookmarkEntity>

    @Query("DELETE FROM bookmarks WHERE bookmarkId = :id")
    fun delete(id: Long): Int

    @Query("DELETE FROM bookmarks WHERE url = :url")
    fun deleteByUrl(url: String): Int
}

@Dao
interface BrowserHistoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun add(row: BrowserHistoryEntity): Long

    @Query("SELECT * FROM browser_history ORDER BY visitedAtMillis DESC, historyId DESC LIMIT :limit")
    fun recent(limit: Int): List<BrowserHistoryEntity>

    @Query("DELETE FROM browser_history WHERE historyId = :id")
    fun delete(id: Long): Int

    @Query("DELETE FROM browser_history")
    fun clear(): Int
}

@Dao
interface BrowserTabDao {
    @Upsert fun save(row: BrowserTabEntity)

    @Query("SELECT * FROM browser_tabs ORDER BY position ASC, lastAccessedAtMillis DESC")
    fun listAll(): List<BrowserTabEntity>

    @Query("SELECT * FROM browser_tabs WHERE tabId = :tabId LIMIT 1")
    fun get(tabId: String): BrowserTabEntity?

    @Query("UPDATE browser_tabs SET isActive = 0")
    fun clearActive(): Int

    @Query("UPDATE browser_tabs SET isActive = 1, lastAccessedAtMillis = :now WHERE tabId = :tabId")
    fun activate(tabId: String, now: Long): Int

    @Query("DELETE FROM browser_tabs WHERE tabId = :tabId")
    fun delete(tabId: String): Int

    @Query("SELECT COUNT(*) FROM browser_tabs")
    fun count(): Int
}
