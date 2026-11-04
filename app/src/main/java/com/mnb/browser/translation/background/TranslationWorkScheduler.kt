package com.mnb.browser.translation.background

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.mnb.browser.translation.ChapterId
import java.util.concurrent.TimeUnit

/** Hands one chapter translation to a background executor. Blocking: call off the main thread. */
interface TranslationWorkScheduler {
    fun schedule(chapterId: ChapterId, sourceLanguageCode: String?, targetLanguageCode: String)

    /** True while the chapter still has live work (waiting for the network, running or retrying). */
    fun isScheduled(chapterId: ChapterId): Boolean

    fun cancel(chapterId: ChapterId)
}

/**
 * WorkManager implementation: one unique work per chapter. KEEP means a second request for a
 * chapter that still has live work is ignored; work that already finished is replaced. The work
 * needs a connected network, so it waits while offline and starts when the network returns.
 */
class WorkManagerTranslationScheduler(private val workManager: WorkManager) : TranslationWorkScheduler {
    override fun schedule(chapterId: ChapterId, sourceLanguageCode: String?, targetLanguageCode: String) {
        workManager.enqueueUniqueWork(
            uniqueName(chapterId),
            ExistingWorkPolicy.KEEP,
            buildRequest(chapterId, sourceLanguageCode, targetLanguageCode),
        )
    }

    override fun isScheduled(chapterId: ChapterId): Boolean =
        workManager.getWorkInfosForUniqueWork(uniqueName(chapterId)).get().any { !it.state.isFinished }

    override fun cancel(chapterId: ChapterId) {
        workManager.cancelUniqueWork(uniqueName(chapterId))
    }

    companion object {
        const val KEY_CHAPTER_ID = "chapterId"
        const val KEY_SOURCE_LANGUAGE = "sourceLanguage"
        const val KEY_TARGET_LANGUAGE = "targetLanguage"
        const val TAG = "mnb-translation"
        const val BACKOFF_SECONDS = 30L

        fun uniqueName(chapterId: ChapterId) = "mnb-translate-" + chapterId.value

        fun buildRequest(
            chapterId: ChapterId,
            sourceLanguageCode: String?,
            targetLanguageCode: String,
        ): OneTimeWorkRequest = OneTimeWorkRequest.Builder(TranslationWorker::class.java)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .setInputData(
                Data.Builder()
                    .putString(KEY_CHAPTER_ID, chapterId.value)
                    .putString(KEY_SOURCE_LANGUAGE, sourceLanguageCode)
                    .putString(KEY_TARGET_LANGUAGE, targetLanguageCode)
                    .build(),
            )
            .addTag(TAG)
            .build()
    }
}
