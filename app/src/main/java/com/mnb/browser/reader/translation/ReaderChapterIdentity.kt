package com.mnb.browser.reader.translation

import com.mnb.browser.extraction.ExtractedContent
import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.NovelId

/**
 * Who a chapter is, as far as translation is concerned (Phase 04-I10-A). A chapter URL alone is
 * NOT an identity: the same chapter can be reached by several URLs, and one URL can show different
 * chapters. [chapterId] is the key for everything translation-related; [sourceUrl] is only
 * informational.
 *
 * [sourceLanguageCode] (Phase 04-I10-C1) is the ISO 639-1 code of the novel's original language,
 * taken from the novel binding. It is null for identities that carry no language; translation is
 * never started for such an identity.
 */
data class ReaderChapterIdentity(
    val novelId: NovelId,
    val chapterId: ChapterId,
    val chapterNumber: Int,
    val sourceUrl: String? = null,
    val sourceLanguageCode: String? = null,
) {
    init {
        require(chapterNumber >= 0) { "chapterNumber must not be negative" }
    }
}

/**
 * Decides which stored chapter the reader's [ExtractedContent] is. Returns null when the chapter
 * cannot be identified with confidence; translation is then unavailable for it (never guessed
 * from the URL). [BoundChapterIdentityResolver] is the real resolver (Phase 04-I10-C1).
 */
fun interface ReaderChapterIdentityResolver {
    fun resolve(content: ExtractedContent): ReaderChapterIdentity?
}

/** Default when no resolver is supplied: no chapter can be identified. */
object NoChapterIdentityResolver : ReaderChapterIdentityResolver {
    override fun resolve(content: ExtractedContent): ReaderChapterIdentity? = null
}
