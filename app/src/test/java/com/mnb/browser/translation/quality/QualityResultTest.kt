package com.mnb.browser.translation.quality

import com.mnb.browser.translation.quality.QualityScoring.CRITICAL_SCORE_CAP
import com.mnb.browser.translation.quality.QualityScoring.MAX_DEDUCTION_PER_TYPE
import com.mnb.browser.translation.quality.QualityTestData.cleanInput
import com.mnb.browser.translation.quality.QualityTestData.issue
import com.mnb.browser.translation.quality.QualityTestData.source
import com.mnb.browser.translation.quality.QualityTestData.translated
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityResultTest {
    private val checker = TranslationQualityChecker()

    // ---- perfect and clean

    @Test fun aPerfectTranslationStructureScoresFullMarks() {
        val result = checker.check(cleanInput())
        assertEquals(100, result.overallScore)
        assertEquals(CheckStatus.PASSED, result.completeness)
        assertEquals(CheckStatus.PASSED, result.structural)
        assertEquals(CheckStatus.PASSED, result.terminology)
        assertEquals(SemanticStatus.NOT_EVALUATED, result.semantic)
        assertTrue(
            result.passedChecks.containsAll(
                listOf(
                    QualityCheckType.EMPTY_TRANSLATION, QualityCheckType.PARAGRAPH_COUNT, QualityCheckType.EMPTY_PARAGRAPHS,
                    QualityCheckType.CONTENT_LENGTH, QualityCheckType.UNTRANSLATED_TEXT, QualityCheckType.DUPLICATE_PARAGRAPHS,
                    QualityCheckType.PARAGRAPH_ORDER, QualityCheckType.GLOSSARY_TERMS, QualityCheckType.CHARACTER_NAMES,
                    QualityCheckType.NUMBERS, QualityCheckType.TARGET_SCRIPT, QualityCheckType.TEXT_ARTIFACTS,
                ),
            ),
        )
        assertTrue(
            result.skippedChecks.containsAll(
                listOf(
                    QualityCheckType.TRANSLATION_RESULT, QualityCheckType.CHUNK_SEQUENCE,
                    QualityCheckType.CHUNK_TRANSLATIONS, QualityCheckType.EXTERNAL_EVALUATION,
                ),
            ),
        )
    }

    @Test fun noIssuesMeansEmptyIssueAndWarningLists() {
        val result = checker.check(cleanInput())
        assertTrue(result.issues.isEmpty())
        assertTrue(result.warnings.isEmpty())
        assertTrue(result.allIssues.isEmpty())
        assertFalse(result.hasBlockingIssues)
    }

    @Test fun everyCheckIsEitherPassedSkippedOrHasIssues() {
        val result = checker.check(cleanInput(translated.take(2)))
        val withIssues = result.allIssues.map { it.type.check }.toSet()
        for (type in QualityCheckType.values()) {
            val states = listOf(type in result.passedChecks, type in result.skippedChecks, type in withIssues)
            assertEquals("check $type", 1, states.count { it })
        }
    }

    // ---- multiple issues

    @Test fun severalProblemsAreAllReportedTogether() {
        val damaged = listOf(source[0], "", translated[1] + "\uFFFD", "နောက်ဆုံးစာပိုဒ်တစ်ခု ထပ်ထည့်ထားသည်။")
        val result = checker.check(cleanInput(damaged))
        val types = result.allIssues.map { it.type }.toSet()
        assertTrue(QualityIssueType.UNTRANSLATED_PARAGRAPH in types)
        assertTrue(QualityIssueType.EMPTY_PARAGRAPH in types)
        assertTrue(QualityIssueType.TEXT_ARTIFACT in types)
        assertTrue(QualityIssueType.EXTRA_PARAGRAPHS in types)
        assertEquals(CheckStatus.FAILED, result.completeness)
        assertTrue(result.overallScore < 60)
        assertEquals(result.allIssues.size, result.issues.size + result.warnings.size)
        assertTrue(result.issues.all { it.severity.isBlocking })
        assertTrue(result.warnings.none { it.severity.isBlocking })
    }

    @Test fun everyIssueCarriesAllReportFields() {
        val result = checker.check(cleanInput(listOf(translated[1], translated[0], translated[2])))
        assertTrue(result.allIssues.isNotEmpty())
        for (issue in result.allIssues) {
            assertTrue(issue.description.isNotBlank())
            assertTrue(issue.suggestedAction.isNotBlank())
            assertTrue(issue.sourceLocation != null || issue.translationLocation != null || issue.type == QualityIssueType.POSSIBLE_MISSING_CONTENT)
        }
    }

    // ---- scoring

    @Test fun scoreSubtractsSeverityWeights() {
        assertEquals(100, QualityScoring.score(emptyList()))
        assertEquals(85, QualityScoring.score(listOf(issue(QualityIssueType.EMPTY_PARAGRAPH, QualitySeverity.MAJOR))))
        assertEquals(95, QualityScoring.score(listOf(issue(QualityIssueType.EXTRA_PARAGRAPHS, QualitySeverity.MINOR))))
        assertEquals(100, QualityScoring.score(listOf(issue(QualityIssueType.NUMBER_MISMATCH, QualitySeverity.INFO))))
        assertEquals(
            80,
            QualityScoring.score(
                listOf(
                    issue(QualityIssueType.EMPTY_PARAGRAPH, QualitySeverity.MAJOR),
                    issue(QualityIssueType.EXTRA_PARAGRAPHS, QualitySeverity.MINOR),
                ),
            ),
        )
    }

    @Test fun oneRepeatedIssueTypeIsCapped() {
        val many = List(10) { issue(QualityIssueType.EMPTY_PARAGRAPH, QualitySeverity.MAJOR) }
        assertEquals(100 - MAX_DEDUCTION_PER_TYPE, QualityScoring.score(many))
    }

    @Test fun differentTypesAddUpAndTheScoreNeverGoesBelowZero() {
        val issues = listOf(
            QualityIssueType.EMPTY_PARAGRAPH, QualityIssueType.DUPLICATE_PARAGRAPH, QualityIssueType.MISSING_CHUNK,
            QualityIssueType.DUPLICATE_CHUNK, QualityIssueType.UNTRANSLATED_PARAGRAPH, QualityIssueType.TEXT_ARTIFACT,
            QualityIssueType.PARAGRAPH_ORDER_MISMATCH,
        ).map { issue(it, QualitySeverity.MAJOR) }
        assertEquals(0, QualityScoring.score(issues))
    }

    @Test fun aCriticalIssueCapsTheScore() {
        val critical = issue(QualityIssueType.EMPTY_TRANSLATION, QualitySeverity.CRITICAL)
        assertEquals(CRITICAL_SCORE_CAP, QualityScoring.score(listOf(critical)))
        val withMinor = listOf(critical, issue(QualityIssueType.EXTRA_PARAGRAPHS, QualitySeverity.MINOR))
        assertEquals(CRITICAL_SCORE_CAP, QualityScoring.score(withMinor))
    }

    // ---- severity classification and statuses

    @Test fun severityDecidesWhetherAnIssueBlocks() {
        assertTrue(QualitySeverity.CRITICAL.isBlocking)
        assertTrue(QualitySeverity.MAJOR.isBlocking)
        assertFalse(QualitySeverity.MINOR.isBlocking)
        assertFalse(QualitySeverity.INFO.isBlocking)
        assertTrue(QualitySeverity.CRITICAL.weight > QualitySeverity.MAJOR.weight)
        assertTrue(QualitySeverity.MAJOR.weight > QualitySeverity.MINOR.weight)
        assertTrue(QualitySeverity.MINOR.weight > QualitySeverity.INFO.weight)
    }

    @Test fun categoryStatusFollowsTheWorstIssue() {
        val ran = setOf(QualityCheckType.PARAGRAPH_COUNT)
        val cat = QualityCategory.COMPLETENESS
        assertEquals(CheckStatus.PASSED, QualityScoring.status(cat, ran, emptyList()))
        assertEquals(CheckStatus.PASSED, QualityScoring.status(cat, ran, listOf(issue(QualityIssueType.NUMBER_MISMATCH, QualitySeverity.INFO))))
        assertEquals(CheckStatus.WARNING, QualityScoring.status(cat, ran, listOf(issue(QualityIssueType.EXTRA_PARAGRAPHS, QualitySeverity.MINOR))))
        assertEquals(CheckStatus.FAILED, QualityScoring.status(cat, ran, listOf(issue(QualityIssueType.MISSING_PARAGRAPHS, QualitySeverity.MAJOR))))
        assertEquals(CheckStatus.NOT_EVALUATED, QualityScoring.status(QualityCategory.STRUCTURE, ran, emptyList()))
    }

    @Test fun issuesOfOtherCategoriesDoNotChangeAStatus() {
        val ran = setOf(QualityCheckType.GLOSSARY_TERMS, QualityCheckType.PARAGRAPH_COUNT)
        val onlyCompleteness = listOf(issue(QualityIssueType.MISSING_PARAGRAPHS, QualitySeverity.MAJOR))
        assertEquals(CheckStatus.PASSED, QualityScoring.status(QualityCategory.TERMINOLOGY, ran, onlyCompleteness))
    }

    // ---- safety of the input

    @Test fun checkingNeverChangesTheInput() {
        val sourceBefore = source.toList()
        val translatedBefore = translated.toList()
        val input = cleanInput(translated.take(2))
        checker.check(input)
        assertEquals(sourceBefore, input.sourceParagraphs)
        assertEquals(translated.take(2), input.translatedParagraphs)
        assertEquals(translatedBefore, translated)
    }

    @Test fun theSameInputAlwaysGivesTheSameResult() {
        val input = cleanInput(listOf(translated[1], translated[0], translated[2]))
        assertEquals(checker.check(input), checker.check(input))
    }

    @Test fun invalidConfigurationIsRejected() {
        try {
            QualityConfig(minChapterLengthRatio = 1.5)
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("minChapterLengthRatio"))
        }
    }
}
