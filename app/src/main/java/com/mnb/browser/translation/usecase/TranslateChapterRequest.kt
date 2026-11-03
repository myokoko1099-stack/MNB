package com.mnb.browser.translation.usecase

import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.NovelId
import com.mnb.browser.translation.TargetLanguage
import com.mnb.browser.translation.db.StoredTranslation

/**
 * Input of [TranslateChapterUseCase]. Languages are raw ISO 639-1 codes so that an unsupported
 * or missing code is reported as [TranslateChapterFailure.UNSUPPORTED_LANGUAGE] instead of being
 * rejected earlier by a typed constructor.
 */
data class TranslateChapterRequest(
    val chapterId: ChapterId,
    val novelId: NovelId,
    val chapterNumber: Int,
    val originalText: String,
    val sourceLanguageCode: String?,
    val targetLanguageCode: String = TargetLanguage.MYANMAR.code,
) {
    init {
        require(chapterNumber >= 0) { "chapterNumber must not be negative" }
    }
}

/** Why a chapter translation request did not produce a stored translation. */
enum class TranslateChapterFailure {
    /** The original text is empty or blank. */
    EMPTY_INPUT,
    TEXT_TOO_LONG,
    /** Source or target code unknown, or the source language is not supported by the provider. */
    UNSUPPORTED_LANGUAGE,
    PROVIDER_FAILURE,
    /** Reading or writing the local database failed (including a missing novel). */
    DATABASE_FAILURE,
    /** A request for the same chapter is already running. */
    DUPLICATE_REQUEST,
}

sealed interface TranslateChapterOutcome {
    /** The provider was called and the result is now stored. Check `translation.metadata.isMock`. */
    data class Translated(val translation: StoredTranslation) : TranslateChapterOutcome

    /** Duplicate request: an up-to-date translation by the same provider already existed and was reused. */
    data class AlreadyTranslated(val translation: StoredTranslation) : TranslateChapterOutcome

    data class Failed(
        val reason: TranslateChapterFailure,
        val message: String? = null,
    ) : TranslateChapterOutcome
}
