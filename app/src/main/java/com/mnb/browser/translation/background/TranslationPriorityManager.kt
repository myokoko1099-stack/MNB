package com.mnb.browser.translation.background

import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.NovelId
import com.mnb.browser.translation.TranslationPriority
import com.mnb.browser.translation.db.PriorityChange
import com.mnb.browser.translation.db.TranslationJobRepository

/** What [TranslationPriorityManager.onReaderChapterChanged] did to the waiting jobs of a novel. */
data class ReprioritizeSummary(
    /** Chapters whose PENDING job got a different priority. */
    val changed: List<ChapterId>,
    /** PENDING jobs that already had the wanted priority, or that are not covered by the rules. */
    val unchanged: List<ChapterId>,
) {
    val changedCount: Int get() = changed.size
}

/**
 * Re-orders the waiting background jobs when the reader moves to another chapter (Phase 04-I9-B2).
 * Blocking: call off the main thread. It only changes the priority of PENDING jobs; a PROCESSING
 * job is never touched, so work in progress is not interrupted. FIFO among jobs of the same
 * priority is kept because a job's queue position (its jobId) never changes.
 *
 * Reader rules for [onReaderChapterChanged] (chapter numbers of the same novel):
 *  - the chapter being read: HIGH
 *  - the next [lookAhead] chapters after it: NORMAL
 *  - later chapters beyond that window: LOW (no longer needed soon)
 *  - chapters before the current one: left as they are
 */
class TranslationPriorityManager(
    private val jobs: TranslationJobRepository,
    private val lookAhead: Int = DEFAULT_LOOK_AHEAD,
) {
    init {
        require(lookAhead >= 0) { "lookAhead must not be negative" }
    }

    /** Gives [chapterId]'s waiting job HIGH priority (the reader arrived at this chapter). */
    fun promoteToHigh(chapterId: ChapterId): PriorityChange = setPriority(chapterId, TranslationPriority.HIGH)

    /** Gives [chapterId]'s waiting job NORMAL priority. */
    fun setNormal(chapterId: ChapterId): PriorityChange = setPriority(chapterId, TranslationPriority.NORMAL)

    /** Gives [chapterId]'s waiting job LOW priority (no longer needed soon). */
    fun demoteToLow(chapterId: ChapterId): PriorityChange = setPriority(chapterId, TranslationPriority.LOW)

    /** Changes the priority of [chapterId]'s job while it is PENDING; see [PriorityChange]. */
    @Synchronized
    fun setPriority(chapterId: ChapterId, priority: TranslationPriority): PriorityChange =
        jobs.changePendingPriority(chapterId, priority)

    /**
     * The reader is now at chapter [currentChapterNumber] of [novelId]: applies the reader rules
     * above to every PENDING job of that novel in one pass. Chapters without a waiting job (not
     * queued, already running or finished) are simply skipped.
     */
    @Synchronized
    fun onReaderChapterChanged(novelId: NovelId, currentChapterNumber: Int): ReprioritizeSummary {
        val changed = mutableListOf<ChapterId>()
        val unchanged = mutableListOf<ChapterId>()
        for (pending in jobs.findPendingForNovel(novelId)) {
            val wanted = wantedPriority(pending.chapterNumber, currentChapterNumber)
            val chapterId = pending.job.chapterId
            if (wanted == null) {
                unchanged += chapterId
                continue
            }
            when (jobs.changePendingPriority(chapterId, wanted)) {
                PriorityChange.UPDATED -> changed += chapterId
                else -> unchanged += chapterId
            }
        }
        return ReprioritizeSummary(changed, unchanged)
    }

    /** Position in the waiting line, 1 = starts first. Null when the chapter has no PENDING job. */
    fun queuePosition(chapterId: ChapterId): Int? = aheadCount(chapterId)?.plus(1)

    /**
     * How many PENDING jobs start before this chapter's job (0 = next in line). Null when the
     * chapter has no PENDING job (not queued, PROCESSING or finished).
     */
    fun aheadCount(chapterId: ChapterId): Int? = jobs.aheadCount(chapterId)

    private fun wantedPriority(chapterNumber: Int, current: Int): TranslationPriority? = when {
        chapterNumber < current -> null
        chapterNumber == current -> TranslationPriority.HIGH
        chapterNumber - current <= lookAhead -> TranslationPriority.NORMAL
        else -> TranslationPriority.LOW
    }

    companion object {
        const val DEFAULT_LOOK_AHEAD = 3
    }
}
