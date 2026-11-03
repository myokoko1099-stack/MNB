package com.mnb.browser.reader

/**
 * Where the reader was in a chapter. [index] and [offset] are the LazyColumn's first visible
 * item index and its pixel scroll offset; [itemCount] is the list size when it was saved.
 */
data class ReaderPosition(val index: Int, val offset: Int, val itemCount: Int)

/** Tiny key/value abstraction so the position logic can be unit-tested without Android. */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

/** Remembers the last reading position per chapter URL, keeping only the most recent chapters. */
class ReaderPositionRepository(
    private val store: KeyValueStore,
    private val maxEntries: Int = 100,
) {
    /**
     * Returns the saved position for [url], or null when nothing usable is saved.
     * If the chapter now has a different number of items the page changed, so the index is
     * clamped into range and the pixel offset is dropped.
     */
    fun load(url: String, itemCount: Int): ReaderPosition? {
        if (!isValidUrl(url) || itemCount <= 0) return null
        val raw = store.get(keyFor(url)) ?: return null
        val parts = raw.split(',')
        if (parts.size != 3) return null
        val index = parts[0].toIntOrNull() ?: return null
        val offset = parts[1].toIntOrNull() ?: return null
        val savedCount = parts[2].toIntOrNull() ?: return null
        if (index < 0 || offset < 0 || savedCount <= 0) return null
        val safeIndex = index.coerceAtMost(itemCount - 1)
        val safeOffset = if (savedCount == itemCount) offset else 0
        return ReaderPosition(safeIndex, safeOffset, itemCount)
    }

    fun save(url: String, position: ReaderPosition) {
        if (!isValidUrl(url) || position.itemCount <= 0) return
        if (position.index < 0 || position.offset < 0) return
        val recent = readRecent().filter { it != url } + url
        val overflow = (recent.size - maxEntries).coerceAtLeast(0)
        recent.take(overflow).forEach { store.remove(keyFor(it)) }
        store.put(RECENT_KEY, recent.drop(overflow).joinToString("\n"))
        store.put(keyFor(url), "${position.index},${position.offset},${position.itemCount}")
    }

    private fun readRecent(): List<String> =
        store.get(RECENT_KEY)?.split('\n')?.filter { it.isNotEmpty() } ?: emptyList()

    private fun isValidUrl(url: String) = url.isNotBlank() && !url.contains('\n')

    private fun keyFor(url: String) = "pos:$url"

    private companion object {
        const val RECENT_KEY = "recent"
    }
}
