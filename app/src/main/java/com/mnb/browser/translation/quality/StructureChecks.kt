package com.mnb.browser.translation.quality

import com.mnb.browser.translation.chunking.ChunkReassembler
import com.mnb.browser.translation.chunking.ParagraphSplitter
import com.mnb.browser.translation.memory.MemoryText

/** Is the translation put together correctly? Functions returning null mean the check could not run. */
internal object StructureChecks {

    fun chunkSequence(ctx: CheckContext): List<QualityIssue> {
        val chunks = ctx.input.chunks
        val issues = ArrayList<QualityIssue>()
        val report = ChunkReassembler.check(ctx.source.joinToString(ParagraphSplitter.SEPARATOR), chunks)

        for (index in report.missingIndices) {
            issues += QualityIssue(
                QualityIssueType.MISSING_CHUNK,
                QualitySeverity.MAJOR,
                "Chunk #$index is missing from the sequence",
                null,
                QualityLocation(chunkIndex = index),
                "Re-create or re-translate the missing chunk",
            )
        }
        for (index in report.duplicateIndices) {
            issues += QualityIssue(
                QualityIssueType.DUPLICATE_CHUNK,
                QualitySeverity.MAJOR,
                "More than one chunk has index #$index",
                null,
                QualityLocation(chunkIndex = index),
                "Remove the duplicate chunk",
            )
        }
        for (id in report.duplicateChunkIds) {
            issues += QualityIssue(
                QualityIssueType.DUPLICATE_CHUNK,
                QualitySeverity.MAJOR,
                "Chunk id '$id' is used more than once",
                null,
                null,
                "Remove the duplicate chunk",
            )
        }
        for (p in report.uncoveredParagraphs) {
            issues += QualityIssue(
                QualityIssueType.INVALID_CHUNK_SEQUENCE,
                QualitySeverity.MAJOR,
                "Source paragraph #${p + 1} is not covered by any chunk",
                ctx.srcLoc(p),
                null,
                "Re-split the chapter into chunks",
            )
        }
        ChunkReassembler.ordered(chunks).zipWithNext().forEach { (prev, cur) ->
            if (cur.startParagraphIndex < prev.endParagraphIndex || cur.startParagraphIndex > prev.endParagraphIndex + 1) {
                issues += QualityIssue(
                    QualityIssueType.INVALID_CHUNK_SEQUENCE,
                    QualitySeverity.MAJOR,
                    "Chunk #${cur.chunkIndex} starts at paragraph #${cur.startParagraphIndex + 1} " +
                        "but chunk #${prev.chunkIndex} ended at paragraph #${prev.endParagraphIndex + 1}",
                    null,
                    QualityLocation(paragraphIndex = cur.startParagraphIndex, chunkIndex = cur.chunkIndex),
                    "Re-split the chapter into chunks",
                )
            }
        }
        if (issues.isEmpty() && !report.textMatches) {
            issues += QualityIssue(
                QualityIssueType.INVALID_CHUNK_SEQUENCE,
                QualitySeverity.MAJOR,
                "The chunks do not rebuild the source text",
                null,
                null,
                "Re-split the chapter into chunks",
            )
        }
        return issues
    }

    /** The same long translated paragraph twice, where the source paragraphs differ, is repeated output. */
    fun duplicateParagraphs(ctx: CheckContext): List<QualityIssue> {
        val seen = HashMap<String, Int>()
        val issues = ArrayList<QualityIssue>()
        ctx.translated.forEachIndexed { j, p ->
            val key = MemoryText.normalize(p)
            if (key.length < ctx.config.minDuplicateLength) return@forEachIndexed
            val i = seen[key]
            if (i == null) {
                seen[key] = j
            } else {
                val sourcesEqual = i < ctx.source.size && j < ctx.source.size &&
                    MemoryText.normalize(ctx.source[i]) == MemoryText.normalize(ctx.source[j])
                if (!sourcesEqual) {
                    issues += QualityIssue(
                        QualityIssueType.DUPLICATE_PARAGRAPH,
                        QualitySeverity.MAJOR,
                        "Translated paragraph #${j + 1} repeats paragraph #${i + 1}, but their source paragraphs differ",
                        ctx.srcLoc(j),
                        ctx.trLoc(j),
                        "Re-translate this paragraph; the provider may have repeated its output",
                    )
                }
            }
        }
        return issues
    }

    /**
     * Looks for a translated paragraph that carries none of its own source paragraph's anchors
     * (numbers and agreed term translations) but does carry another paragraph's. It is a
     * heuristic: a swap of two paragraphs is MAJOR, a single suspicious paragraph is MINOR.
     * Returns null when no source paragraph has any anchor, so the check cannot run.
     */
    fun paragraphOrder(ctx: CheckContext): List<QualityIssue>? {
        val n = minOf(ctx.source.size, ctx.translated.size)
        if (n < 2) return null
        val expectations = TerminologyChecks.expectations(ctx.input)
        val anchors: List<Set<String>> = (0 until n).map { anchorsFor(ctx.source[it], expectations, ctx.config) }
        if (anchors.all { it.isEmpty() }) return null

        val translatedKeys = (0 until n).map { QualityText.key(ctx.translated[it]) }
        val window = ctx.config.orderWindow

        fun score(translatedIndex: Int, sourceIndex: Int): Int =
            anchors[sourceIndex].count { translatedKeys[translatedIndex].contains(it) }

        /** The other source paragraph whose anchors this translated paragraph carries most, with its score. */
        fun bestOther(i: Int): Pair<Int, Int>? {
            var best = -1
            var bestScore = 0
            for (j in maxOf(0, i - window)..minOf(n - 1, i + window)) {
                if (j == i) continue
                val s = score(i, j)
                if (s > bestScore) {
                    bestScore = s
                    best = j
                }
            }
            return if (best >= 0) best to bestScore else null
        }

        val issues = ArrayList<QualityIssue>()
        for (i in 0 until n) {
            if (anchors[i].isEmpty() || score(i, i) > 0) continue
            val (j, _) = bestOther(i) ?: continue
            val mutual = anchors[j].isNotEmpty() && score(j, j) == 0 && bestOther(j)?.first == i
            issues += QualityIssue(
                QualityIssueType.PARAGRAPH_ORDER_MISMATCH,
                if (mutual) QualitySeverity.MAJOR else QualitySeverity.MINOR,
                "Translated paragraph #${i + 1} matches source paragraph #${j + 1} better than #${i + 1}" +
                    (if (mutual) " (the two look swapped)" else ""),
                ctx.srcLoc(i),
                ctx.trLoc(i),
                "Check the paragraph order of the translation",
            )
        }
        return issues
    }

    private fun anchorsFor(sourceParagraph: String, expectations: List<TermExpectation>, config: QualityConfig): Set<String> {
        val anchors = LinkedHashSet<String>()
        QualityText.digitRuns(sourceParagraph, config.minNumberDigits).forEach { anchors += it }
        val key = MemoryText.normalize(sourceParagraph)
        for (e in expectations) {
            if (e.appearsIn(key)) anchors += QualityText.key(e.expected)
        }
        return anchors
    }
}
