package com.mnb.browser.translation.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert

/*
 * DAOs are blocking (the project has no coroutines dependency yet); call them off the main
 * thread. @Upsert is used instead of REPLACE because REPLACE deletes the old row first, which
 * would cascade-delete every child row.
 */

@Dao
interface NovelDao {
    @Upsert fun upsert(novel: NovelEntity)

    @Query("SELECT * FROM novels WHERE novelId = :novelId")
    fun get(novelId: String): NovelEntity?

    @Query("SELECT * FROM novels ORDER BY createdAtMillis, novelId")
    fun getAll(): List<NovelEntity>

    @Query("DELETE FROM novels WHERE novelId = :novelId")
    fun delete(novelId: String): Int
}

@Dao
interface ChapterDao {
    /** Strict insert: a duplicate id or chapter number throws SQLiteConstraintException (@Upsert would swallow it). */
    @Insert fun insert(chapter: ChapterEntity)

    @Update fun update(chapter: ChapterEntity): Int

    @Query("SELECT * FROM chapters WHERE chapterId = :chapterId")
    fun get(chapterId: String): ChapterEntity?

    @Query("SELECT * FROM chapters WHERE novelId = :novelId ORDER BY chapterNumber")
    fun listForNovel(novelId: String): List<ChapterEntity>

    @Query("DELETE FROM chapters WHERE chapterId = :chapterId")
    fun delete(chapterId: String): Int
}

@Dao
interface TranslationDao {
    @Upsert fun upsert(translation: TranslationEntity)

    @Query("SELECT * FROM translations WHERE chapterId = :chapterId")
    fun get(chapterId: String): TranslationEntity?

    @Query("UPDATE translations SET status = :status, updatedAtMillis = :nowMillis WHERE chapterId = :chapterId")
    fun setStatus(chapterId: String, status: String, nowMillis: Long): Int
}

@Dao
interface TranslationChunkDao {
    @Upsert fun upsert(chunk: TranslationChunkEntity)

    @Query("SELECT * FROM translation_chunks WHERE chapterId = :chapterId AND chunkIndex = :chunkIndex")
    fun findByIndex(chapterId: String, chunkIndex: Int): TranslationChunkEntity?

    @Query("SELECT * FROM translation_chunks WHERE chapterId = :chapterId ORDER BY chunkIndex")
    fun listForChapter(chapterId: String): List<TranslationChunkEntity>

    @Query("SELECT COUNT(*) FROM translation_chunks WHERE chapterId = :chapterId AND status = :status")
    fun countWithStatus(chapterId: String, status: String): Int

    @Query("DELETE FROM translation_chunks WHERE chunkId = :chunkId")
    fun delete(chunkId: String): Int

    @Query("DELETE FROM translation_chunks WHERE chapterId = :chapterId")
    fun deleteForChapter(chapterId: String): Int
}

@Dao
interface TranslationJobDao {
    @Insert fun insert(job: TranslationJobEntity): Long

    @Update fun update(job: TranslationJobEntity): Int

    @Query("SELECT * FROM translation_jobs WHERE jobId = :jobId")
    fun get(jobId: Long): TranslationJobEntity?

    @Query("SELECT * FROM translation_jobs WHERE chapterId = :chapterId ORDER BY jobId DESC LIMIT 1")
    fun latestForChapter(chapterId: String): TranslationJobEntity?

    @Query("SELECT * FROM translation_jobs WHERE status IN ('PENDING', 'PROCESSING') ORDER BY createdAtMillis, jobId")
    fun unfinished(): List<TranslationJobEntity>

    /**
     * PENDING jobs in start order (I9-B1): higher priority first, then FIFO. FIFO uses jobId (the
     * insertion order) rather than createdAtMillis, so equal or out-of-order clock values cannot
     * reorder jobs.
     */
    @Query("SELECT * FROM translation_jobs WHERE status = 'PENDING' ORDER BY priority DESC, jobId ASC")
    fun pendingInRunOrder(): List<TranslationJobEntity>

    /** How many PENDING jobs must start before a job with this [jobId] and [priority] (I9-B1). */
    @Query(
        "SELECT COUNT(*) FROM translation_jobs WHERE status = 'PENDING' " +
            "AND (priority > :priority OR (priority = :priority AND jobId < :jobId))",
    )
    fun countOutranking(jobId: Long, priority: Int): Int

    @Query(
        "UPDATE translation_jobs SET status = 'CANCELLED', updatedAtMillis = :nowMillis " +
            "WHERE chapterId = :chapterId AND status IN ('PENDING', 'PROCESSING')",
    )
    fun cancelUnfinished(chapterId: String, nowMillis: Long): Int
}
