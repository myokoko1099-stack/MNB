package com.mnb.browser.reader.translation

import com.mnb.browser.extraction.ExtractedContent
import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.SourceLanguage
import com.mnb.browser.translation.TargetLanguage
import com.mnb.browser.translation.TranslationProvider
import com.mnb.browser.translation.TranslationStatus
import com.mnb.browser.translation.usecase.InstantTranslationFailure
import com.mnb.browser.translation.usecase.InstantTranslationOutcome
import com.mnb.browser.translation.usecase.TranslateChapterRequest
import java.util.concurrent.Executor

/** Runs one instant chapter translation (production: InstantTranslationPipeline.translate). Blocking. */
fun interface InstantTranslationRunner {
    fun run(request: TranslateChapterRequest, nowMillis: Long, isCancelled: () -> Boolean): InstantTranslationOutcome
}

enum class NovelEnsureResult { READY, SOURCE_LANGUAGE_MISMATCH, FAILED }

/** Makes sure the bound novel exists in the translation store before a chapter is saved. Blocking. */
fun interface NovelRegistrar {
    fun ensure(binding: NovelBinding, nowMillis: Long): NovelEnsureResult
}

class TranslationBackend(val runner: InstantTranslationRunner, val novels: NovelRegistrar)

enum class StartResult {
    /** Work was handed to the executor; the reader shows Processing. */
    STARTED,
    NO_CHAPTER_OPEN,
    /** The open chapter has no identity, or its binding/language is missing or inconsistent. */
    NO_IDENTITY,
    /** The content is not the chapter the reader has open. */
    CONTENT_MISMATCH,
    /** Already running, already translated, or a failure that retrying cannot fix. */
    NOT_ALLOWED,
    /** No usable provider or backend (not configured, mock only, unsupported language). */
    BLOCKED,
}

/**
 * Connects the reader to the instant translation pipeline (Phase 04-I10-C1). It owns no
 * translation logic: it checks identity and provider, sets the reader state, runs the pipeline on
 * [executor] (never on the caller's thread) and applies the outcome to [holder].
 *
 * Safety rules:
 *  - nothing starts without a chapter identity, a matching URL binding and a supported source language;
 *  - a missing or mock provider shows Unavailable and the pipeline is never called;
 *  - Completed is shown only for a stored, non-blank, non-mock translation;
 *  - a result for a chapter that is no longer open is dropped by [ReaderTranslationStateHolder.update],
 *    and the pipeline is told to cancel as soon as its chapter is not the open one.
 */
