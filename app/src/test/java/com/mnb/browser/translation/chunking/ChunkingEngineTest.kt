package com.mnb.browser.translation.chunking

import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.ChunkId
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkingEngineTest {
    private val c1 = ChapterId("c1")

    private fun engine(max: Int, min: Int = max / 4) = ChunkingEngine(ChunkingConfig(max, min))
    private fun normalized(text: String) = ParagraphSplitter.normalize(text)
    private fun para(prefix: String, len: Int) = prefix + "x".repeat(len - prefix.length)
    private fun dialogue(len: Int) = "“" + "d".repeat(len - 2) + "”"

    private fun twelveParagraphs() = (0 until 12).joinToString("\n\n") { para("P$it ", 300) }

    // 1
    @Test fun normalChapterIsSplitIntoBoundedChunks() {
        val text = twelveParagraphs()
        val chunks = engine(1000).chunk(c1, text)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.characterCount <= 1000 })
        assertEquals(normalized(text), ChunkReassembler.reassemble(chunks))
        assertTrue(ChunkReassembler.check(text, chunks).isValid)
    }

    // 2
    @Test fun veryLongChapterIsHandled() {
        val text = (0 until 2000).joinToString("\n") { para("P$it ", 200) }
        val chunks = ChunkingEngine().chunk(c1, text)
        assertTrue(chunks.size > 100)
        assertTrue(chunks.all { it.characterCount <= ChunkingConfig.DEFAULT_MAX_CHUNK_CHARS })
        assertTrue(ChunkReassembler.check(text, chunks).isValid)
    }

    // 3
    @Test fun shortParagraphsAreGroupedIntoFewChunks() {
        val text = (0 until 50).joinToString("\n") { "ab${it % 10}cd" }
        val chunks = engine(100, 25).chunk(c1, text)
        assertTrue(chunks.size < 10)
        assertTrue("no chunk below the minimum", chunks.all { it.characterCount >= 25 })
        assertTrue(chunks.all { it.characterCount <= 100 })
        assertTrue(ChunkReassembler.check(text, chunks).isValid)
    }

    // 4
    @Test fun longParagraphIsSplitAtSentenceEnds() {
        val long = (0 until 30).joinToString(" ") { "Sentence number $it has some words in it." }
        val text = "Short start.\n$long\nShort end."
        val chunks = engine(400, 50).chunk(c1, text)
        assertTrue(chunks.all { it.characterCount <= 400 })
        assertTrue(chunks.count { it.startParagraphIndex == 1 || it.endParagraphIndex == 1 } >= 3)
        for (c in chunks.filter { it.endParagraphIndex == 1 }) assertTrue(c.originalText.trimEnd().endsWith("."))
        assertEquals(normalized(text), ChunkReassembler.reassemble(chunks))
        assertTrue(ChunkReassembler.check(text, chunks).isValid)
    }

    // 5
    @Test fun singleLongSentenceIsCutOnlyAsALastResort() {
        val text = "y".repeat(1000)
        val chunks = engine(300, 75).chunk(c1, text)
        assertEquals(listOf(300, 300, 300, 100), chunks.map { it.characterCount })
        assertTrue(chunks.all { it.startParagraphIndex == 0 && it.endParagraphIndex == 0 })
        assertEquals(text, ChunkReassembler.reassemble(chunks))
    }

    // 6
    @Test fun paragraphsAreKeptSeparateAndInOrder() {
        val paragraphs = (0 until 5).map { para("P$it ", 50) }
        val chunks = engine(1000).chunk(c1, paragraphs.joinToString("\n\n"))
        assertEquals(1, chunks.size)
        assertEquals(paragraphs, chunks[0].originalText.split("\n"))
        assertEquals(0, chunks[0].startParagraphIndex)
        assertEquals(4, chunks[0].endParagraphIndex)
    }

    // 7
    @Test fun dialogueExchangeStaysInOneChunk() {
        val narrative = para("Narrative ", 40)
        val lines = List(4) { dialogue(20) }
        val chunks = engine(100, 20).chunk(c1, (listOf(narrative) + lines).joinToString("\n"))
        assertEquals(2, chunks.size)
        assertEquals(narrative, chunks[0].originalText)
        assertEquals(lines, chunks[1].originalText.split("\n"))
    }

    // 8
    @Test fun emptyInputGivesNoChunks() {
        assertTrue(engine(100).chunk(c1, "").isEmpty())
        assertTrue(engine(100).chunk(c1, "  \n \t\r\n　").isEmpty())
        assertTrue(ChunkReassembler.check("", emptyList()).isValid)
    }

    // 9
    @Test fun whitespaceIsTrimmedAroundParagraphsOnly() {
        val text = "  \n\n First para  \r\n\r\n　　Second  para\n   \nThird\n"
        val chunks = ChunkingEngine().chunk(c1, text)
        assertEquals(1, chunks.size)
        assertEquals("First para\nSecond  para\nThird", chunks[0].originalText)
    }

    // 10
    @Test fun chunkIndexesStartAtZeroAndFollowTheText() {
        val chunks = engine(700).chunk(c1, twelveParagraphs())
        assertEquals(chunks.indices.toList(), chunks.map { it.chunkIndex })
        assertEquals(chunks.size, chunks.map { it.chunkId.value }.toSet().size)
        assertEquals(0, chunks.first().startParagraphIndex)
        assertEquals(11, chunks.last().endParagraphIndex)
        assertTrue(chunks.zipWithNext().all { (a, b) -> b.startParagraphIndex - a.endParagraphIndex in 0..1 })
    }

    // 11
    @Test fun shuffledChunksReassembleInOriginalOrder() {
        val long = (0 until 40).joinToString(" ") { "Sentence $it goes here." }
        val text = "Intro paragraph.\n$long\nClosing paragraph."
        val chunks = engine(300, 40).chunk(c1, text)
        assertTrue(chunks.size > 3)
        assertEquals(normalized(text), ChunkReassembler.reassemble(chunks.shuffled(Random(42))))
    }

    // 12
    @Test fun missingChunksAreDetected() {
        val text = twelveParagraphs()
        val chunks = engine(700).chunk(c1, text)
        assertTrue(chunks.size >= 3)

        val middle = ChunkReassembler.check(text, chunks.filterNot { it.chunkIndex == 1 })
        assertEquals(listOf(1), middle.missingIndices)
        assertTrue(middle.uncoveredParagraphs.isNotEmpty())
        assertTrue(!middle.textMatches && !middle.isValid)

        val tail = ChunkReassembler.check(text, chunks.dropLast(1))
        assertTrue(tail.missingIndices.isEmpty())
        assertTrue(tail.uncoveredParagraphs.isNotEmpty())
        assertTrue(!tail.isValid)
    }

    // 13
    @Test fun duplicateChunksAreDetected() {
        val text = twelveParagraphs()
        val chunks = engine(700).chunk(c1, text)

        val same = ChunkReassembler.check(text, chunks + chunks[1])
        assertEquals(listOf(1), same.duplicateIndices)
        assertEquals(listOf(chunks[1].chunkId.value), same.duplicateChunkIds)
        assertTrue(!same.isValid)

        val otherId = chunks[1].copy(chunk = chunks[1].chunk.copy(chunkId = ChunkId("other")))
        assertEquals(listOf(1), ChunkReassembler.check(text, chunks + otherId).duplicateIndices)
    }

    // 16
    @Test fun sameInputGivesIdenticalChunks() {
        val text = twelveParagraphs() + "\n" + (0 until 40).joinToString(" ") { "Sentence $it goes here." }
        assertEquals(engine(500).chunk(c1, text), engine(500).chunk(c1, text))
    }

    // extras
    @Test fun configIsValidated() {
        assertThrows(IllegalArgumentException::class.java) { ChunkingConfig(0) }
        assertThrows(IllegalArgumentException::class.java) { ChunkingConfig(20_001) }
        assertThrows(IllegalArgumentException::class.java) { ChunkingConfig(100, 101) }
    }

    @Test fun surrogatePairsAreNeverCut() {
        val text = "😀".repeat(20)
        val chunks = engine(5, 1).chunk(c1, text)
        assertTrue(chunks.all { it.originalText.length % 2 == 0 && it.characterCount <= 5 })
        assertEquals(text, ChunkReassembler.reassemble(chunks))
    }

    @Test fun neighborContextGivesTextAroundAChunk() {
        val chunks = engine(700).chunk(c1, twelveParagraphs())
        val ctx = ChunkNeighbors.around(chunks, 1, 10)
        assertEquals(chunks[0].originalText.takeLast(10), ctx.previousText)
        assertEquals(chunks[2].originalText.take(10), ctx.nextText)
        assertNull(ChunkNeighbors.around(chunks, 0, 10).previousText)
        assertNull(ChunkNeighbors.around(chunks, chunks.lastIndex, 10).nextText)
    }
}
