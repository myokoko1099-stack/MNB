package com.mnb.browser.translation.db

import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.ContentHash
import com.mnb.browser.translation.TranslationChunk
import com.mnb.browser.translation.TranslationMetadata
import com.mnb.browser.translation.TranslationStatus
import java.util.concurrent.Callable

/** A chapter's stored translation. [sourceContentHash] is the hash of the original text it was made from. */
data class StoredTranslation(
    val chapterId: ChapterId,
    val translatedText: String?,
    val status: TranslationStatus,
    val sourceContentHash: ContentHash,
    val metadata: TranslationMetadata,
)

/** Stores translated chapters and their chunks. Blocking: call off the main thread. */
class TranslationRepository(private val db: MnbDatabase) {
    private val chapterDao = db.chapterDao()
    private val translationDao = db.translationDao()
    private val chunkDao = db.translationChunkDao()

    /**
     * Saves (or replaces) the translation of a chapter, recording the chapter's current content
     * hash as its source. Throws IllegalArgumentException for a COMPLETED translation without
     * text and IllegalStateException when the chapter does not exist.
     */
    fun saveTranslation(
        chapterId: ChapterId,
        translatedText: String?,
        status: TranslationStatus,
        metadata: TranslationMetadata,
    ) {
        require(status != TranslationStatus.COMPLETED || !translatedText.isNullOrBlank()) {
            "A COMPLETED translation needs translatedText"
        }
        db.runInTransaction(
            Callable<Unit> {
                val chapter = checkNotNull(chapterDao.get(chapterId.value)) { "Chapter ${chapterId.value} not found" }
                translationDao.upsert(
                    TranslationEntity(
                        chapterId = chapterId.value,
                        translatedText = translatedText,
                        status = status.name,
                        sourceContentHash = chapter.contentHash,
                        providerId = metadata.providerId,
                        modelId = metadata.modelId,
                        translationVersion = metadata.translationVersion,
                        isMock = metadata.isMock,
                        createdAtMillis = metadata.createdAtMillis,
                        updatedAtMillis = metadata.updatedAtMillis,
                    ),
                )
            },
        )
    }

    fun getTranslation(chapterId: ChapterId): StoredTranslation? {
        val e = translationDao.get(chapterId.value) ?: return null
        return StoredTranslation(
            chapterId = chapterId,
            translatedText = e.translatedText,
            status = TranslationStatus.fromName(e.status) ?: TranslationStatus.PENDING,
            sourceContentHash = ContentHash(e.sourceContentHash),
            metadata = e.toMetadata(),
        )
    }

    /** True when a translation exists but the chapter's original text has changed since. */
    fun isStale(chapterId: ChapterId): Boolean {
        val chapter = chapterDao.get(chapterId.value) ?: return false
        val translation = translationDao.get(chapterId.value) ?: return false
        return translation.sourceContentHash != chapter.contentHash
    }

    /**
     * Saves a chunk. A different chunk already stored at the same chapter and index is replaced.
     * [providerId] records which provider produced the translation. Throws
     * SQLiteConstraintException when the chapter does not exist.
     */
    fun saveChunk(chunk: TranslationChunk, providerId: String? = null) {
        require(providerId == null || providerId.isNotBlank()) { "providerId must be null or not blank" }
        db.runInTransaction(
            Callable<Unit> {
                val atIndex = chunkDao.findByIndex(chunk.chapterId.value, chunk.chunkIndex)
                if (atIndex != null && atIndex.chunkId != chunk.chunkId.value) {
                    chunkDao.delete(atIndex.chunkId)
                }
                chunkDao.upsert(chunk.toEntity(providerId))
            },
        )
    }

    fun getChunks(chapterId: ChapterId): List<TranslationChunk> =
        chunkDao.listForChapter(chapterId.value).map { it.toModel() }

    fun completedChunkCount(chapterId: ChapterId): Int =
        chunkDao.countWithStatus(chapterId.value, TranslationStatus.COMPLETED.name)

    /**
     * The already-translated chunk at [chunkIndex], or null when it must be translated again:
     * not stored, not COMPLETED, original text changed (hash differs) or made by another provider
     * (so mock output is never reused as a real translation).
     */
    fun findReusableChunk(
        chapterId: ChapterId,
        chunkIndex: Int,
        originalText: String,
        providerId: String,
    ): TranslationChunk? {
        val e = chunkDao.findByIndex(chapterId.value, chunkIndex) ?: return null
        val reusable = TranslationStatus.fromName(e.status) == TranslationStatus.COMPLETED &&
            e.contentHash == ContentHash.of(originalText).value &&
            e.providerId == providerId
        return if (reusable) e.toModel() else null
    }
}
