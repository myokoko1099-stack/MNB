package com.mnb.browser.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 06-A4: input rules and redaction of [ApiKey] on their own (no cipher, no storage). */
class ApiKeyTest {

    private fun parse(raw: String): ApiKey? = ApiKey.parseOrNull(raw)

    // ---- accepted input ----

    @Test
    fun aTypicalKeyIsAcceptedUnchanged() {
        assertEquals(TEST_API_KEY, parse(TEST_API_KEY)!!.reveal())
    }

    @Test
    fun theFirstAndLastAllowedCharactersAreAccepted() {
        assertEquals("!", parse("!")!!.reveal()) // 0x21
        assertEquals("~", parse("~")!!.reveal()) // 0x7E
        assertEquals("!~", parse("!~")!!.reveal())
    }

    @Test
    fun everyPrintableAsciiCharacterWithoutSpaceIsAccepted() {
        val all = (0x21..0x7E).map { it.toChar() }.joinToString("")
        assertEquals(all, parse(all)!!.reveal())
    }

    @Test
    fun surroundingWhitespaceIsTrimmedOff() {
        assertEquals("abc123", parse("  abc123  ")!!.reveal())
        assertEquals("abc123", parse("\t\n abc123 \r\n")!!.reveal())
    }

    // ---- length ----

    @Test
    fun maxLengthIsAcceptedAndOneMoreIsRejected() {
        assertNotNull(parse("a".repeat(ApiKey.MAX_LENGTH)))
        assertNull(parse("a".repeat(ApiKey.MAX_LENGTH + 1)))
    }

    @Test
    fun lengthIsCheckedAfterTrimming() {
        val padded = "a".repeat(ApiKey.MAX_LENGTH) + "   \n"
        assertEquals(ApiKey.MAX_LENGTH, parse(padded)!!.reveal().length)
    }

    @Test
    fun aSingleCharacterIsAccepted() {
        assertEquals("x", parse("x")!!.reveal())
    }

    // ---- rejected input ----

    @Test
    fun emptyAndWhitespaceOnlyTextIsRejected() {
        for (bad in listOf("", " ", "   ", "\t", "\n", " \r\n\t ")) {
            assertNull("expected null for ${bad.length} chars", parse(bad))
        }
    }

    @Test
    fun spacesAndLineBreaksInsideTheKeyAreRejected() {
        for (bad in listOf("ab cd", "ab\tcd", "ab\ncd", "ab\rcd", "ab\r\ncd")) {
            assertNull(parse(bad))
        }
    }

    @Test
    fun controlCharactersAreRejected() {
        for (bad in listOf("ab\u0000cd", "ab\u0001cd", "ab\u001Fcd", "ab\u007Fcd")) {
            assertNull(parse(bad))
        }
    }

    @Test
    fun nonAsciiTextIsRejected() {
        val rejected = listOf(
            "k\u00E9y", // e with acute accent
            "\u1019\u103C\u1014\u103A\u1019\u102C", // Myanmar script
            "key\uD83D\uDE00", // emoji (surrogate pair)
            "\uFF21\uFF22\uFF23", // full-width letters
            "ab\u0080cd", // first character above ASCII
            "ab\u00A0cd", // no-break space inside the text
        )
        for (bad in rejected) assertNull(parse(bad))
    }

    @Test
    fun aBadCharacterAnywhereRejectsTheWholeKey() {
        val good = "AIzaSyTEST"
        assertNull(parse("\u00E9$good"))
        assertNull(parse("${good}\u00E9"))
        assertNull(parse("${good.take(5)}\u00E9${good.drop(5)}"))
    }

    // ---- value semantics and redaction ----

    @Test
    fun equalTextGivesEqualKeysWithEqualHashCodes() {
        assertEquals(ApiKey("abc"), ApiKey("abc"))
        assertEquals(ApiKey("abc").hashCode(), ApiKey("abc").hashCode())
        assertNotEquals(ApiKey("abc"), ApiKey("abd"))
        assertNotEquals(ApiKey("abc"), ApiKey("ABC"))
    }

    @Test
    fun aKeyIsNotEqualToItsPlainText() {
        assertFalse(ApiKey("abc").equals("abc"))
        assertFalse(ApiKey("abc").equals(null))
    }

    @Test
    fun toStringNeverShowsTheKey() {
        val key = ApiKey(TEST_API_KEY)
        assertEquals("ApiKey(***)", key.toString())
        assertFalse(key.toString().contains(TEST_API_KEY))
        assertFalse(key.toString().contains("AIza"))
    }

    @Test
    fun theKeyStaysHiddenInStringTemplatesAndCollections() {
        val key = ApiKey(TEST_API_KEY)
        val rendered = listOf(
            "key=$key",
            listOf(key).toString(),
            mapOf("k" to key).toString(),
            setOf(key).toString(),
            Pair(key, 1).toString(),
            Result.success(key).toString(),
        )
        for (text in rendered) {
            assertFalse(text, text.contains(TEST_API_KEY))
            assertTrue(text, text.contains("ApiKey(***)"))
        }
    }

    @Test
    fun revealIsTheOnlyWayToGetTheText() {
        val key = ApiKey(TEST_API_KEY)
        assertEquals(TEST_API_KEY, key.reveal())
        // The text is not exposed through any other public no-argument getter-like member.
        val publicMethods = ApiKey::class.java.methods
            .filter { it.parameterCount == 0 && it.returnType == String::class.java }
            .map { it.name }
            .toSet()
        assertEquals(setOf("reveal", "toString"), publicMethods)
    }

    @Test
    fun parsedKeysEqualKeysBuiltFromTheTrimmedText() {
        assertEquals(ApiKey("abc123"), parse("  abc123 "))
    }
}
