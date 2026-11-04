package com.mnb.browser.reader.translation

import com.mnb.browser.reader.KeyValueStore
import com.mnb.browser.translation.NovelId
import com.mnb.browser.translation.SourceLanguage

/**
 * Ties a novel's URL prefix to its [novelId] and original language (Phase 04-I10-C1).
 * [urlPrefix] is an http(s) URL such as "https://example.com/novel/123"; every chapter URL that
 * starts with it (on a path boundary) belongs to this novel.
 */
data class NovelBinding(
    val urlPrefix: String,
    val novelId: NovelId,
    val novelTitle: String,
    val sourceLanguageCode: String,
)

/**
 * Stores [NovelBinding]s in a [KeyValueStore] (outside the database, no schema change).
 * Everything is validated on save, so a stored binding always has a usable prefix and a supported
 * source language. Lookup picks the longest matching prefix; no match means "unknown novel".
 */
class NovelBindingStore(private val store: KeyValueStore) {

    /** Saves or replaces the binding of its prefix. Returns false (nothing stored) when invalid or conflicting. */
    @Synchronized
    fun save(binding: NovelBinding): Boolean {
        val language = SourceLanguage.fromCode(binding.sourceLanguageCode) ?: return false
        val clean = NovelBinding(
            urlPrefix = binding.urlPrefix.trim(),
            novelId = binding.novelId,
            novelTitle = binding.novelTitle.trim(),
            sourceLanguageCode = language.code,
        )
        if (!isValid(clean)) return false
        val others = list().filter { it.urlPrefix != clean.urlPrefix }
        // One novel has one original language, however many URL prefixes point at it.
        if (others.any { it.novelId == clean.novelId && it.sourceLanguageCode != clean.sourceLanguageCode }) return false
        write(others + clean)
        return true
    }

    @Synchronized
    fun remove(urlPrefix: String): Boolean {
        val all = list()
        val rest = all.filter { it.urlPrefix != urlPrefix.trim() }
        if (rest.size == all.size) return false
        write(rest)
        return true
    }

    @Synchronized
    fun list(): List<NovelBinding> =
        (store.get(KEY) ?: "").split('\n').mapNotNull { decode(it) }

    /** The binding whose prefix is the longest match for [url], or null when none matches. */
    fun find(url: String): NovelBinding? {
        val clean = url.trim()
        if (clean.isEmpty()) return null
        return list().filter { matches(it.urlPrefix, clean) }.maxByOrNull { it.urlPrefix.length }
    }

    private fun write(all: List<NovelBinding>) {
        if (all.isEmpty()) store.remove(KEY) else store.put(KEY, all.joinToString("\n") { encode(it) })
    }

    private fun matches(prefix: String, url: String): Boolean {
        if (!url.startsWith(prefix)) return false
        // "…/novel/1" must not match "…/novel/12/…".
        return url.length == prefix.length || prefix.endsWith('/') || url[prefix.length] in "/?#"
    }

    private fun isValid(b: NovelBinding): Boolean =
        (b.urlPrefix.startsWith("http://") || b.urlPrefix.startsWith("https://")) &&
            b.urlPrefix.length > "https://".length &&
            b.urlPrefix.none { it.isWhitespace() } &&
            b.novelTitle.isNotEmpty() &&
            b.novelTitle.none { it == '\t' || it == '\n' || it == '\r' } &&
            b.novelId.value.none { it == '\t' || it == '\n' || it == '\r' }

    private fun encode(b: NovelBinding) =
        listOf(b.urlPrefix, b.novelId.value, b.novelTitle, b.sourceLanguageCode).joinToString("\t")

    private fun decode(line: String): NovelBinding? {
        val parts = line.split('\t')
        if (parts.size != 4) return null
        return try {
            NovelBinding(parts[0], NovelId(parts[1]), parts[2], parts[3])
                .takeIf { isValid(it) && SourceLanguage.fromCode(it.sourceLanguageCode) != null }
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private companion object {
        const val KEY = "novelbind:list"
    }
}
