package com.mnb.browser.translation.usecase

import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.SourceLanguage
import com.mnb.browser.translation.TargetLanguage
import com.mnb.browser.translation.TranslationChunk
import com.mnb.browser.translation.TranslationMetadata
import com.mnb.browser.translation.TranslationProvider
import com.mnb.browser.translation.TranslationRequest
import com.mnb.browser.translation.TranslationResult
import com.mnb.browser.translation.TranslationService
import com.mnb.browser.translation.TranslationStatus
import com.mnb.browser.translation.chunking.ChapterChunk
import com.mnb.browser.translation.chunking.ChapterChunkRepository
import com.mnb.browser.translation.chunking.ChunkReassembler
import com.mnb.browser.translation.chunking.ChunkingOutcome
import com.mnb.browser.translation.chunking.ParagraphSplitter
import com.mnb.browser.translation.db.ChapterRepository
import com.mnb.browser.translation.db.StoredTranslation
import com.mnb.browser.translation.db.TranslationRepository
import com.mnb.browser.translation.memory.ContextRetriever
import com.mnb.browser.translation.memory.MemoryRepository
import com.mnb.browser.translation.memory.TranslationContextProvider
import com.mnb.browser.translation.quality.QualityInput
import com.mnb.browser.translation.quality.QualityResult
import com.mnb.browser.translation.quality.TranslationQualityChecker

/**
 * Instant (foreground, blocking) translation of one whole chapter.
 *
 * original text -> language check -> chunking -> per chunk: context memory + provider ->
 * reassembly in chunk order -> quality check -> storage. The chapter is COMPLETED only when every
 * chunk has a translation; otherwise it ends FAILED or CANCELLED with no translated text, while
 * finished chunks stay stored and are reused by the next attempt.
 *
 * The original text is taken from [TranslateChapterRequest.originalText] (extraction happens
 * before this class). The source language comes from the request code; there is no auto-detection.
 * Call off the main thread. Not wired to any UI.
 *
 * (I10-D) One chapter is translated by one caller at a time across the whole process: the
 * [lock] (default [ChapterTranslationLock.shared]) is shared with the background worker's pipeline,
 * and a second caller gets [InstantTranslationFailure.DUPLICATE_REQUEST] without touching any data.
 */
