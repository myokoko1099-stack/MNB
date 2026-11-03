package com.mnb.browser.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TranslationIdsTest {
    @Test fun validIdsAreKept() {
        assertEquals("n1", NovelId("n1").value)
        assertEquals("c1", ChapterId("c1").value)
        assertEquals("k1", ChunkId("k1").value)
    }

    @Test fun blankIdsAreRejected() {
        for (bad in listOf("", "  ", "\n")) {
            assertThrows(IllegalArgumentException::class.java) { NovelId(bad) }
            assertThrows(IllegalArgumentException::class.java) { ChapterId(bad) }
            assertThrows(IllegalArgumentException::class.java) { ChunkId(bad) }
        }
    }

    @Test fun hashIsKnownSha256() {
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            ContentHash.of("hello").value,
        )
    }

    @Test fun hashIsStableAndSensitiveToChanges() {
        assertEquals(ContentHash.of("你好"), ContentHash.of("你好"))
        assertNotEquals(ContentHash.of("abc"), ContentHash.of("abc "))
    }

    @Test fun malformedHashIsRejected() {
        for (bad in listOf("", "abc", "G".repeat(64), "A".repeat(64))) {
            assertThrows(IllegalArgumentException::class.java) { ContentHash(bad) }
        }
    }
}
