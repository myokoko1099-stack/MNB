package com.mnb.browser.translation.chunking

import com.mnb.browser.translation.TranslationRequest

/**
 * Size rules for chunking, counted in UTF-16 characters (the same unit as
 * [TranslationRequest.MAX_TEXT_LENGTH]). A chunk never exceeds [maxChunkChars]; chunks shorter
 * than [minChunkChars] are avoided where the text allows it.
 */
data class ChunkingConfig(
    val maxChunkChars: Int = DEFAULT_MAX_CHUNK_CHARS,
    val minChunkChars: Int = minOf(DEFAULT_MIN_CHUNK_CHARS, maxChunkChars / 4),
) {
    init {
        require(maxChunkChars in 1..TranslationRequest.MAX_TEXT_LENGTH) {
            "maxChunkChars must be between 1 and ${TranslationRequest.MAX_TEXT_LENGTH}"
        }
        require(minChunkChars in 0..maxChunkChars) { "minChunkChars must be between 0 and maxChunkChars" }
    }

    companion object {
        const val DEFAULT_MAX_CHUNK_CHARS = 3_000
        const val DEFAULT_MIN_CHUNK_CHARS = 500
    }
}
