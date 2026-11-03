package com.mnb.browser.translation

/** Which provider/model produced a translation and when. [isMock] keeps placeholder output identifiable. */
data class TranslationMetadata(
    val providerId: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long = createdAtMillis,
    val modelId: String? = null,
    val translationVersion: Int = 1,
    val isMock: Boolean = false,
) {
    init {
        require(providerId.isNotBlank()) { "providerId must not be blank" }
        require(modelId == null || modelId.isNotBlank()) { "modelId must be null or not blank" }
        require(translationVersion >= 1) { "translationVersion must be at least 1" }
        require(createdAtMillis >= 0) { "createdAtMillis must not be negative" }
        require(updatedAtMillis >= createdAtMillis) { "updatedAtMillis must not be before createdAtMillis" }
    }

    fun touched(nowMillis: Long): TranslationMetadata = copy(updatedAtMillis = nowMillis)

    companion object {
        fun fromResult(
            result: TranslationResult.Success,
            nowMillis: Long,
            modelId: String? = null,
        ): TranslationMetadata = TranslationMetadata(
            providerId = result.providerId,
            createdAtMillis = nowMillis,
            modelId = modelId,
            isMock = result.isMock,
        )
    }
}
