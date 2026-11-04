package com.mnb.browser.translation.quality

import com.mnb.browser.translation.quality.QualityTestData.cleanInput
import com.mnb.browser.translation.quality.QualityTestData.translated
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextQualityTest {
    private val checker = TranslationQualityChecker()

    private fun withParagraph0(text: String) = cleanInput(listOf(text, translated[1], translated[2]))

    // ---- numbers

    @Test fun numbersWrittenInMyanmarDigitsMatchTheSource() {
        assertTrue(QualityCheckType.NUMBERS in checker.check(cleanInput()).passedChecks)
    }

    @Test fun aMissingNumberIsOnlyAnInfoNote() {
        val result = checker.check(withParagraph0("လင်ဖုန်းသည် တပည့်များနှင့်အတူ ကောင်းကင်ပြာဂိုဏ်း တံခါးဝသို့ ရောက်ရှိလာခဲ့သည်။"))
        val issue = result.issueOf(QualityIssueType.NUMBER_MISMATCH)!!
        assertEquals(QualitySeverity.INFO, issue.severity)
        assertTrue(issue in result.warnings)
        assertTrue(issue.description.contains("12"))
    }

    @Test fun numbersAreNotComparedWhenParagraphsDoNotLineUp() {
        val result = checker.check(cleanInput(translated.take(2)))
        assertTrue(QualityCheckType.NUMBERS in result.skippedChecks)
    }

    // ---- script

    @Test fun latinTextWithoutMyanmarIsFlagged() {
        val result = checker.check(withParagraph0("Lin Feng arrived with the disciples at the gate."))
        val issue = result.issueOf(QualityIssueType.NO_MYANMAR_TEXT)!!
        assertEquals(QualitySeverity.MINOR, issue.severity)
        assertEquals(0, issue.translationLocation!!.paragraphIndex)
    }

    @Test fun leftoverSourceLanguageTextIsFlagged() {
        val result = checker.check(withParagraph0("林风带着十二名弟子来到了蓝天宗的山门前。"))
        assertEquals(QualitySeverity.MINOR, result.issueOf(QualityIssueType.SOURCE_SCRIPT_LEFTOVER)!!.severity)
    }

    @Test fun aShortLatinNameInsideMyanmarTextIsFine() {
        val result = checker.check(withParagraph0("လင်ဖုန်းသည် Zhao နှင့် တပည့် ၁၂ ဦး ကောင်းကင်ပြာဂိုဏ်း တံခါးဝသို့ ရောက်ရှိလာခဲ့သည်။"))
        assertFalse(result.has(QualityIssueType.NO_MYANMAR_TEXT))
        assertFalse(result.has(QualityIssueType.SOURCE_SCRIPT_LEFTOVER))
    }

    @Test fun scriptCheckIsSkippedWhenTheTargetIsNotMyanmar() {
        val lenient = TranslationQualityChecker(QualityConfig(expectMyanmarScript = false))
        val result = lenient.check(withParagraph0("Lin Feng arrived with the disciples at the gate."))
        assertTrue(QualityCheckType.TARGET_SCRIPT in result.skippedChecks)
        assertFalse(result.has(QualityIssueType.NO_MYANMAR_TEXT))
    }

    // ---- artifacts

    @Test fun brokenCharactersAreMajor() {
        val result = checker.check(withParagraph0(translated[0] + "\uFFFD"))
        assertEquals(QualitySeverity.MAJOR, result.issueOf(QualityIssueType.TEXT_ARTIFACT)!!.severity)
    }

    @Test fun codeFencesAndControlCharactersAreMinor() {
        val fenced = checker.check(withParagraph0("```" + translated[0]))
        assertEquals(QualitySeverity.MINOR, fenced.issueOf(QualityIssueType.TEXT_ARTIFACT)!!.severity)
        val control = checker.check(withParagraph0(translated[0] + "\u0007"))
        assertEquals(QualitySeverity.MINOR, control.issueOf(QualityIssueType.TEXT_ARTIFACT)!!.severity)
    }

    @Test fun aDoubledSentenceMarkIsOnlyInfo() {
        val result = checker.check(withParagraph0(translated[0] + "။"))
        assertEquals(QualitySeverity.INFO, result.issueOf(QualityIssueType.TEXT_ARTIFACT)!!.severity)
    }

    @Test fun grammarAndWordChoiceAreNeverJudged() {
        // Odd but well formed Myanmar: nothing deterministic to flag.
        val result = checker.check(withParagraph0("လင်ဖုန်း တပည့် ၁၂ ဦး ကောင်းကင်ပြာဂိုဏ်း တံခါးဝ ရောက်။"))
        assertTrue(result.allIssues.none { it.category == QualityCategory.NATURALNESS })
    }

    // ---- meaning is not judged

    @Test fun semanticAccuracyStaysUnknownEvenForAPerfectScore() {
        val result = checker.check(cleanInput())
        assertEquals(100, result.overallScore)
        assertEquals(SemanticStatus.NOT_EVALUATED, result.semantic)
        assertTrue(QualityCheckType.EXTERNAL_EVALUATION in result.skippedChecks)
    }

    @Test fun aPluggedInEvaluatorMakesTheSemanticResultEvaluated() {
        val evaluator = object : QualityEvaluator {
            override val id = "fake"
            override fun evaluate(input: QualityInput) = listOf(
                QualityIssue(
                    QualityIssueType.POSSIBLE_MISSING_CONTENT,
                    QualitySeverity.MINOR,
                    "The evaluator thinks a sentence is missing",
                    null,
                    null,
                    "Read the paragraph",
                ),
            )
        }
        val result = TranslationQualityChecker(evaluators = listOf(evaluator)).check(cleanInput())
        assertEquals(SemanticStatus.EVALUATED, result.semantic)
        assertTrue(result.has(QualityIssueType.POSSIBLE_MISSING_CONTENT))
        assertTrue(QualityCheckType.EXTERNAL_EVALUATION !in result.skippedChecks)
    }

    @Test fun aFailingEvaluatorIsReportedAndDoesNotCountAsEvaluation() {
        val broken = object : QualityEvaluator {
            override val id = "broken"
            override fun evaluate(input: QualityInput): List<QualityIssue> = throw IllegalStateException("model offline")
        }
        val result = TranslationQualityChecker(evaluators = listOf(broken)).check(cleanInput())
        assertEquals(SemanticStatus.NOT_EVALUATED, result.semantic)
        val issue = result.issueOf(QualityIssueType.EVALUATOR_FAILED)!!
        assertEquals(QualitySeverity.INFO, issue.severity)
        assertTrue(issue.description.contains("model offline"))
        assertFalse(result.hasBlockingIssues)
    }
}
