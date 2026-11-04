package com.mnb.browser.reader.translation

import com.mnb.browser.translation.NovelTranslationInfo
import com.mnb.browser.translation.SourceLanguage
import com.mnb.browser.translation.TranslationProvider
import com.mnb.browser.translation.chunking.ChapterChunkRepository
import com.mnb.browser.translation.db.ChapterRepository
import com.mnb.browser.translation.db.MnbDatabase
import com.mnb.browser.translation.db.NovelRepository
import com.mnb.browser.translation.db.TranslationRepository
import com.mnb.browser.translation.memory.MemoryRepository
import com.mnb.browser.translation.usecase.InstantTranslationPipeline

/**
 * Builds the existing [InstantTranslationPipeline] on top of the existing repositories (Phase
 * 04-I10-C1); no pipeline logic lives here. [database] is called only when a usable provider
 * exists, so the database is not opened while translation is not configured. The pipeline is cached
 * per provider instance because its duplicate-request guard lives inside the pipeline object.
 */
class InstantTranslationWiring(private val database: () -> MnbDatabase) {
    private var cachedProvider: TranslationProvider? = null
    private var cachedBackend: TranslationBackend? = null

    @Synchronized
    fun backendFor(provider: TranslationProvider): TranslationBackend? {
        if (cachedProvider === provider) return cachedBackend
        return try {
            val db = database()
            val pipeline = InstantTranslationPipeline(
                provider = provider,
                chapters = ChapterRepository(db),
                chunkRepository = ChapterChunkRepository(db),
                translations = TranslationRepository(db),
                memory = MemoryRepository(db),
            )
            val backend = TranslationBackend(
                runner = InstantTranslationRunner { request, now, cancelled -> pipeline.translate(request, now, cancelled) },
                novels = RoomNovelRegistrar(NovelRepository(db)),
            )
            cachedProvider = provider
            cachedBackend = backend
            backend
        } catch (e: Exception) {
            null
        }
    }
}

/** Creates the bound novel on first use; refuses a novel stored with a different source language. */
class RoomNovelRegistrar(private val novels: NovelRepository) : NovelRegistrar {
    override fun ensure(binding: NovelBinding, nowMillis: Long): NovelEnsureResult = try {
        val language = SourceLanguage.fromCode(binding.sourceLanguageCode)
        val existing = novels.get(binding.novelId)
        when {
            language == null -> NovelEnsureResult.SOURCE_LANGUAGE_MISMATCH
            existing != null ->
                if (existing.sourceLanguage == language) NovelEnsureResult.READY else NovelEnsureResult.SOURCE_LANGUAGE_MISMATCH
            else -> {
                novels.save(NovelTranslationInfo(binding.novelId, binding.novelTitle, language, nowMillis))
                NovelEnsureResult.READY
            }
        }
    } catch (e: Exception) {
        NovelEnsureResult.FAILED
    }
}
