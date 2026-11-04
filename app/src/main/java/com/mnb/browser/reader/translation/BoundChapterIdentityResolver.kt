package com.mnb.browser.reader.translation

import com.mnb.browser.extraction.ExtractedContent
import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.NovelId
import com.mnb.browser.translation.SourceLanguage

/**
 * The real [ReaderChapterIdentityResolver] (Phase 04-I10-C1). A chapter is identified only when
 *  1. its URL matches a stored [NovelBinding],
 *  2. the binding's source language is supported, and
 *  3. [ChapterTitleParser] finds an unambiguous chapter number in the chapter title.
 * Otherwise it returns null and the reader reports "chapter not identified"; nothing is guessed.
 */
class BoundChapterIdentityResolver(private val bindings: NovelBindingStore) : ReaderChapterIdentityResolver {

    override fun resolve(content: ExtractedContent): ReaderChapterIdentity? = try {
        val binding = bindings.find(content.url)
        val language = SourceLanguage.fromCode(binding?.sourceLanguageCode)
        val number = ChapterTitleParser.parse(content.chapterTitle)
        if (binding == null || language == null || number == null) {
            null
        } else {
            ReaderChapterIdentity(
                novelId = binding.novelId,
                chapterId = chapterIdFor(binding.novelId, number),
                chapterNumber = number,
                sourceUrl = content.url,
                sourceLanguageCode = language.code,
            )
        }
    } catch (e: Exception) {
        null
    }

    companion object {
        /** Stable id: the same novel and chapter number always give the same id, whatever the URL. */
        fun chapterIdFor(novelId: NovelId, chapterNumber: Int): ChapterId =
            ChapterId("${novelId.value}#ch$chapterNumber")
    }
}
