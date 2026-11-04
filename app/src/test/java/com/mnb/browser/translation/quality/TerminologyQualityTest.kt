package com.mnb.browser.translation.quality

import com.mnb.browser.translation.memory.ConflictStatus
import com.mnb.browser.translation.memory.MemoryConflict
import com.mnb.browser.translation.memory.MemoryStatus
import com.mnb.browser.translation.memory.MemoryType
import com.mnb.browser.translation.quality.QualityTestData.character
import com.mnb.browser.translation.quality.QualityTestData.cleanInput
import com.mnb.browser.translation.quality.QualityTestData.glossary
import com.mnb.browser.translation.quality.QualityTestData.glossaryList
import com.mnb.browser.translation.quality.QualityTestData.novel
import com.mnb.browser.translation.quality.QualityTestData.translated
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminologyQualityTest {
    private val checker = TranslationQualityChecker()

    private val wrongSect = "ကောင်းကင်ဂိုဏ်း"
    private val wrongLin = "လင်ဖိုင်း"

    private fun withParagraph0(text: String) = listOf(text, translated[1], translated[2])

    private val sectWrong = translated[0].replace("ကောင်းကင်ပြာဂိုဏ်း", wrongSect)
    private val linWrong = translated[0].replace("လင်ဖုန်း", wrongLin)

    // ---- glossary

    @Test fun consistentTermsPass() {
        val result = checker.check(cleanInput())
        assertEquals(CheckStatus.PASSED, result.terminology)
        assertTrue(QualityCheckType.GLOSSARY_TERMS in result.passedChecks)
    }

    @Test fun aKnownWrongRenderingOfAConfirmedTermIsMajor() {
        val input = cleanInput(withParagraph0(sectWrong)).copy(termVariants = mapOf("azure sect" to listOf(wrongSect)))
        val result = checker.check(input)
        val issue = result.issueOf(QualityIssueType.GLOSSARY_VARIANT_USED)!!
        assertEquals(QualitySeverity.MAJOR, issue.severity)
        assertTrue(issue.description.contains(wrongSect))
        assertEquals(0, issue.translationLocation!!.paragraphIndex)
        assertEquals(CheckStatus.FAILED, result.terminology)
    }

    @Test fun aMissingConfirmedTermWithoutKnownVariantIsOnlyAWarning() {
        val result = checker.check(cleanInput(withParagraph0(sectWrong)))
        val issue = result.issueOf(QualityIssueType.GLOSSARY_TERM_MISSING)!!
        assertEquals(QualitySeverity.MINOR, issue.severity)
        assertTrue(issue in result.warnings)
        assertEquals(CheckStatus.WARNING, result.terminology)
    }

    @Test fun uncertainTermsAreNeverEnforced() {
        val uncertain = cleanInput(withParagraph0(sectWrong)).copy(
            glossary = listOf(glossary("Azure Sect", "ကောင်းကင်ပြာဂိုဏ်း", MemoryStatus.UNCERTAIN), glossaryList[1]),
        )
        val result = checker.check(uncertain)
        val issue = result.issueOf(QualityIssueType.GLOSSARY_TERM_MISSING)!!
        assertEquals(QualitySeverity.INFO, issue.severity)
        assertTrue(issue.description.contains("uncertain"))
        assertEquals(CheckStatus.PASSED, result.terminology)
        assertFalse(result.hasBlockingIssues)
    }

    @Test fun anUncertainTermWithAKnownWrongRenderingIsOnlyMinor() {
        val input = cleanInput(withParagraph0(sectWrong)).copy(
            glossary = listOf(glossary("Azure Sect", "ကောင်းကင်ပြာဂိုဏ်း", MemoryStatus.UNCERTAIN)),
            termVariants = mapOf("azure sect" to listOf(wrongSect)),
        )
        assertEquals(QualitySeverity.MINOR, checker.check(input).issueOf(QualityIssueType.GLOSSARY_VARIANT_USED)!!.severity)
    }

    @Test fun termsAreCheckedAgainstTheWholeTranslationWhenParagraphsDoNotLineUp() {
        val shorter = cleanInput(translated.take(2))
        val result = checker.check(shorter)
        val issue = result.issueOf(QualityIssueType.GLOSSARY_TERM_MISSING)!!
        assertEquals(null, issue.translationLocation)
        assertTrue(issue.description.contains("ကျောက်စိမ်းမြို့"))
    }

    @Test fun termsThatDoNotAppearInTheSourceAreNotRequired() {
        val input = cleanInput().copy(glossary = listOf(glossary("Phoenix Hall", "ဖီးနစ်ခန်းမ")))
        val result = checker.check(input)
        assertTrue(QualityCheckType.GLOSSARY_TERMS in result.passedChecks)
        assertFalse(result.has(QualityIssueType.GLOSSARY_TERM_MISSING))
    }

    @Test fun termChecksAreSkippedWithoutGlossary() {
        val result = checker.check(cleanInput().copy(glossary = emptyList(), characters = emptyList()))
        assertTrue(QualityCheckType.GLOSSARY_TERMS in result.skippedChecks)
        assertTrue(QualityCheckType.CHARACTER_NAMES in result.skippedChecks)
        assertEquals(CheckStatus.NOT_EVALUATED, result.terminology)
    }

    // ---- characters

    @Test fun aKnownWrongCharacterNameIsMajor() {
        val input = cleanInput(withParagraph0(linWrong)).copy(termVariants = mapOf("lin feng" to listOf(wrongLin)))
        val result = checker.check(input)
        val issue = result.issueOf(QualityIssueType.CHARACTER_NAME_INCONSISTENT)!!
        assertEquals(QualitySeverity.MAJOR, issue.severity)
        assertEquals(CheckStatus.FAILED, result.terminology)
    }

    @Test fun aMissingCharacterNameWithoutEvidenceIsMinor() {
        val result = checker.check(cleanInput(withParagraph0(linWrong)))
        assertEquals(QualitySeverity.MINOR, result.issueOf(QualityIssueType.CHARACTER_NAME_INCONSISTENT)!!.severity)
    }

    @Test fun aliasesInTheSourceAreCheckedAgainstTheSameName() {
        val input = QualityInput(
            sourceParagraphs = listOf("Young Master Lin bowed to the elder."),
            translatedParagraphs = listOf("အသက်ကြီးသူကို ဦးညွှတ်လိုက်သည်။"),
            characters = listOf(character("Lin Feng", "လင်ဖုန်း", aliases = listOf("Young Master Lin"))),
        )
        val issue = checker.check(input).issueOf(QualityIssueType.CHARACTER_NAME_INCONSISTENT)!!
        assertTrue(issue.description.contains("Young Master Lin") || issue.description.contains("Lin Feng"))
    }

    @Test fun uncertainCharactersAreNotEnforced() {
        val input = cleanInput(withParagraph0(linWrong)).copy(
            characters = listOf(character("Lin Feng", "လင်ဖုန်း", MemoryStatus.UNCERTAIN), character("Su Mei", "ဆူမေ")),
        )
        val result = checker.check(input)
        assertEquals(QualitySeverity.INFO, result.issueOf(QualityIssueType.CHARACTER_NAME_INCONSISTENT)!!.severity)
        assertFalse(result.hasBlockingIssues)
    }

    @Test fun charactersWithoutAMyanmarNameCannotBeChecked() {
        val input = cleanInput().copy(characters = listOf(character("Lin Feng", null)))
        assertTrue(QualityCheckType.CHARACTER_NAMES in checker.check(input).skippedChecks)
    }

    @Test fun aGlossaryEntryForACharacterIsNotCheckedTwice() {
        val input = cleanInput(withParagraph0(linWrong)).copy(glossary = listOf(glossary("Lin Feng", "လင်ဖုန်း")))
        val result = checker.check(input)
        assertEquals(1, result.allIssues.count { it.type == QualityIssueType.CHARACTER_NAME_INCONSISTENT })
        assertFalse(result.has(QualityIssueType.GLOSSARY_TERM_MISSING))
    }

    // ---- variants from memory conflicts

    @Test fun variantsComeFromRejectedOpenAndReplacedValues() {
        fun conflict(id: Long, type: MemoryType, memoryId: String, field: String, status: ConflictStatus) = MemoryConflict(
            id, novel, type, memoryId, field, existingValue = "old-$id", proposedValue = "new-$id",
            proposedSourceChapter = 3, status = status,
        )
        val conflicts = listOf(
            conflict(1, MemoryType.GLOSSARY, "g-Azure Sect", "translatedTerm", ConflictStatus.REJECTED),
            conflict(2, MemoryType.GLOSSARY, "g-Jade City", "translatedTerm", ConflictStatus.ACCEPTED),
            conflict(3, MemoryType.CHARACTER, "c-Lin Feng", "myanmarName", ConflictStatus.OPEN),
            conflict(4, MemoryType.CHARACTER, "c-Lin Feng", "notes", ConflictStatus.OPEN),
            conflict(5, MemoryType.WORLD, "w", "description", ConflictStatus.OPEN),
        )
        val variants = QualityVariants.fromConflicts(glossaryList, QualityTestData.characterList, conflicts)
        assertEquals(listOf("new-1"), variants["azure sect"])
        assertEquals(listOf("old-2"), variants["jade city"])
        assertEquals(listOf("new-3"), variants["lin feng"])
        assertEquals(3, variants.size)
    }
}
