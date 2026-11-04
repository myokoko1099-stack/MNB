package com.mnb.browser.translation.usecase

import com.mnb.browser.translation.ChapterId
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-wide, in-memory "one translation per chapter at a time" lock (Phase 04-I10-D).
 *
 * Instant (reader) and background (WorkManager) translation build their own pipeline objects, so a
 * per-pipeline guard cannot see the other side. Every [InstantTranslationPipeline] takes this lock
 * (by default [shared]) before it touches a chapter and releases it in a `finally`, so success,
 * failure, cancellation and exceptions all release it.
 *
 * Non-blocking: [tryAcquire] returns false at once when the chapter is busy; the caller defers or
 * rejects. Nothing is persisted, so a process restart always starts with every chapter free; a
 * crashed worker cannot leave a chapter locked.
 */
class ChapterTranslationLock {
    private val held: MutableSet<String> = ConcurrentHashMap.newKeySet<String>()

    /** True when the caller now owns the chapter and must call [release]. */
    fun tryAcquire(chapterId: ChapterId): Boolean = held.add(chapterId.value)

    fun release(chapterId: ChapterId) {
        held.remove(chapterId.value)
    }

    fun isHeld(chapterId: ChapterId): Boolean = chapterId.value in held

    companion object {
        /** The lock used by the app. Tests may pass their own instance to the pipeline instead. */
        val shared = ChapterTranslationLock()
    }
}
