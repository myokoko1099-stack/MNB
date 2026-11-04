package com.mnb.browser.reader.translation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mnb.browser.extraction.ExtractedContent
import com.mnb.browser.translation.ChapterId

/** The open chapter's identity (null when it could not be identified) with its translation state. */
data class ReaderChapterTranslation(
    val identity: ReaderChapterIdentity?,
    val state: ReaderTranslationState,
)

/**
 * Translation state of the chapter the reader has open (Phase 04-I10-A). It tracks exactly one
 * chapter, keyed by [ChapterId] and never by URL, so states of different chapters cannot mix and a
 * chapter can never have two states:
 *  - opening a chapter starts it at [ReaderTranslationState.OriginalOnly]; nothing is carried over
 *    from the chapter that was open before;
 *  - an update for any chapter other than the open one (for example a late result from a chapter
 *    the reader already left) is ignored.
 * [current] is Compose state, so a screen can read it directly. Updates may come from any thread.
 *
 * [chapterListener] (Phase 04-I10-C2, optional) is told about every opened chapter that has an
 * identity, so background translation can follow reader navigation. It is called on the caller's
 * thread while the holder is locked, so it must return quickly (hand the work to an executor) and
 * must not call back into the holder; an exception it throws is ignored.
 */
class ReaderTranslationStateHolder(
    private val resolver: ReaderChapterIdentityResolver = NoChapterIdentityResolver,
    private val gateway: ReaderTranslationGateway = UnavailableReaderTranslationGateway,
    private val chapterListener: ((ReaderChapterIdentity, ExtractedContent) -> Unit)? = null,
) {
    /** The open chapter's translation, or null when no chapter is open. */
    var current: ReaderChapterTranslation? by mutableStateOf(null)
        private set

    @Synchronized
    fun onChapterOpened(content: ExtractedContent) {
        val identity = resolver.resolve(content)
        current = if (identity == null) {
            ReaderChapterTranslation(
                null,
                ReaderTranslationState.Unavailable(ReaderTranslationUnavailableReason.NO_CHAPTER_IDENTITY),
            )
        } else {
            ReaderChapterTranslation(identity, ReaderTranslationState.OriginalOnly)
        }
        if (identity != null) {
            try {
                chapterListener?.invoke(identity, content)
            } catch (e: Exception) {
                // Background translation must never break the reader.
            }
        }
    }

    @Synchronized
    fun onChapterClosed() {
        current = null
    }

    /**
     * Sets the state of [chapterId]. Returns true when it was the open chapter and its state was
     * applied; false when it was ignored because that chapter is not open.
     */
    @Synchronized
    fun update(chapterId: ChapterId, state: ReaderTranslationState): Boolean {
        val open = current ?: return false
        if (open.identity?.chapterId != chapterId) return false
        current = open.copy(state = state)
        return true
    }

    /**
     * Asks the gateway for the open chapter's state and applies it. Blocking (the gateway may read a
     * database): call off the main thread. Returns the applied state, or null when no identified
     * chapter is open or the reader moved to another chapter meanwhile (the stale answer is dropped).
     */
    fun refresh(): ReaderTranslationState? {
        val identity = synchronized(this) { current?.identity } ?: return null
        val state = gateway.stateOf(identity)
        return if (update(identity.chapterId, state)) state else null
    }
}
