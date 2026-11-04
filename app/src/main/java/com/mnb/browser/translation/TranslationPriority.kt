package com.mnb.browser.translation

/**
 * How soon a queued chapter translation should start (Phase 04-I9-B1). A job with a higher [rank]
 * starts before a job with a lower one; jobs with the same priority start in the order they were
 * queued (FIFO). The priority is fixed when the job is created.
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
