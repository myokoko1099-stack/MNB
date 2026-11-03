package com.mnb.browser.translation

/** Languages MNB can translate FROM. Codes are ISO 639-1. */
enum class SourceLanguage(val code: String) {
    ENGLISH("en"),
    CHINESE("zh"),
    KOREAN("ko"),
    JAPANESE("ja");

    companion object {
        /** Returns null for a missing or unsupported code instead of throwing. */
        fun fromCode(code: String?): SourceLanguage? {
            val normalized = code?.trim()?.lowercase() ?: return null
            return values().firstOrNull { it.code == normalized }
        }
    }
}

/** Languages MNB can translate INTO. Only Myanmar for now; kept as an enum so it can grow. */
enum class TargetLanguage(val code: String) {
    MYANMAR("my"),
}
