package com.mnb.browser.translation

/** Why a translation did not produce text. Callers map these to UI messages later. */
enum class TranslationError {
    EMPTY_TEXT,
    TEXT_TOO_LONG,
    UNSUPPORTED_SOURCE_LANGUAGE,
    PROVIDER_FAILURE,
}
