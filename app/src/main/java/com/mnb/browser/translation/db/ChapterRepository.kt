package com.mnb.browser.translation.db

import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.ChapterTranslation
import com.mnb.browser.translation.ContentHash
import com.mnb.browser.translation.NovelId
import com.mnb.browser.translation.TranslationStatus
import java.util.concurrent.Callable

enum class ChapterSaveResult { CREATED, UNCHANGED, TEXT_CHANGED }

/** Stores original chapter text. Blocking: call off the main thread. */
class ChapterRepository(private val db: MnbDatabase) {
    private val chapterDao = db.chapterDao()
    private val translationDao = db.translationDao()
    private val chunkDao = db.translationChunkDao()
    private val jobDao = db.translationJobDao()

    /**
     * Saves a chapter's original text, comparing content hashes.
     *
     * - new chapter: [ChapterSaveResult.CREATED]
     * - same text: [ChapterSaveResult.UNCHANGED], nothing is written
     * - changed text: the text is updated and, in the same transaction, its chunks are deleted,
     *   its translation is set back to PENDING (the old translation stays marked stale by its
     *   source hash) and its unfinished jobs are cancelled: [ChapterSaveResult.TEXT_CHANGED]
     *
     * Throws IllegalArgumentException for invalid values or a chapter id that belongs to another
     * novel, and SQLiteConstraintException when the novel does not exist or the novel already has
     * a different chapter with this number.
     */
    fun saveOriginal(
        chapterId: ChapterId,
        novelId: NovelId,
        chapterNumber: Int,
        originalText: String,
        nowMillis: Long,
    ): ChapterSaveResult {
        // Constructing the model validates the arguments before the database is touched.
        ChapterTranslation(chapterId, novelId, chapterNumber, originalText, nowMillis)
        val hash = ContentHash.of(originalText).value
        return db.runInTransaction(
            Callable<ChapterSaveResult> {
                val existing = chapterDao.get(chapterId.value)
                when {
                    existing == null -> {
                        chapterDao.upsert(
                            ChapterEntity(chapterId.value, novelId.value, chapterNumber, originalText, hash, nowMillis),
                        )
                        ChapterSaveResult.CREATED
                    }
                    existing.novelId != novelId.value ->
                        throw IllegalArgumentException("Chapter ${chapterId.value} belongs to a different novel")
                    existing.contentHash == hash -> ChapterSaveResult.UNCHANGED
                    else -> {
                        chapterDao.upsert(
                            existing.copy(originalText = originalText, contentHash = hash, updatedAtMillis = nowMillis),
                        )
                        chunkDao.deleteForChapter(chapterId.value)
                        translationDao.setStatus(chapterId.value, TranslationStatus.PENDING.name, nowMillis)
                        jobDao.cancelUnfinished(chapterId.value, nowMillis)
                        ChapterSaveResult.TEXT_CHANGED
                    }
                }
            },
        )
    }

    /** The chapter joined with its translation state (PENDING when it has no translation yet). */
    fun get(chapterId: ChapterId): ChapterTranslation? {
        val chapter = chapterDao.get(chapterId.value) ?: return null
        return chapter.toModel(translationDao.get(chapterId.value))
    }

    /** All chapters of a novel in chapter-number order. */
    fun listForNovel(novelId: NovelId): List<ChapterTranslation> =
        chapterDao.listForNovel(novelId.value).map { it.toModel(translationDao.get(it.chapterId)) }

    /** Deletes the chapter and, by cascade, its translation, chunks and jobs. */
    fun delete(chapterId: ChapterId): Boolean = chapterDao.delete(chapterId.value) > 0
}
