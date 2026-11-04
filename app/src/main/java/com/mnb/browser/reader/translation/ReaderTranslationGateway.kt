package com.mnb.browser.reader.translation

/**
 * The only door between the reader and the translation core (Phase 04-I10-A). The reader and its
 * state holder depend on this interface alone, so a real AI provider, the instant pipeline or the
 * background queue can be connected behind it later without touching reader code.
 */
fun interface ReaderTranslationGateway {
    /**
     * Current translation state of [identity]'s chapter. May read a database, so callers run it off
     * the main thread. Must not start any translation work by itself.
     */
    fun stateOf(identity: ReaderChapterIdentity): ReaderTranslationState
}

/** Default until a backend is connected: every chapter reports "not configured". */
object UnavailableReaderTranslationGateway : ReaderTranslationGateway {
    override fun stateOf(identity: ReaderChapterIdentity): ReaderTranslationState =
        ReaderTranslationState.Unavailable(ReaderTranslationUnavailableReason.NOT_CONFIGURED)
}
