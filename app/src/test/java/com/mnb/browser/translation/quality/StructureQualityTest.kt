package com.mnb.browser.translation.quality

import com.mnb.browser.translation.quality.QualityTestData.chunk
import com.mnb.browser.translation.quality.QualityTestData.chunks
import com.mnb.browser.translation.quality.QualityTestData.cleanInput
import com.mnb.browser.translation.quality.QualityTestData.source
import com.mnb.browser.translation.quality.QualityTestData.sourceText
import com.mnb.browser.translation.quality.QualityTestData.translated
import com.mnb.browser.translation.quality.QualityTestData.translatedText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StructureQualityTest {
    private val checker = TranslationQualityChecker()

    // ---- duplicate paragraphs

    @Test fun aRepeatedTranslatedParagraphIsReported() {
        val result = checker.check(cleanInput(listOf(translated[0], translated[1], translated[1])))
        val issue = result.issueOf(QualityIssueType.DUPLICATE_PARAGRAPH)!!
        assertEquals(QualitySeverity.MAJOR, issue.severity)
        assertEquals(2, issue.translationLocation!!.paragraphIndex)
        assertEquals(CheckStatus.FAILED, result.structural)
    }

    @Test fun repeatingTheSourceIsNotADuplicateProblem() {
        val repeated = listOf("He nodded slowly at the gate.", "He nodded slowly at the gate.")
        val line = "သူသည် တံခါးဝတွင် ဖြည်းဖြည်းချင်း ခေါင်းညိတ်ခဲ့သည်။"
        val result = checker.check(QualityInput(repeated, listOf(line, line)))
        assertFalse(result.has(QualityIssueType.DUPLICATE_PARAGRAPH))
        assertTrue(QualityCheckType.DUPLICATE_PARAGRAPHS in result.passedChecks)
    }

    @Test fun shortRepeatedLinesAreIgnored() {
        val result = checker.check(QualityInput(listOf("Yes.", "No.", "Maybe."), listOf("ဟုတ်။", "ဟုတ်။", "ဟုတ်။")))
        assertFalse(result.has(QualityIssueType.DUPLICATE_PARAGRAPH))
    }

    // ---- paragraph order

    @Test fun swappedParagraphsAreReportedAsMajor() {
        val result = checker.check(cleanInput(listOf(translated[1], translated[0], translated[2])))
        val order = result.allIssues.filter { it.type == QualityIssueType.PARAGRAPH_ORDER_MISMATCH }
        assertEquals(listOf(0, 1), order.map { it.translationLocation!!.paragraphIndex }.sorted())
        assertTrue(order.all { it.severity == QualitySeverity.MAJOR })
        assertEquals(CheckStatus.FAILED, result.structural)
    }

    @Test fun orderIsPassedWhenParagraphsAreInPlace() {
        val result = checker.check(cleanInput())
        assertFalse(result.has(QualityIssueType.PARAGRAPH_ORDER_MISMATCH))
        assertTrue(QualityCheckType.PARAGRAPH_ORDER in result.passedChecks)
    }

    @Test fun orderCheckIsSkippedWhenThereAreNoAnchors() {
        val result = checker.check(QualityInput(listOf("He ran away.", "She stayed behind."), listOf("သူ ထွက်ပြေးသွားသည်။", "သူမ နောက်ကျန်ခဲ့သည်။")))
        assertTrue(QualityCheckType.PARAGRAPH_ORDER in result.skippedChecks)
    }

    // ---- chunks

    @Test fun completeChunksPass() {
        val result = checker.check(QualityInput.fromText(sourceText, translatedText, chunks = chunks()))
        assertFalse(result.has(QualityIssueType.MISSING_CHUNK))
        assertTrue(QualityCheckType.CHUNK_SEQUENCE in result.passedChecks)
        assertTrue(QualityCheckType.CHUNK_TRANSLATIONS in result.passedChecks)
    }

    @Test fun chunkChecksAreSkippedWithoutChunks() {
        val result = checker.check(cleanInput())
        assertTrue(QualityCheckType.CHUNK_SEQUENCE in result.skippedChecks)
        assertTrue(QualityCheckType.CHUNK_TRANSLATIONS in result.skippedChecks)
    }

    @Test fun aMissingChunkIsReported() {
        val result = checker.check(QualityInput.fromText(sourceText, translatedText, chunks = chunks(skip = setOf(1))))
        val missing = result.issueOf(QualityIssueType.MISSING_CHUNK)!!
        assertEquals(1, missing.translationLocation!!.chunkIndex)
        assertTrue(result.has(QualityIssueType.INVALID_CHUNK_SEQUENCE))
        assertEquals(CheckStatus.FAILED, result.structural)
    }

    @Test fun aDuplicateChunkIndexIsReported() {
        val withDuplicate = chunks() + chunk(1, source[1], translated[1], 1, 1, id = "k1-dup")
        val result = checker.check(QualityInput.fromText(sourceText, translatedText, chunks = withDuplicate))
        val issue = result.issueOf(QualityIssueType.DUPLICATE_CHUNK)!!
        assertEquals(1, issue.translationLocation!!.chunkIndex)
        assertEquals(CheckStatus.FAILED, result.structural)
    }

    @Test fun aDuplicateChunkIdIsReported() {
        val clash = chunks().toMutableList()
        clash[2] = chunk(2, source[2], translated[2], 2, 2, id = "k0")
        val result = checker.check(QualityInput.fromText(sourceText, translatedText, chunks = clash))
        assertTrue(result.allIssues.any { it.type == QualityIssueType.DUPLICATE_CHUNK && it.description.contains("k0") })
    }

    @Test fun chunksThatDoNotFollowOnFromEachOtherAreAnInvalidSequence() {
        val broken = listOf(
            chunk(0, source[0], translated[0], 0, 0),
            chunk(1, source[1], translated[1], 2, 2),
            chunk(2, source[2], translated[2], 1, 1),
        )
        val result = checker.check(QualityInput.fromText(sourceText, translatedText, chunks = broken))
        assertTrue(result.has(QualityIssueType.INVALID_CHUNK_SEQUENCE))
    }

    @Test fun chunksWithoutTranslationAreReportedOneByOne() {
        val result = checker.check(QualityInput.fromText(sourceText, translatedText, chunks = chunks(withTranslation = false)))
        val pending = result.allIssues.filter { it.type == QualityIssueType.CHUNK_NOT_TRANSLATED }
        assertEquals(listOf(0, 1, 2), pending.map { it.translationLocation!!.chunkIndex })
        assertTrue(pending.all { it.severity == QualitySeverity.MAJOR })
        assertEquals(CheckStatus.FAILED, result.completeness)
    }

    @Test fun inputBuiltFromChunksIsCheckedLikeAnyOther() {
        val result = checker.check(QualityInput.fromChunks(chunks(), QualityTestData.glossaryList, QualityTestData.characterList))
        assertEquals(100, result.overallScore)
        assertTrue(result.allIssues.isEmpty())
    }

    @Test fun aChunkWithoutTranslationShowsUpAsMissingParagraphToo() {
        val withGap = chunks().toMutableList()
        withGap[1] = chunk(1, source[1], null, 1, 1)
        val result = checker.check(QualityInput.fromChunks(withGap))
        assertTrue(result.has(QualityIssueType.CHUNK_NOT_TRANSLATED))
        assertTrue(result.has(QualityIssueType.MISSING_PARAGRAPHS))
    }
}
