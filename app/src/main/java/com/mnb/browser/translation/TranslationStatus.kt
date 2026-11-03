package com.mnb.browser.translation

/** Progress of a chapter or chunk translation. */
enum class TranslationStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED;

    /** True once no more work is running for this item (it may still be retried by the user). */
    val isFinished: Boolean get() = this == COMPLETED || this == FAILED || this == CANCELLED

    companion object {
        /** Returns null for a missing or unknown name instead of throwing. */
        fun fromName(name: String?): TranslationStatus? {
            val n = name?.trim() ?: return null
            return values().firstOrNull { it.name == n }
        }
    }
}

/** How faithful or free a translation should be. LITERARY matches MNB's goal for novels. */
enum class TranslationStyle {
    LITERARY,
    NATURAL,
    LITERAL;

    companion object {
        fun fromName(name: String?): TranslationStyle? {
            val n = name?.trim() ?: return null
            return values().firstOrNull { it.name == n }
        }
    }
}
