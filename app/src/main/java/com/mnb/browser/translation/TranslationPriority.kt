package com.mnb.browser.translation

/**
 * How soon a queued chapter translation should start (Phase 04-I9-B1). A job with a higher [rank]
 * starts before a job with a lower one; jobs with the same priority start in the order they were
 * queued (FIFO). A job starts with the priority it was created with; while it is still PENDING it can
 * be changed (Phase 04-I9-B2, see TranslationPriorityManager).
 */
enum class TranslationPriority(val rank: Int) {
    HIGH(2),
    NORMAL(1),
    LOW(0),
    ;

    companion object {
        val DEFAULT: TranslationPriority = NORMAL

        /** An unknown stored rank becomes NORMAL, so a job is never left unreadable. */
        fun fromRank(rank: Int): TranslationPriority = values().firstOrNull { it.rank == rank } ?: NORMAL
    }
}
