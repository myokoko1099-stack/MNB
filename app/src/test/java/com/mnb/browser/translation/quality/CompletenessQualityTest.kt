package com.mnb.browser.translation.quality

import com.mnb.browser.translation.SourceLanguage
import com.mnb.browser.translation.TargetLanguage
import com.mnb.browser.translation.TranslationError
import com.mnb.browser.translation.TranslationResult
import com.mnb.browser.translation.quality.QualityTestData.cleanInput
import com.mnb.browser.translation.quality.QualityTestData.source
import com.mnb.browser.translation.quality.QualityTestData.sourceText
import com.mnb.browser.translation.quality.QualityTestData.translated
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletenessQualityTest {
    private val checker = TranslationQualityChecker()

    @Test fun emptyTranslationIsCritical() {
        val result = checker.check(QualityInput.fromText(sourceText, ""))
        val issue = result.issueOf(QualityIssueType.EMPTY_TRANSLATION)!!
        assertEquals(QualitySeverity.CRITICAL, issue.severity)
        assertEquals(CheckStatus.FAILED, result.completeness)
        assertTrue(result.overallScore <= QualityScoring.CRITICAL_SCORE_CAP)
        assertTrue(result.hasBlockingIssues)
    }

    @Test fun nullAndBlankTranslationsAreBothEmpty() {
        assertTrue(checker.check(QualityInput.fromText(sourceText, null)).has(QualityIssueType.EMPTY_TRANSLATION))
        assertTrue(checker.check(QualityInput.fromText(sourceText, "  \n \n")).has(QualityIssueType.EMPTY_TRANSLATION))
    }

    @Test fun anEmptyTranslationSkipsTheParagraphChecksInsteadOfPassingThem() {
        val result = checker.check(QualityInput.fromText(sourceText, ""))
        assertTrue(QualityCheckType.PARAGRAPH_COUNT in result.skippedChecks)
        assertTrue(QualityCheckType.GLOSSARY_TERMS in result.skippedChecks)
        assertFalse(QualityCheckType.PARAGRAPH_COUNT in result.passedChecks)
        assertEquals(CheckStatus.NOT_EVALUATED, result.terminology)
        assertEquals(CheckStatus.NOT_EVALUATED, result.structural)
    }

    @Test fun missingParagraphIsMajorAndPointsAtTheFirstUnmatchedSourceParagraph() {
        val result = checker.check(cleanInput(translated.take(2)))
        val issue = result.issueOf(QualityIssueType.MISSING_PARAGRAPHS)!!
        assertEquals(QualitySeverity.MAJOR, issue.severity)
        assertEquals(2, issue.sourceLocation!!.paragraphIndex)
        assertEquals(1, issue.translationLocation!!.paragraphIndex)
        assertEquals(CheckStatus.FAILED, result.completeness)
        assertTrue(issue in result.issues)
    }

    @Test fun losingMoreThanHalfTheParagraphsIsCritical() {
        val result = checker.check(cleanInput(translated.take(1)))
        assertEquals(QualitySeverity.CRITICAL, result.issueOf(QualityIssueType.MISSING_PARAGRAPHS)!!.severity)
        assertTrue(result.overallScore <= QualityScoring.CRITICAL_SCORE_CAP)
    }

    @Test fun extraParagraphsAreOnlyAWarning() {
        val result = checker.check(cleanInput(translated + "အပိုစာပိုဒ်တစ်ခု ထည့်ထားသည်။"))
        val issue = result.issueOf(QualityIssueType.EXTRA_PARAGRAPHS)!!
        assertEquals(QualitySeverity.MINOR, issue.severity)
        assertTrue(issue in result.warnings)
        assertEquals(CheckStatus.WARNING, result.completeness)
        assertFalse(result.hasBlockingIssues)
    }

    @Test fun anEmptyTranslatedParagraphIsReportedWithItsPosition() {
        val result = checker.check(cleanInput(listOf(translated[0], "", translated[2])))
        val issue = result.issueOf(QualityIssueType.EMPTY_PARAGRAPH)!!
        assertEquals(QualitySeverity.MAJOR, issue.severity)
        assertEquals(1, issue.translationLocation!!.paragraphIndex)
        assertEquals(1, issue.sourceLocation!!.paragraphIndex)
    }

    @Test fun blankLinesInTextAreLayoutNotEmptyParagraphs() {
        val result = checker.check(QualityInput.fromText(sourceText, translated.joinToString("\n\n")))
        assertFalse(result.has(QualityIssueType.EMPTY_PARAGRAPH))
        assertFalse(result.has(QualityIssueType.MISSING_PARAGRAPHS))
    }

    @Test fun aVeryShortTranslationSuggestsMissingContent() {
        val longSource = "The old master of the Azure Sect spent thirty years studying the sword before he finally taught his first disciple."
        val result = checker.check(QualityInput(listOf(longSource), listOf("ဆရာကြီး")))
        val types = result.allIssues.filter { it.type == QualityIssueType.POSSIBLE_MISSING_CONTENT }
        assertEquals(2, types.size)
        assertTrue(types.any { it.severity == QualitySeverity.MAJOR && it.sourceLocation == null })
        assertTrue(types.any { it.severity == QualitySeverity.MINOR && it.sourceLocation != null })
    }

    @Test fun anUntranslatedParagraphIsDetected() {
        val damaged = listOf(source[0], translated[1], translated[2])
        val result = checker.check(cleanInput(damaged))
        val issue = result.issueOf(QualityIssueType.UNTRANSLATED_PARAGRAPH)!!
        assertEquals(QualitySeverity.MAJOR, issue.severity)
        assertEquals(0, issue.translationLocation!!.paragraphIndex)
        assertFalse(result.has(QualityIssueType.NO_MYANMAR_TEXT))
    }

    @Test fun aFailedProviderResultIsInvalid() {
        val failure = TranslationResult.Failure(TranslationError.PROVIDER_FAILURE, "p1", "boom")
        val result = checker.check(QualityInput.fromResult(sourceText, failure))
        val issue = result.issueOf(QualityIssueType.INVALID_TRANSLATION_RESULT)!!
        assertEquals(QualitySeverity.CRITICAL, issue.severity)
        assertTrue(issue.description.contains("boom"))
        assertTrue(result.has(QualityIssueType.EMPTY_TRANSLATION))
        assertTrue(QualityCheckType.TRANSLATION_RESULT !in result.skippedChecks)
    }

    @Test fun aSuccessResultWithBlankTextIsInvalid() {
        val blank = TranslationResult.Success("  ", "p1", false, SourceLanguage.CHINESE, TargetLanguage.values().first())
        val result = checker.check(QualityInput.fromResult(sourceText, blank))
        assertEquals(QualitySeverity.CRITICAL, result.issueOf(QualityIssueType.INVALID_TRANSLATION_RESULT)!!.severity)
    }

    @Test fun mockOutputIsFlaggedEvenWhenTheTextLooksFine() {
        val mock = TranslationResult.Success(
            QualityTestData.translatedText, "mock", true, SourceLanguage.CHINESE, TargetLanguage.values().first(),
        )
        val result = checker.check(QualityInput.fromResult(sourceText, mock))
        assertEquals(QualitySeverity.MAJOR, result.issueOf(QualityIssueType.INVALID_TRANSLATION_RESULT)!!.severity)
        assertEquals(CheckStatus.FAILED, result.completeness)
    }

    @Test fun aGoodRealResultHasNoResultIssue() {
        val ok = TranslationResult.Success(
            QualityTestData.translatedText, "real", false, SourceLanguage.CHINESE, TargetLanguage.values().first(),
        )
        val result = checker.check(QualityInput.fromResult(sourceText, ok))
        assertNull(result.issueOf(QualityIssueType.INVALID_TRANSLATION_RESULT))
        assertTrue(QualityCheckType.TRANSLATION_RESULT in result.passedChecks)
    }

    @Test fun blankSourceIsRejected() {
        try {
            QualityInput.fromText("  ", "x")
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertNotNull(expected.message)
        }
    }
}
