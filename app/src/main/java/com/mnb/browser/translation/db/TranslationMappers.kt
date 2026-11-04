package com.mnb.browser.translation.db

import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.ChapterTranslation
import com.mnb.browser.translation.ChunkId
import com.mnb.browser.translation.ContentHash
import com.mnb.browser.translation.NovelId
import com.mnb.browser.translation.NovelTranslationInfo
import com.mnb.browser.translation.SourceLanguage
import com.mnb.browser.translation.TargetLanguage
import com.mnb.browser.translation.TranslationChunk
import com.mnb.browser.translation.TranslationError
import com.mnb.browser.translation.TranslationJob
import com.mnb.browser.translation.TranslationMetadata
import com.mnb.browser.translation.TranslationPriority
import com.mnb.browser.translation.TranslationStatus
import com.mnb.browser.translation.TranslationStyle

/** An unknown stored status is treated as PENDING so the work is simply redone. */
private fun parseStatus(name: String): TranslationStatus =
    TranslationStatus.fromName(name) ?: TranslationStatus.PENDING

/** Null when the stored source language is not one MNB supports. */
internal fun NovelEntity.toModel(): NovelTranslationInfo? {
    val source = SourceLanguage.fromCode(sourceLanguage) ?: return null
    val target = TargetLanguage.values().firstOrNull { it.code == targetLanguage } ?: TargetLanguage.MYANMAR
    return NovelTranslationInfo(
        novelId = NovelId(novelId),
        novelTitle = title,
        sourceLanguage = source,
        createdAtMillis = createdAtMillis,
        targetLanguage = target,
        style = TranslationStyle.fromName(style) ?: TranslationStyle.LITERARY,
    )
}

internal fun NovelTranslationInfo.toEntity() = NovelEntity(
    novelId = novelId.value,
    title = novelTitle,
    sourceLanguage = sourceLanguage.code,
    targetLanguage = targetLanguage.code,
    style = style.name,
    createdAtMillis = createdAtMillis,
)

/** Joins a chapter with its translation row (if any) into the Phase 04-I2 [ChapterTranslation]. */
internal fun ChapterEntity.toModel(translation: TranslationEntity?): ChapterTranslation = ChapterTranslation(
    chapterId = ChapterId(chapterId),
    novelId = NovelId(novelId),
    chapterNumber = chapterNumber,
    originalText = originalText,
    updatedAtMillis = maxOf(updatedAtMillis, translation?.updatedAtMillis ?: 0L),
    translatedText = translation?.translatedText,
    status = translation?.let { parseStatus(it.status) } ?: TranslationStatus.PENDING,
    contentHash = ContentHash(translation?.sourceContentHash ?: contentHash),
)

internal fun TranslationEntity.toMetadata() = TranslationMetadata(
    providerId = providerId,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    modelId = modelId,
    translationVersion = translationVersion,
    isMock = isMock,
)

internal fun TranslationChunkEntity.toModel() = TranslationChunk(
    chunkId = ChunkId(chunkId),
    chapterId = ChapterId(chapterId),
    chunkIndex = chunkIndex,
    originalText = originalText,
    translatedText = translatedText,
    status = parseStatus(status),
)

internal fun TranslationChunk.toEntity(providerId: String?) = TranslationChunkEntity(
    chunkId = chunkId.value,
    chapterId = chapterId.value,
    chunkIndex = chunkIndex,
    originalText = originalText,
    contentHash = ContentHash.of(originalText).value,
    translatedText = translatedText,
    status = status.name,
    providerId = providerId,
)

internal fun TranslationJobEntity.toModel() = TranslationJob(
    jobId = jobId,
    chapterId = ChapterId(chapterId),
    totalChunks = totalChunks,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    completedChunks = completedChunks,
    status = parseStatus(status),
    errorCode = errorCode?.let { code -> TranslationError.values().firstOrNull { it.name == code } },
    priority = TranslationPriority.fromRank(priority),
)

internal fun TranslationJob.toEntity() = TranslationJobEntity(
    jobId = jobId,
    chapterId = chapterId.value,
    status = status.name,
    totalChunks = totalChunks,
    completedChunks = completedChunks,
    errorCode = errorCode?.name,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    priority = priority.rank,
)
