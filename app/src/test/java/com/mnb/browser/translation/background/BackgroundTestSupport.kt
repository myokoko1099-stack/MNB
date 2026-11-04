package com.mnb.browser.translation.background

import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.SourceLanguage
import com.mnb.browser.translation.TranslationProvider
import com.mnb.browser.translation.TranslationRequest
import com.mnb.browser.translation.TranslationResult
import com.mnb.browser.translation.chunking.ChapterChunkRepository
import com.mnb.browser.translation.chunking.ChunkingConfig
import com.mnb.browser.translation.chunking.ChunkingEngine
import com.mnb.browser.translation.chunking.ParagraphSplitter
import com.mnb.browser.translation.db.ChapterRepository
import com.mnb.browser.translation.db.MnbDatabase
import com.mnb.browser.translation.db.TranslationJobRepository
import com.mnb.browser.translation.db.TranslationRepository
import com.mnb.browser.translation.memory.MemoryRepository
import com.mnb.browser.translation.usecase.InstantTranslationPipeline

internal val PARAGRAPHS = listOf(
    "Arden reached Zephyr at dawn today.",
    "Nothing else happens in this paragraph.",
    "The last paragraph closes the chapter.",
)
internal val CHAPTER_TEXT: String = PARAGRAPHS.joinToString(ParagraphSplitter.SEPARATOR)

/** Scripted provider; [behaviour] may fail or throw per request. */
internal class FakeProvider(
    override val id: String = "fake",
    private val behaviour: (TranslationRequest) -> TranslationResult = { ok(it) },
) : TranslationProvider {
    override val displayName = "Fake"
    override val isMock = false
    override val supportedSourceLanguages: Set<SourceLanguage> = SourceLanguage.values().toSet()
    val requests = mutableListOf<TranslationRequest>()

    override fun translate(request: TranslationRequest): TranslationResult {
        requests += request
        return behaviour(request)
    }

    companion object {
        fun ok(req: TranslationRequest, providerId: String = "fake") = TranslationResult.Success(
            translatedText = "T:" + req.text,
            providerId = providerId,
            isMock = false,
            sourceLanguage = req.sourceLanguage,
            targetLanguage = req.targetLanguage,
        )
    }
}

/** Three chunks per chapter (max 60 characters each). */
internal fun buildRunner(
    db: MnbDatabase,
    provider: TranslationProvider,
    maxAttempts: Int = 3,
    clock: () -> Long = { 100L },
): TranslationJobRunner {
    val chapters = ChapterRepository(db)
    val pipeline = InstantTranslationPipeline(
        provider = provider,
        chapters = chapters,
        chunkRepository = ChapterChunkRepository(db, ChunkingEngine(ChunkingConfig(maxChunkChars = 60))),
        translations = TranslationRepository(db),
        memory = MemoryRepository(db),
    )
    return TranslationJobRunner(chapters, TranslationJobRepository(db), pipeline, clock, maxAttempts)
}

internal class FakeScheduler : TranslationWorkScheduler {
    val scheduled = mutableListOf<ChapterId>()
    private val live = mutableSetOf<String>()

    override fun schedule(chapterId: ChapterId, sourceLanguageCode: String?, targetLanguageCode: String) {
        scheduled += chapterId
        live += chapterId.value
    }

    override fun isScheduled(chapterId: ChapterId) = chapterId.value in live

    override fun cancel(chapterId: ChapterId) {
        live -= chapterId.value
    }

    /** Simulates work lost without the job row being updated. */
    fun dropWork(chapterId: ChapterId) {
        live -= chapterId.value
    }
}
