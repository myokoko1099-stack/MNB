package com.mnb.browser.translation

/**
 * Progress record for translating one chapter, kept so unfinished work can be resumed after the
 * app is closed. [jobId] is 0 until the job has been stored.
 */
data class TranslationJob(
    val jobId: Long,
    val chapterId: ChapterId,
    val totalChunks: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long = createdAtMillis,
    val completedChunks: Int = 0,
    val status: TranslationStatus = TranslationStatus.PENDING,
    val errorCode: TranslationError? = null,
) {
    init {
        require(jobId >= 0) { "jobId must not be negative" }
        require(totalChunks >= 0) { "totalChunks must not be negative" }
        require(completedChunks in 0..totalChunks) { "completedChunks must be between 0 and totalChunks" }
        require(createdAtMillis >= 0) { "createdAtMillis must not be negative" }
        require(updatedAtMillis >= createdAtMillis) { "updatedAtMillis must not be before createdAtMillis" }
    }
}