class InstantTranslationPipeline(
    private val provider: TranslationProvider,
    private val chapters: ChapterRepository,
    private val chunkRepository: ChapterChunkRepository,
    private val translations: TranslationRepository,
    private val memory: MemoryRepository,
    private val contextProvider: TranslationContextProvider = ContextRetriever(memory),
    private val checkQuality: (QualityInput) -> QualityResult = TranslationQualityChecker()::check,
    private val chunker: (ChapterId) -> ChunkingOutcome = chunkRepository::chunkChapter,
    private val lock: ChapterTranslationLock = ChapterTranslationLock.shared,
) {
    private val service = TranslationService(provider)

    /** [isCancelled] is checked before every chunk. Never throws for expected problems. */
    fun translate(
        request: TranslateChapterRequest,
        nowMillis: Long,
        isCancelled: () -> Boolean = { false },
    ): InstantTranslationOutcome {
        require(nowMillis >= 0) { "nowMillis must not be negative" }
        val key = request.chapterId.value
        if (!lock.tryAcquire(request.chapterId)) {
            return fail(InstantTranslationFailure.DUPLICATE_REQUEST, "Chapter $key is already being translated")
        }
        try {
            return run(request, nowMillis, isCancelled)
        } finally {
            lock.release(request.chapterId)
        }
    }

    private fun run(request: TranslateChapterRequest, nowMillis: Long, isCancelled: () -> Boolean): InstantTranslationOutcome {
        val source = SourceLanguage.fromCode(request.sourceLanguageCode)
            ?: return fail(InstantTranslationFailure.UNSUPPORTED_LANGUAGE, "Unsupported source language: ${request.sourceLanguageCode}")
        val targetCode = request.targetLanguageCode.trim().lowercase()
        val target = TargetLanguage.values().firstOrNull { it.code == targetCode }
            ?: return fail(InstantTranslationFailure.UNSUPPORTED_LANGUAGE, "Unsupported target language: ${request.targetLanguageCode}")
        if (source !in provider.supportedSourceLanguages) {
            return fail(InstantTranslationFailure.UNSUPPORTED_LANGUAGE, "Provider ${provider.id} does not support ${source.code}")
        }
        if (request.originalText.isBlank() || ParagraphSplitter.split(request.originalText).isEmpty()) {
            return fail(InstantTranslationFailure.EMPTY_INPUT, "Chapter text is empty")
        }

        // Store the original; reuse a finished, up-to-date translation by the same provider.
        val previous: StoredTranslation? = try {
            chapters.saveOriginal(request.chapterId, request.novelId, request.chapterNumber, request.originalText, nowMillis)
            val existing = translations.getTranslation(request.chapterId)
            if (existing != null &&
                existing.status == TranslationStatus.COMPLETED &&
                existing.metadata.providerId == provider.id &&
                !translations.isStale(request.chapterId)
            ) {
                return reuse(request, existing)
            }
            existing
        } catch (e: Exception) {
            return dbFailure(e)
        }
        val priorVersion = previous?.takeIf { it.status == TranslationStatus.COMPLETED || it.status == TranslationStatus.PENDING }?.metadata?.translationVersion ?: 0
        val interimVersion = maxOf(priorVersion, 1)

        // Chunking and integrity.
        val chunks: List<ChapterChunk> = try {
            chunker(request.chapterId).chunks.sortedBy { it.chunkIndex }
        } catch (e: Exception) {
            return dbFailure(e)
        }
        if (chunks.isEmpty()) return fail(InstantTranslationFailure.EMPTY_INPUT, "Chapter produced no chunks")
        val oversized = chunks.firstOrNull { it.characterCount > TranslationRequest.MAX_TEXT_LENGTH }
        if (oversized != null) {
            return fail(InstantTranslationFailure.INVALID_CHUNK, "Chunk ${oversized.chunkIndex} is too long", chunks.size)
        }
        val integrity = ChunkReassembler.check(request.originalText, chunks)
        if (!integrity.isValid) {
            return fail(
                InstantTranslationFailure.INVALID_CHUNK,
                "Chunks do not match the chapter: missing=${integrity.missingIndices} duplicates=${integrity.duplicateIndices} " +
                    "uncoveredParagraphs=${integrity.uncoveredParagraphs} textMatches=${integrity.textMatches}",
                chunks.size,
            )
        }

        try {
            translations.saveTranslation(request.chapterId, null, TranslationStatus.PROCESSING, interim(nowMillis, interimVersion))
        } catch (e: Exception) {
            return dbFailure(e, chunks.size)
        }

        val done = ArrayList<ChapterChunk>(chunks.size)
        val failed = ArrayList<ChunkFailure>()
        var reused = 0
        for (c in chunks) {
            if (isCancelled()) {
                markStopped(request, TranslationStatus.CANCELLED, nowMillis, interimVersion)
                return InstantTranslationOutcome.Cancelled(chunks.size, done.size)
            }
            val reusable = try {
                translations.findReusableChunk(request.chapterId, c.chunkIndex, c.originalText, provider.id)
            } catch (e: Exception) {
                markStopped(request, TranslationStatus.FAILED, nowMillis, interimVersion)
                return dbFailure(e, chunks.size, done.size)
            }
            if (reusable != null) {
                done += c.copy(chunk = reusable)
                reused++
                continue
            }
            val context = try {
                contextProvider.contextFor(request.novelId, c.originalText, request.chapterNumber)
            } catch (e: Exception) {
                markStopped(request, TranslationStatus.FAILED, nowMillis, interimVersion)
                return dbFailure(e, chunks.size, done.size)
            }
            val result = service.translate(TranslationRequest(c.originalText, source, target, context))
            val translated: String? = when (result) {
                is TranslationResult.Success -> result.translatedText.takeIf { it.isNotBlank() }
                is TranslationResult.Failure -> null
            }
            val problem: String? = when {
                result is TranslationResult.Failure -> result.message ?: result.error.name
                translated == null -> "Provider returned empty text"
                else -> null
            }
            try {
                if (translated != null) {
                    val saved = TranslationChunk(c.chunkId, c.chapterId, c.chunkIndex, c.originalText, translated, TranslationStatus.COMPLETED)
                    translations.saveChunk(saved, provider.id)
                    done += c.copy(chunk = saved)
                } else {
                    val bad = TranslationChunk(c.chunkId, c.chapterId, c.chunkIndex, c.originalText, null, TranslationStatus.FAILED)
                    translations.saveChunk(bad, provider.id)
                    failed += ChunkFailure(c.chunkIndex, problem)
                }
            } catch (e: Exception) {
                markStopped(request, TranslationStatus.FAILED, nowMillis, interimVersion)
                return dbFailure(e, chunks.size, done.size)
            }
        }

        if (failed.isNotEmpty() || done.size != chunks.size) {
            markStopped(request, TranslationStatus.FAILED, nowMillis, interimVersion)
            val reason = if (done.isEmpty()) InstantTranslationFailure.PROVIDER_FAILURE else InstantTranslationFailure.PARTIAL_FAILURE
            return fail(
                reason,
                "${failed.size} of ${chunks.size} chunks failed",
                chunks.size,
                done.size,
                failed,
            )
        }

        val text = assemble(done)
        if (text.isBlank()) {
            markStopped(request, TranslationStatus.FAILED, nowMillis, interimVersion)
            return fail(InstantTranslationFailure.PROVIDER_FAILURE, "Assembled translation is empty", chunks.size, done.size)
        }

        val (quality, qualityError) = runQuality {
            QualityInput.fromChunks(
                chunks = done,
                glossary = memory.listGlossary(request.novelId),
                characters = memory.listCharacters(request.novelId),
            )
        }

        return try {
            translations.saveTranslation(
                request.chapterId,
                text,
                TranslationStatus.COMPLETED,
                TranslationMetadata(
                    providerId = provider.id,
                    createdAtMillis = nowMillis,
                    translationVersion = priorVersion + 1,
                    isMock = provider.isMock,
                ),
            )
            val stored = checkNotNull(translations.getTranslation(request.chapterId)) { "Stored translation could not be read back" }
            InstantTranslationOutcome.Completed(stored, quality, qualityError, chunks.size, reused, false)
        } catch (e: Exception) {
            markStopped(request, TranslationStatus.FAILED, nowMillis, interimVersion)
            dbFailure(e, chunks.size, done.size)
        }
    }

    private fun reuse(request: TranslateChapterRequest, existing: StoredTranslation): InstantTranslationOutcome {
        val (quality, qualityError) = runQuality {
            QualityInput.fromText(
                sourceText = request.originalText,
                translatedText = existing.translatedText,
                glossary = memory.listGlossary(request.novelId),
                characters = memory.listCharacters(request.novelId),
            )
        }
        val count = try {
            translations.getChunks(request.chapterId).size
        } catch (e: Exception) {
            0
        }
        return InstantTranslationOutcome.Completed(existing, quality, qualityError, count, count, true)
    }

    /** Chunks joined in chunk order; a paragraph break between chunks, nothing inside one split paragraph. */
    private fun assemble(chunks: List<ChapterChunk>): String {
        val sb = StringBuilder()
        var prev: ChapterChunk? = null
        for (c in ChunkReassembler.ordered(chunks)) {
            if (prev != null && prev.endParagraphIndex != c.startParagraphIndex) sb.append(ParagraphSplitter.SEPARATOR)
            sb.append(c.chunk.translatedText.orEmpty().trim())
            prev = c
        }
        return sb.toString()
    }

    private fun runQuality(buildInput: () -> QualityInput): Pair<QualityResult?, String?> = try {
        checkQuality(buildInput()) to null
    } catch (e: Exception) {
        null to (e.message ?: e.javaClass.simpleName)
    }

    private fun interim(nowMillis: Long, version: Int) =
        TranslationMetadata(provider.id, nowMillis, translationVersion = version, isMock = provider.isMock)

    /** Best effort: records FAILED or CANCELLED without text. A database error here is ignored. */
    private fun markStopped(request: TranslateChapterRequest, status: TranslationStatus, nowMillis: Long, version: Int) {
        try {
            translations.saveTranslation(request.chapterId, null, status, interim(nowMillis, version))
        } catch (e: Exception) {
            // The original failure is what gets reported.
        }
    }

    private fun fail(
        reason: InstantTranslationFailure,
        message: String? = null,
        total: Int = 0,
        completed: Int = 0,
        failed: List<ChunkFailure> = emptyList(),
    ) = InstantTranslationOutcome.Failed(reason, message, total, completed, failed)

    private fun dbFailure(e: Exception, total: Int = 0, completed: Int = 0) =
        fail(InstantTranslationFailure.DATABASE_FAILURE, e.message ?: e.javaClass.simpleName, total, completed)
}
