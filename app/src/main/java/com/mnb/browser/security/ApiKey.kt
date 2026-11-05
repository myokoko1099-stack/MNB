package com.mnb.browser.security

/**
 * An API key held in memory. The text is only reachable through [reveal], and [toString] never
 * shows it, so putting an [ApiKey] (or a result containing one) into a log line, an exception or a
 * debugger string leaks nothing.
 */
class ApiKey(private val value: String) {

    /** The plain key. Use it only at the point where it is sent to the service. */
    fun reveal(): String = value

    override fun toString(): String = "ApiKey(***)"

    override fun equals(other: Any?): Boolean = other is ApiKey && other.value == value

    override fun hashCode(): Int = value.hashCode()

    companion object {
        const val MAX_LENGTH = 512

        /**
         * Trims surrounding whitespace and checks the rest. Returns null when the text is empty,
         * longer than [MAX_LENGTH], or contains anything other than printable ASCII without spaces
         * (real API keys never do; this also rejects pasted line breaks).
         */
        fun parseOrNull(raw: String): ApiKey? {
            val text = raw.trim()
            if (text.isEmpty() || text.length > MAX_LENGTH) return null
            if (text.any { it.code !in 0x21..0x7E }) return null
            return ApiKey(text)
        }
    }
}