class ReaderInstantTranslationController(
    private val holder: ReaderTranslationStateHolder,
    private val bindings: NovelBindingStore,
    private val gateway: ReaderTranslationGateway,
    private val providerSource: () -> TranslationProvider?,
    private val backendSource: (TranslationProvider) -> TranslationBackend?,
    private val executor: Executor,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ReaderTranslationActions {

    override fun requestTranslation(content: ExtractedContent) {
        start(content)
    }

    fun start(content: ExtractedContent): StartResult {
        val open = holder.current ?: return StartResult.NO_CHAPTER_OPEN
        val identity = open.identity ?: return StartResult.NO_IDENTITY
        if (identity.sourceUrl != content.url) return StartResult.CONTENT_MISMATCH
        if (!open.state.canRequestTranslation) return StartResult.NOT_ALLOWED
        val chapterId = identity.chapterId

        val binding = bindings.find(content.url)
        val source = SourceLanguage.fromCode(identity.sourceLanguageCode)
        if (binding == null || source == null ||
            binding.novelId != identity.novelId ||
            SourceLanguage.fromCode(binding.sourceLanguageCode) != source
        ) {
            holder.update(chapterId, ReaderTranslationState.Unavailable(ReaderTranslationUnavailableReason.NO_CHAPTER_IDENTITY))
            return StartResult.NO_IDENTITY
        }

        val provider = providerSource()
        when (TranslationProviderGuard.check(provider, source)) {
            ProviderReadiness.Ready -> Unit
            ProviderReadiness.NotConfigured -> return blocked(chapterId, unavailable(ReaderTranslationUnavailableReason.NOT_CONFIGURED))
            ProviderReadiness.MockOnly -> return blocked(chapterId, unavailable(ReaderTranslationUnavailableReason.MOCK_ONLY))
            ProviderReadiness.UnsupportedLanguage -> return blocked(
                chapterId,
                ReaderTranslationState.Failed(ReaderTranslationFailure.UNSUPPORTED_LANGUAGE, canRetry = false),
            )
        }
        val readyProvider = provider ?: return blocked(chapterId, unavailable(ReaderTranslationUnavailableReason.NOT_CONFIGURED))
        val backend: TranslationBackend? = try {
            backendSource(readyProvider)
        } catch (e: Exception) {
            null
        }
        if (backend == null) return blocked(chapterId, unavailable(ReaderTranslationUnavailableReason.NOT_CONFIGURED))

        val request = TranslateChapterRequest(
            chapterId = chapterId,
            novelId = identity.novelId,
            chapterNumber = identity.chapterNumber,
            originalText = content.mainText,
            sourceLanguageCode = source.code,
            targetLanguageCode = TargetLanguage.MYANMAR.code,
        )

        if (!holder.update(chapterId, ReaderTranslationState.Processing())) return StartResult.NO_CHAPTER_OPEN
        try {
            executor.execute { work(chapterId, binding, request, backend) }
        } catch (e: Exception) {
            holder.update(chapterId, ReaderTranslationState.Failed(ReaderTranslationFailure.UNKNOWN, canRetry = true))
        }
        return StartResult.STARTED
    }

    /**
     * Picks up a translation that is already stored for the open chapter (for example one finished
     * while the chapter was closed). Reads the gateway off the calling thread and applies the answer
     * only while the chapter still shows no work in progress and no translation.
     */
    override fun onChapterShown() {
        val identity = holder.current?.identity ?: return
        try {
            executor.execute {
                val stored = try {
                    gateway.stateOf(identity)
                } catch (e: Exception) {
                    return@execute
                }
                if (stored == ReaderTranslationState.OriginalOnly) return@execute
                val now = holder.current
                if (now != null && now.identity?.chapterId == identity.chapterId && now.state == ReaderTranslationState.OriginalOnly) {
                    holder.update(identity.chapterId, stored)
                }
            }
        } catch (e: Exception) {
            // Executor shut down: the original text stays available.
        }
    }

    private fun work(chapterId: ChapterId, binding: NovelBinding, request: TranslateChapterRequest, backend: TranslationBackend) {
        val state = try {
            when (backend.novels.ensure(binding, clock())) {
                NovelEnsureResult.SOURCE_LANGUAGE_MISMATCH ->
                    unavailable(ReaderTranslationUnavailableReason.NO_CHAPTER_IDENTITY)
                NovelEnsureResult.FAILED ->
                    ReaderTranslationState.Failed(ReaderTranslationFailure.STORAGE, canRetry = true)
                NovelEnsureResult.READY ->
                    mapOutcome(backend.runner.run(request, clock()) { !isOpen(chapterId) })
            }
        } catch (e: Exception) {
            ReaderTranslationState.Failed(ReaderTranslationFailure.UNKNOWN, canRetry = true)
        }
        // Ignored (returns false) when the reader has moved to another chapter meanwhile.
        holder.update(chapterId, state)
    }

    private fun isOpen(chapterId: ChapterId): Boolean = holder.current?.identity?.chapterId == chapterId

    private fun blocked(chapterId: ChapterId, state: ReaderTranslationState): StartResult {
        holder.update(chapterId, state)
        return StartResult.BLOCKED
    }

    private fun unavailable(reason: ReaderTranslationUnavailableReason) = ReaderTranslationState.Unavailable(reason)

    companion object {
        /** Pure: turns a pipeline outcome into what the reader may show. */
        fun mapOutcome(outcome: InstantTranslationOutcome): ReaderTranslationState = when (outcome) {
            is InstantTranslationOutcome.Completed -> {
                val stored = outcome.translation
                val text = stored.translatedText.orEmpty()
                when {
                    stored.status != TranslationStatus.COMPLETED || text.isBlank() ->
                        ReaderTranslationState.Failed(ReaderTranslationFailure.STORAGE, canRetry = true)
                    stored.metadata.isMock ->
                        ReaderTranslationState.Unavailable(ReaderTranslationUnavailableReason.MOCK_ONLY)
                    else -> ReaderTranslationState.Completed(text, stored.metadata.providerId)
                }
            }
            is InstantTranslationOutcome.Cancelled -> ReaderTranslationState.OriginalOnly
            is InstantTranslationOutcome.Failed -> when (outcome.reason) {
                InstantTranslationFailure.EMPTY_INPUT, InstantTranslationFailure.INVALID_CHUNK ->
                    ReaderTranslationState.Failed(ReaderTranslationFailure.INVALID_CONTENT, canRetry = false)
                InstantTranslationFailure.UNSUPPORTED_LANGUAGE ->
                    ReaderTranslationState.Failed(ReaderTranslationFailure.UNSUPPORTED_LANGUAGE, canRetry = false)
                InstantTranslationFailure.PROVIDER_FAILURE, InstantTranslationFailure.PARTIAL_FAILURE ->
                    ReaderTranslationState.Failed(ReaderTranslationFailure.PROVIDER, canRetry = true)
                InstantTranslationFailure.DATABASE_FAILURE ->
                    ReaderTranslationState.Failed(ReaderTranslationFailure.STORAGE, canRetry = true)
                // Another run for this chapter is already in progress: keep showing Processing.
                InstantTranslationFailure.DUPLICATE_REQUEST -> ReaderTranslationState.Processing()
            }
        }
    }
}
