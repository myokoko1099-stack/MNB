package com.mnb.browser.reader.translation

import com.mnb.browser.translation.SourceLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The chapter listener added in Phase 04-I10-C2 reports navigation without changing the holder's rules. */
class ReaderTranslationStateHolderChapterListenerTest {
    private fun resolver() = BoundChapterIdentityResolver(c1BoundStore(SourceLanguage.CHINESE.code))

    @Test fun listenerGetsEveryIdentifiedChapterAndItsContent() {
        val seen = mutableListOf<Pair<ReaderChapterIdentity, String>>()
        val holder = ReaderTranslationStateHolder(resolver(), chapterListener = { id, content -> seen += id to content.url })
        holder.onChapterOpened(c1Chapter(1))
        holder.onChapterOpened(c1Chapter(2))
        assertEquals(listOf(1, 2), seen.map { it.first.chapterNumber })
        assertEquals(listOf("$C1_NOVEL_URL/ch1", "$C1_NOVEL_URL/ch2"), seen.map { it.second })
        assertEquals(holder.current!!.identity, seen.last().first)
    }

    @Test fun listenerIsNotCalledForAChapterWithoutIdentity() {
        var calls = 0
        val holder = ReaderTranslationStateHolder(resolver(), chapterListener = { _, _ -> calls++ })
        holder.onChapterOpened(c1Chapter(number = null, url = "https://other.example/x"))
        assertEquals(0, calls)
        assertEquals(
            ReaderTranslationState.Unavailable(ReaderTranslationUnavailableReason.NO_CHAPTER_IDENTITY),
            holder.current!!.state,
        )
    }

    @Test fun aFailingListenerDoesNotBreakOpeningAChapter() {
        val holder = ReaderTranslationStateHolder(resolver(), chapterListener = { _, _ -> error("boom") })
        holder.onChapterOpened(c1Chapter(1))
        assertTrue(holder.current!!.identity != null)
        assertEquals(ReaderTranslationState.OriginalOnly, holder.current!!.state)
    }

    @Test fun withoutAListenerTheHolderBehavesAsBefore() {
        val holder = ReaderTranslationStateHolder(resolver())
        holder.onChapterOpened(c1Chapter(1))
        assertEquals(ReaderTranslationState.OriginalOnly, holder.current!!.state)
    }
}
