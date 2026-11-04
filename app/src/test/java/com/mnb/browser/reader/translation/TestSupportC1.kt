package com.mnb.browser.reader.translation

import com.mnb.browser.extraction.ExtractedContent
import com.mnb.browser.extraction.ExtractedParagraph
import com.mnb.browser.reader.KeyValueStore
import com.mnb.browser.translation.ContentHash
import com.mnb.browser.translation.NovelId
import com.mnb.browser.translation.SourceLanguage
import com.mnb.browser.translation.TranslationMetadata
import com.mnb.browser.translation.TranslationProvider
import com.mnb.browser.translation.TranslationRequest
import com.mnb.browser.translation.TranslationResult
import com.mnb.browser.translation.TranslationStatus
import com.mnb.browser.translation.db.StoredTranslation
import com.mnb.browser.translation.usecase.InstantTranslationOutcome
import java.util.ArrayDeque
import java.util.concurrent.Executor

internal class C1KeyValueStore : KeyValueStore {
    val map = LinkedHashMap<String, String>()
    override fun get(key: String): String? = map[key]
    override fun put(key: String, value: String) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
}

internal class C1FakeProvider(
    override val isMock: Boolean = false,
    override val supportedSourceLanguages: Set<SourceLanguage> = SourceLanguage.values().toSet(),
    override val id: String = "fake-real",
) : TranslationProvider {
    override val displayName: String = id
    override fun translate(request: TranslationRequest): TranslationResult = error("not used in these tests")
}

/** Collects work; the test decides when it runs. */
internal class C1ManualExecutor : Executor {
    private val queue = ArrayDeque<Runnable>()
    val pending: Int get() = queue.size
    override fun execute(command: Runnable) { queue.add(command) }
    fun runAll() { while (queue.isNotEmpty()) queue.poll().run() }
}

internal const val C1_NOVEL_URL = "https://site.example/novel/7"
internal val C1_NOVEL = NovelId("novel-7")

internal fun c1Chapter(
    number: Int?,
    url: String = "$C1_NOVEL_URL/ch$number",
    title: String? = number?.let { "Chapter $it: Title" },
) = ExtractedContent(
    pageTitle = "Novel 7",
    chapterTitle = title,
    url = url,
    paragraphs = listOf(ExtractedParagraph("Paragraph one."), ExtractedParagraph("Paragraph two.")),
)

internal fun c1BoundStore(language: String = "en"): NovelBindingStore =
    NovelBindingStore(C1KeyValueStore()).also {
        check(it.save(NovelBinding(C1_NOVEL_URL, C1_NOVEL, "Novel 7", language)))
    }

internal fun c1Stored(
    chapterId: com.mnb.browser.translation.ChapterId,
    text: String? = "မြန်မာစာသား",
    status: TranslationStatus = TranslationStatus.COMPLETED,
    isMock: Boolean = false,
) = StoredTranslation(
    chapterId = chapterId,
    translatedText = text,
    status = status,
    sourceContentHash = ContentHash.of("x"),
    metadata = TranslationMetadata(providerId = "fake-real", createdAtMillis = 1L, translationVersion = 1, isMock = isMock),
)

internal fun c1Completed(s: StoredTranslation) = InstantTranslationOutcome.Completed(s, null, null, 1, 0, false)
