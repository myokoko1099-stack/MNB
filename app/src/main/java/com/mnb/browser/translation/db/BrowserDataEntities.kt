package com.mnb.browser.translation.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["url"], unique = true)],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val bookmarkId: Long = 0,
    val url: String,
    val title: String,
    val createdAtMillis: Long,
)

@Entity(
    tableName = "browser_history",
    indices = [Index("visitedAtMillis")],
)
data class BrowserHistoryEntity(
    @PrimaryKey(autoGenerate = true) val historyId: Long = 0,
    val url: String,
    val title: String,
    val visitedAtMillis: Long,
)

@Entity(
    tableName = "browser_tabs",
    indices = [Index("position"), Index("isActive")],
)
data class BrowserTabEntity(
    @PrimaryKey val tabId: String,
    val url: String,
    val title: String,
    val position: Int,
    val lastAccessedAtMillis: Long,
    val isActive: Boolean = false,
)
