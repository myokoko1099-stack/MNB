package com.mnb.browser.translation.gemini

import android.os.Looper
import com.mnb.browser.translation.SourceLanguage
import com.mnb.browser.translation.TranslationError
import com.mnb.browser.translation.TranslationProvider
import com.mnb.browser.translation.TranslationRequest
import com.mnb.browser.translation.TranslationResult

/**
 * Phase 06-B2: the adapter between the direct Gemini provider ([GeminiProvider], Phase 06-B1) and
 * the translation engine ([TranslationProvider]). It owns no network, key or prompt logic: it
 * only checks the calling thread, calls [gemini] and maps its [GeminiResult] to a
 * [TranslationResult]. Everything that used a [TranslationProvider] before (TranslationService,
 * the instant pipeline, the background worker) can use it without any change.
 *
 * Rules of this class:
 * - It never touches the API key. The key is read from the Phase 06-A secure storage inside
 *   [gemini] only.
 * - [translate] blocks (Keystore, file and network work). The engine already calls providers
 *   from its own background threads; as a hard guard this class refuses to run on the Android main
 *   thread and then neither reads the key nor sends anything.
 * - It never throws, and every failure message is a fixed text: nothing from an exception, an HTTP
 *   body or the request text is copied into a result.
 * - It is never a mock ([isMock] is false), so its output may be shown as a real translation.
 */
class GeminiTranslationProvider(
    private val gemini: GeminiProvider,
    private val isMainThread: () -> Boolean = { currentThreadIsAndroidMain() },
) : TranslationProvider {

    override val id: String = gemini.id
    override val displayName: String = DISPLAY_NAME
    override val isMock: Boolean = false
    override val supportedSourceLanguages: Set<SourceLanguage> = SourceLanguage.values().toSet()

    override fun translate(request: TranslationRequest): TranslationResult {
        // The engine validates first, but a provider must not depend on its caller for that.
        request.validate()?.let { return TranslationResult.Failure(it, id, GeminiError.INVALID_REQUEST.message) }

        if (onMainThread()) {
            return TranslationResult.Failure(TranslationError.PROVIDER_FAILURE, id, MAIN_THREAD_MESSAGE)
        }

        val result = try {
            gemini.translate(request)
        } catch (e: Exception) {
            // No message or cause is kept: it could mention the key or the text.
            return TranslationResult.Failure(TranslationError.PROVIDER_FAILURE, id, GeminiError.INTERNAL.message)
        }
        return map(result, request)
    }

    /** A failing guard counts as "on the main thread": nothing is sent when that is not certain. */
    private fun onMainThread(): Boolean = try {
        isMainThread()
    } catch (t: Throwable) {
        true
    }

    private fun map(result: GeminiResult, request: TranslationRequest): TranslationResult = when (result) {
        is GeminiResult.Success ->
            if (result.translatedText.isBlank()) {
                TranslationResult.Failure(TranslationError.PROVIDER_FAILURE, id, GeminiError.EMPTY_RESPONSE.message)
            } else {
                TranslationResult.Success(
                    translatedText = result.translatedText,
                    providerId = id,
                    isMock = false,
                    sourceLanguage = request.sourceLanguage,
                    targetLanguage = request.targetLanguage,
                )
            }
        // GeminiError carries a fixed message only; every Gemini failure is a provider failure for
        // the engine. The finer code stays in the message for the UI of a later phase.
        is GeminiResult.Failure ->
            TranslationResult.Failure(TranslationError.PROVIDER_FAILURE, id, result.error.message)
    }

    companion object {
        const val DISPLAY_NAME = "Google Gemini (your own API key)"
        const val MAIN_THREAD_MESSAGE = "Translation must not run on the main thread."

        /**
         * True on the Android main thread. Outside Android (plain JVM) there is no main looper and
         * this is false.
         */
        fun currentThreadIsAndroidMain(): Boolean = try {
            Looper.getMainLooper()?.thread === Thread.currentThread()
        } catch (t: Throwable) {
            false
        }
    }
}
