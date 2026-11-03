package com.mnb.browser.translation

/** Per-novel translation settings. Dates are epoch milliseconds (java.time needs API 26+). */
data class NovelTranslationInfo(
    val novelId: NovelId,
    val novelTitle: String,
    val sourceLanguage: SourceLanguage,
    val createdAtMillis: Long,
    val targetLanguage: TargetLanguage = TargetLanguage.MYANMAR,
    val style: TranslationStyle = TranslationStyle.LITERARY,
) {
    init {
        require(novelTitle.isNotBlank()) { "novelTitle must not be blank" }
        require(createdAtMillis >= 0) { "createdAtMillis must not be negative" }
    }
}
