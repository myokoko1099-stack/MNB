package com.mnb.browser.reader.translation

import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.SourceLanguage
import com.mnb.browser.translation.TargetLanguage
import com.mnb.browser.translation.TranslationPriority
import com.mnb.browser.translation.TranslationProvider
import com.mnb.browser.translation.TranslationStatus
import com.mnb.browser.translation.background.EnqueueResult
import com.mnb.browser.translation.background.TranslationPriorityManager
import com.mnb.browser.translation.background.TranslationQueue
import com.mnb.browser.translation.db.ChapterRepository
import com.mnb.browser.translation.db.TranslationJobRepository
import java.util.concurrent.Executor

/** What one background sync did (Phase 04-I10-C2). Nothing here is ever shown as a translation. */
enum class BackgroundSyncStatus {
    /** Chapters were queued / re-prioritised (see the lists of the report). */
    SYNCED,

    /** No provider exists. Nothing was started and the database was not opened. */
    NOT_CONFIGURED,

    /** The provider is a mock. Nothing was started. */
    MOCK_ONLY,

    UNSUPPORTED_LANGUAGE,

    /** The chapter has no usable identity, novel binding or source language. */
    NO_IDENTITY,

    /** The background worker would use a different provider (or a mock) than the reader. Nothing was started. */
    WORKER_PROVIDER_MISMATCH,

    /** The reader is no longer on this chapter. Nothing (more) was changed for it. */
    SUPERSEDED,

    FAILED,
}

data class BackgroundSyncReport(
    val status: BackgroundSyncStatus,
    /** Chapters that got a new background job. */
    val enqueued: List<ChapterId> = emptyList(),
    /** Chapters that already had live work; no second job was made. */
    val alreadyQueued: List<ChapterId> = emptyList(),
    /** Chapters that already have a completed, up-to-date translation; not queued again. */
    val alreadyTranslated: List<ChapterId> = emptyList(),
    /** Waiting jobs whose priority changed because of the new reader position. */
    val reprioritized: List<ChapterId> = emptyList(),
    /** Later chapters left alone because their last job FAILED or was CANCELLED. */
    val skipped: List<ChapterId> = emptyList(),
    /** Chapters the queue could not handle (storage problem). */
    val failed: List<ChapterId> = emptyList(),
)

/** The existing repositories and queue objects the coordinator works with, built for one provider. */
class BackgroundTranslationPorts(
    val chapters: ChapterRepository,
    val jobs: TranslationJobRepository,
    val queue: TranslationQueue,
    val priorities: TranslationPriorityManager,
    val novels: NovelRegistrar,
    /** Must be the look-ahead [priorities] was built with. */
    val lookAhead: Int = TranslationPriorityManager.DEFAULT_LOOK_AHEAD,
)

/**
 * Connects reader chapter navigation to the background translation pipeline (Phase 04-I10-C2).
 * It owns no translation logic: it only decides which stored chapters to hand to the existing
 * [TranslationQueue] and lets the existing [TranslationPriorityManager] order the waiting jobs.
 *
 * When the reader opens chapter N of a novel (everything runs on [executor], never on the caller's
 * thread):
 *  - nothing happens without a ready, non-mock provider that is also the provider the background
 *    worker will use, so no background work can ever be translated by a mock;
 *  - the open chapter's original text is stored if it is not stored yet (an existing stored text is
 *    never overwritten here);
 *  - every stored chapter from N onwards is queued (the queue itself refuses duplicates and
 *    chapters that already have a completed translation). Chapter N is HIGH, the next
 *    [BackgroundTranslationPorts.lookAhead] chapters NORMAL, later ones LOW; earlier chapters are
 *    not touched. Later chapters whose last job FAILED or was CANCELLED are not queued again;
 *  - jobs that were already waiting are re-prioritised with [TranslationPriorityManager]; a job
 *    that is already PROCESSING keeps its priority;
 *  - if the reader has moved to another chapter meanwhile, the work for the old chapter stops.
 * The coordinator never writes to the reader's translation state: background results only reach
 * the reader through the stored translation, for the chapter that is open at that time.
 */
