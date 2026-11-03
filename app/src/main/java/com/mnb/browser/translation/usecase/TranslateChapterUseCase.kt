package com.mnb.browser.translation.usecase

import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.SourceLanguage
import com.mnb.browser.translation.TargetLanguage
import com.mnb.browser.translation.TranslationError
import com.mnb.browser.translation.TranslationMetadata
import com.mnb.browser.translation.TranslationProvider
import com.mnb.browser.translation.TranslationRequest
import com.mnb.browser.translation.TranslationResult
import com.mnb.browser.translation.TranslationService
import com.mnb.browser.translation.TranslationStatus
import com.mnb.browser.translation.db.ChapterRepository
import com.mnb.browser.translation.db.StoredTranslation
import com.mnb.browser.translation.db.TranslationRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * Translates one chapter with the given provider and stores the result locally.
 *
 * Flow: validate languages and text -> save the original text (hash compared, so changed text
 * invalidates the old translation) -> reuse an up-to-date translation by the same provider, or
 * call the provider through [TranslationService] -> store the result with its metadata.
 *
 * Never throws for expected problems; they come back as [TranslateChapterOutcome.Failed].
 * Blocking, like the repositories: call it off the main thread. Not wired to any UI.
 */
class TranslateChapterUseCase(
    private val provider: TranslationProvider,
    private val chapters: ChapterRepository,
    private val translations: TranslationRepository,
) {
    private val service = TranslationService(provider)
    private val inFlight: MutableSet<String> = ConcurrentHashMap.newKeySet<String>()

    fun translate(request: TranslateChapterRequest, nowMillis: Long): TranslateChapterOutcome {
        require(nowMillis >= 0) { "nowMillis must not be negative" }
        val key = request.chapterId.value
        if (!inFlight.add(key)) {
            return fail(TranslateChapterFailure.DUPLICATE_REQUEST, "Chapter $key is already being translated")
        }
        try {
            return process(request, nowMillis)
        } finally {
            inFlight.remove(key)
        }
    }

    /** The stored translation of a chapter, or null when it has none. May throw on a database error. */
    fun findTranslation(chapterId: ChapterId): StoredTranslation? = translations.getTranslation(chapterId)

    private fun process(request: TranslateChapterRequest, nowMillis: Long): TranslateChapterOutcome {
        val source = SourceLanguage.fromCode(request.sourceLanguageCode)
            ?: return fail(
                TranslateChapterFailure.UNSUPPORTED_LANGUAGE,
                "Unsupported source language: ${request.sourceLanguageCode}",
            )
        val targetCode = request.targetLanguageCode.trim().lowercase()
        val target = TargetLanguage.values().firstOrNull { it.code == targetCode }
            ?: return fail(
                TranslateChapterFailure.UNSUPPORTED_LANGUAGE,
                "Unsupported target language: ${request.targetLanguageCode}",
            )
        if (source !in provider.supportedSourceLanguages) {
            return fail(
                TranslateChapterFailure.UNSUPPORTED_LANGUAGE,
                "Provider ${provider.id} does not support ${source.code}",
            )
        }
        val textRequest = TranslationRequest(request.originalText, source, target)
        textRequest.validate()?.let { return fail(it.toFailure()) }

        // Step 1: store the original and look for a translation that can be reused.
        val previous: StoredTranslation? = try {
            chapters.saveOriginal(
                request.chapterId,
                request.novelId,
                request.chapterNumber,
                request.originalText,
                nowMillis,
            )
            val existing = translations.getTranslation(request.chapterId)
            if (existing != null &&
                existing.status == TranslationStatus.COMPLETED &&
                existing.metadata.providerId == provider.id &&
                !translations.isStale(request.chapterId)
            ) {
                return TranslateChapterOutcome.AlreadyTranslated(existing)
            }
            existing
        } catch (e: Exception) {
            return dbFailure(e)
        }

        // Step 2: translate. The service turns provider exceptions into Failure results.
        return when (val result = service.translate(textRequest)) {
            is TranslationResult.Failure -> fail(result.error.toFailure(), result.message)
            is TranslationResult.Success -> store(request, result, previous, nowMillis)
        }
    }

    private fun store(
        request: TranslateChapterRequest,
        result: TranslationResult.Success,
        previous: StoredTranslation?,
        nowMillis: Long,
    ): TranslateChapterOutcome {
        if (result.translatedText.isBlank()) {
            return fail(TranslateChapterFailure.PROVIDER_FAILURE, "Provider returned empty text")
        }
        val metadata = TranslationMetadata.fromResult(result, nowMillis).copy(
            translationVersion = (previous?.metadata?.translationVersion ?: 0) + 1,
        )
        return try {
            translations.saveTranslation(
                request.chapterId,
                result.translatedText,
                TranslationStatus.COMPLETED,
                metadata,
            )
            val stored = checkNotNull(translations.getTranslation(request.chapterId)) {
                "Stored translation could not be read back"
            }
            TranslateChapterOutcome.Translated(stored)
        } catch (e: Exception) {
            dbFailure(e)
        }
    }

    private fun TranslationError.toFailure(): TranslateChapterFailure = when (this) {
        TranslationError.EMPTY_TEXT -> TranslateChapterFailure.EMPTY_INPUT
        TranslationError.TEXT_TOO_LONG -> TranslateChapterFailure.TEXT_TOO_LONG
        TranslationError.UNSUPPORTED_SOURCE_LANGUAGE -> TranslateChapterFailure.UNSUPPORTED_LANGUAGE
        TranslationError.PROVIDER_FAILURE -> TranslateChapterFailure.PROVIDER_FAILURE
    }

    private fun fail(reason: TranslateChapterFailure, message: String? = null) =
        TranslateChapterOutcome.Failed(reason, message)

    private fun dbFailure(e: Exception) =
        fail(TranslateChapterFailure.DATABASE_FAILURE, e.message ?: e.javaClass.simpleName)
}
