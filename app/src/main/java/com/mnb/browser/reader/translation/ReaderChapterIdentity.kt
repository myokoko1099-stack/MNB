package com.mnb.browser.reader.translation

import com.mnb.browser.extraction.ExtractedContent
import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.NovelId

/**
 * Who a chapter is, as far as translation is concerned (Phase 04-I10-A). A chapter URL alone is
 * NOT an identity: the same chapter can be reached by several URLs, and one URL can show different
 * chapters. [chapterId] is the key for everything translation-related; [sourceUrl] is only
 * informational.
 */
data class ReaderChapterIdentity(
    val novelId: NovelId,
    val chapterId: ChapterId,
    val chapterNumber: Int,
    val sourceUrl: String? = null,
) {
    init {
        require(chapterNumber >= 0) { "chapterNumber must not be negative" }
    }
}

/**
 * Decides which stored chapter the reader's [ExtractedContent] is. Returns null when the chapter
 * cannot be identified with confidence; translation is then unavailable for it (never guessed
 * from the URL). The real resolver arrives with the later phases that store chapters.
 */
fun interface ReaderChapterIdentityResolver {
    fun resolve(content: ExtractedContent): ReaderChapterIdentity?
}

/** Default until a real resolver exists: no chapter can be identified. */
object NoChapterIdentityResolver : ReaderChapterIdentityResolver {
    override fun resolve(content: ExtractedContent): ReaderChapterIdentity? = null
}
