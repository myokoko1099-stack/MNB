package com.mnb.browser.translation.background

import com.mnb.browser.translation.SourceLanguage
import com.mnb.browser.translation.TranslationProvider

/** Why the background worker refuses to translate (Phase 04-I10-D). All of these are permanent. */
enum class WorkerRefusal {
    /** No worker provider, or the reader has no provider bound. */
    NO_PROVIDER,

    /** The worker's provider is a placeholder; its output is never a real translation. */
    MOCK_PROVIDER,

    /** The worker's provider id differs from the reader's provider id. */
    PROVIDER_MISMATCH,

    /** The source language is missing/unknown or the worker's provider does not support it. */
    UNSUPPORTED_LANGUAGE,
}

/**
 * Hard guard run by [TranslationWorker] before it builds a provider-backed runner. Pure: no
 * database, no WorkManager. A refused job must not call any provider and must not store a
 * COMPLETED translation.
 */
object TranslationWorkerGuard {
    /** Returns null when the work may start, otherwise the reason it must not. */
    fun check(
        workerProvider: TranslationProvider?,
        readerProviderId: String?,
        sourceLanguageCode: String?,
    ): WorkerRefusal? {
        if (workerProvider == null || readerProviderId == null) return WorkerRefusal.NO_PROVIDER
        if (workerProvider.isMock) return WorkerRefusal.MOCK_PROVIDER
        if (workerProvider.id != readerProviderId) return WorkerRefusal.PROVIDER_MISMATCH
        val source = SourceLanguage.fromCode(sourceLanguageCode) ?: return WorkerRefusal.UNSUPPORTED_LANGUAGE
        if (source !in workerProvider.supportedSourceLanguages) return WorkerRefusal.UNSUPPORTED_LANGUAGE
        return null
    }
}