class ReaderBackgroundTranslationCoordinator(
    private val currentChapterId: () -> ChapterId?,
    private val bindingFor: (String) -> NovelBinding?,
    private val providerSource: () -> TranslationProvider?,
    /** The provider the background worker will actually use (see TranslationWorkEnvironment). */
    private val workerProviderSource: () -> TranslationProvider?,
    private val portsSource: (TranslationProvider) -> BackgroundTranslationPorts?,
    private val executor: Executor,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    /** Returns immediately; the sync runs on [executor]. */
    fun onChapterOpened(identity: ReaderChapterIdentity, sourceUrl: String, originalText: String) {
        try {
            executor.execute {
                try {
                    sync(identity, sourceUrl, originalText)
                } catch (e: Exception) {
                    // Best effort: the reader must never be affected.
                }
            }
        } catch (e: Exception) {
            // Executor shut down.
        }
    }

    /** Blocking (database): call off the main thread. */
    @Synchronized
    fun sync(identity: ReaderChapterIdentity, sourceUrl: String, originalText: String): BackgroundSyncReport {
        return try {
            syncInternal(identity, sourceUrl, originalText)
        } catch (e: Exception) {
            BackgroundSyncReport(BackgroundSyncStatus.FAILED)
        }
    }

    private fun syncInternal(identity: ReaderChapterIdentity, sourceUrl: String, originalText: String): BackgroundSyncReport {
        if (!isCurrent(identity)) return BackgroundSyncReport(BackgroundSyncStatus.SUPERSEDED)
        val source = SourceLanguage.fromCode(identity.sourceLanguageCode)
            ?: return BackgroundSyncReport(BackgroundSyncStatus.NO_IDENTITY)

        val provider = providerSource()
        when (TranslationProviderGuard.check(provider, source)) {
            ProviderReadiness.Ready -> Unit
            ProviderReadiness.NotConfigured -> return BackgroundSyncReport(BackgroundSyncStatus.NOT_CONFIGURED)
            ProviderReadiness.MockOnly -> return BackgroundSyncReport(BackgroundSyncStatus.MOCK_ONLY)
            ProviderReadiness.UnsupportedLanguage -> return BackgroundSyncReport(BackgroundSyncStatus.UNSUPPORTED_LANGUAGE)
        }
        val ready = provider ?: return BackgroundSyncReport(BackgroundSyncStatus.NOT_CONFIGURED)

        val worker = workerProviderSource()
        if (worker == null || worker.isMock || worker.id != ready.id) {
            return BackgroundSyncReport(BackgroundSyncStatus.WORKER_PROVIDER_MISMATCH)
        }

        val binding = bindingFor(sourceUrl)
        if (binding == null ||
            binding.novelId != identity.novelId ||
            SourceLanguage.fromCode(binding.sourceLanguageCode) != source
        ) {
            return BackgroundSyncReport(BackgroundSyncStatus.NO_IDENTITY)
        }

        val ports = portsSource(ready) ?: return BackgroundSyncReport(BackgroundSyncStatus.FAILED)
        when (ports.novels.ensure(binding, clock())) {
            NovelEnsureResult.READY -> Unit
            NovelEnsureResult.SOURCE_LANGUAGE_MISMATCH -> return BackgroundSyncReport(BackgroundSyncStatus.NO_IDENTITY)
            NovelEnsureResult.FAILED -> return BackgroundSyncReport(BackgroundSyncStatus.FAILED)
        }
        if (!isCurrent(identity)) return BackgroundSyncReport(BackgroundSyncStatus.SUPERSEDED)

        // Store the open chapter's original text once. An already stored text is never overwritten here.
        if (originalText.isNotBlank() && ports.chapters.get(identity.chapterId) == null) {
            ports.chapters.saveOriginal(identity.chapterId, identity.novelId, identity.chapterNumber, originalText, clock())
        }

        val current = identity.chapterNumber
        val enqueued = mutableListOf<ChapterId>()
        val alreadyQueued = mutableListOf<ChapterId>()
        val alreadyTranslated = mutableListOf<ChapterId>()
        val skipped = mutableListOf<ChapterId>()
        val failed = mutableListOf<ChapterId>()

        val targets = ports.chapters.listForNovel(identity.novelId).filter { it.chapterNumber >= current }
        for (chapter in targets) {
            if (!isCurrent(identity)) {
                return BackgroundSyncReport(
                    BackgroundSyncStatus.SUPERSEDED, enqueued, alreadyQueued, alreadyTranslated, emptyList(), skipped, failed,
                )
            }
            val id = chapter.chapterId
            if (id != identity.chapterId && gaveUp(ports, id)) {
                skipped += id
                continue
            }
            val priority = priorityFor(chapter.chapterNumber, current, ports.lookAhead)
            when (ports.queue.enqueue(id, source.code, TargetLanguage.MYANMAR.code, priority)) {
                is EnqueueResult.Enqueued -> enqueued += id
                is EnqueueResult.AlreadyQueued -> alreadyQueued += id
                is EnqueueResult.AlreadyTranslated -> alreadyTranslated += id
                EnqueueResult.ChapterNotFound, is EnqueueResult.Failed -> failed += id
            }
        }

        if (!isCurrent(identity)) {
            return BackgroundSyncReport(
                BackgroundSyncStatus.SUPERSEDED, enqueued, alreadyQueued, alreadyTranslated, emptyList(), skipped, failed,
            )
        }
        // Re-calculates HIGH / NORMAL / LOW for jobs that were already waiting (PROCESSING jobs are never touched).
        val summary = ports.priorities.onReaderChapterChanged(identity.novelId, current)
        return BackgroundSyncReport(
            BackgroundSyncStatus.SYNCED, enqueued, alreadyQueued, alreadyTranslated, summary.changed, skipped, failed,
        )
    }

    private fun isCurrent(identity: ReaderChapterIdentity): Boolean = currentChapterId() == identity.chapterId

    /** A later chapter whose last job failed or was cancelled is not retried on every navigation. */
    private fun gaveUp(ports: BackgroundTranslationPorts, chapterId: ChapterId): Boolean {
        val status = ports.jobs.latestForChapter(chapterId)?.status
        return status == TranslationStatus.FAILED || status == TranslationStatus.CANCELLED
    }

    companion object {
        /** The same rule as [TranslationPriorityManager.onReaderChapterChanged], for chapters from the current one onwards. */
        internal fun priorityFor(chapterNumber: Int, currentNumber: Int, lookAhead: Int): TranslationPriority = when {
            chapterNumber <= currentNumber -> TranslationPriority.HIGH
            chapterNumber - currentNumber <= lookAhead -> TranslationPriority.NORMAL
            else -> TranslationPriority.LOW
        }
    }
}
