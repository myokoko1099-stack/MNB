package com.mnb.browser.reader.translation

import com.mnb.browser.translation.TranslationProvider
import com.mnb.browser.translation.background.TranslationPriorityManager
import com.mnb.browser.translation.background.TranslationQueue
import com.mnb.browser.translation.background.TranslationWorkScheduler
import com.mnb.browser.translation.db.ChapterRepository
import com.mnb.browser.translation.db.MnbDatabase
import com.mnb.browser.translation.db.NovelRepository
import com.mnb.browser.translation.db.TranslationJobRepository
import com.mnb.browser.translation.db.TranslationRepository

/**
 * Builds [BackgroundTranslationPorts] on top of the existing repositories, [TranslationQueue] and
 * [TranslationPriorityManager] (Phase 04-I10-C2); no queue logic lives here. [database] and
 * [scheduler] (WorkManagerTranslationScheduler in the app) are called only when a usable provider
 * exists, so neither the database nor WorkManager is touched while translation is not configured.
 * The ports are cached per provider id because the queue compares stored translations with it.
 */
class BackgroundTranslationWiring(
    private val database: () -> MnbDatabase,
    private val scheduler: () -> TranslationWorkScheduler,
    private val lookAhead: Int = TranslationPriorityManager.DEFAULT_LOOK_AHEAD,
) {
    private var cachedProviderId: String? = null
    private var cachedPorts: BackgroundTranslationPorts? = null

    @Synchronized
    fun portsFor(provider: TranslationProvider): BackgroundTranslationPorts? {
        if (cachedProviderId == provider.id) return cachedPorts
        return try {
            val db = database()
            val chapters = ChapterRepository(db)
            val jobs = TranslationJobRepository(db)
            val ports = BackgroundTranslationPorts(
                chapters = chapters,
                jobs = jobs,
                queue = TranslationQueue(chapters, TranslationRepository(db), jobs, scheduler(), provider.id),
                priorities = TranslationPriorityManager(jobs, lookAhead),
                novels = RoomNovelRegistrar(NovelRepository(db)),
                lookAhead = lookAhead,
            )
            cachedProviderId = provider.id
            cachedPorts = ports
            ports
        } catch (e: Exception) {
            null
        }
    }
}
