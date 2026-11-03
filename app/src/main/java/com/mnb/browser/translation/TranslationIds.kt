package com.mnb.browser.translation

import java.security.MessageDigest

/** Typed identifiers so a novel id can never be passed where a chapter id is expected. */
@JvmInline
value class NovelId(val value: String) {
    init { require(value.isNotBlank()) { "NovelId must not be blank" } }
}

@JvmInline
value class ChapterId(val value: String) {
    init { require(value.isNotBlank()) { "ChapterId must not be blank" } }
}

@JvmInline
value class ChunkId(val value: String) {
    init { require(value.isNotBlank()) { "ChunkId must not be blank" } }
}

/**
 * SHA-256 of a text, as 64 lowercase hex characters. Used to detect that a chapter's original
 * text changed after it was translated. The text is hashed exactly as given (no trimming).
 */
@JvmInline
value class ContentHash(val value: String) {
    init { require(HEX_64.matches(value)) { "ContentHash must be 64 lowercase hex characters" } }

    companion object {
        private val HEX_64 = Regex("[0-9a-f]{64}")

        fun of(text: String): ContentHash {
            val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            return ContentHash(bytes.joinToString("") { "%02x".format(it) })
        }
    }
}
