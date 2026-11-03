package com.mnb.browser.translation.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Room rows. Enums and ids are stored as plain Strings/Longs so the schema does not depend on
 * Kotlin types; mapping to the Phase 04-I2 models lives in TranslationMappers.kt.
 * Dates are epoch milliseconds. Child tables cascade on delete.
 */

@Entity(tableName = "novels")
data class NovelEntity(
    @PrimaryKey val novelId: String,
    val title: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val style: String,
    val createdAtMillis: Long,
)

/** Original (untranslated) chapter text. [contentHash] is the hash of the current [originalText]. */
@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = NovelEntity::class,
            parentColumns = ["novelId"],
            childColumns = ["novelId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["novelId", "chapterNumber"], unique = true)],
)
data class ChapterEntity(
    @PrimaryKey val chapterId: String,
    val novelId: String,
    val chapterNumber: Int,
    val originalText: String,
    val contentHash: String,
    val updatedAtMillis: Long,
)

/**
 * The current translation of a chapter (one row per chapter). [sourceContentHash] is the hash of
 * the original text that was translated; when it differs from the chapter's current hash the
 * translation is stale.
 */
@Entity(
    tableName = "translations",
    foreignKeys = [
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["chapterId"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TranslationEntity(
    @PrimaryKey val chapterId: String,
    val translatedText: String?,
    val status: String,
    val sourceContentHash: String,
    val providerId: String,
    val modelId: String?,
    val translationVersion: Int,
    val isMock: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

/** One translated piece of a chapter. [contentHash] is the hash of this chunk's [originalText]. */
@Entity(
    tableName = "translation_chunks",
    foreignKeys = [
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["chapterId"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["chapterId", "chunkIndex"], unique = true)],
)
data class TranslationChunkEntity(
    @PrimaryKey val chunkId: String,
    val chapterId: String,
    val chunkIndex: Int,
    val originalText: String,
    val contentHash: String,
    val translatedText: String?,
    val status: String,
    val providerId: String?,
)

@Entity(
    tableName = "translation_jobs",
    foreignKeys = [
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["chapterId"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("chapterId"), Index("status")],
)
data class TranslationJobEntity(
    @PrimaryKey(autoGenerate = true) val jobId: Long,
    val chapterId: String,
    val status: String,
    val totalChunks: Int,
    val completedChunks: Int,
    val errorCode: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
