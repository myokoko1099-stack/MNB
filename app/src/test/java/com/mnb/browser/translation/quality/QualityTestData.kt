package com.mnb.browser.translation.quality

import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.ChunkId
import com.mnb.browser.translation.NovelId
import com.mnb.browser.translation.TranslationChunk
import com.mnb.browser.translation.TranslationStatus
import com.mnb.browser.translation.chunking.ChapterChunk
import com.mnb.browser.translation.memory.CharacterMemory
import com.mnb.browser.translation.memory.GlossaryCategory
import com.mnb.browser.translation.memory.GlossaryMemory
import com.mnb.browser.translation.memory.MemoryStatus

/** A small chapter that translates cleanly, and helpers to damage it in specific ways. */
object QualityTestData {
    val novel = NovelId("n1")

    val source = listOf(
        "Lin Feng arrived at the Azure Sect gate with 12 disciples.",
        "Su Mei was waiting for him under the old pine tree.",
        "They walked together into Jade City as the sun set.",
    )

    val translated = listOf(
        "လင်ဖုန်းသည် တပည့် ၁၂ ဦးနှင့်အတူ ကောင်းကင်ပြာဂိုဏ်း တံခါးဝသို့ ရောက်ရှိလာခဲ့သည်။",
        "ဆူမေသည် အိုမင်းသော ထင်းရှူးပင်အောက်တွင် သူ့ကို စောင့်နေခဲ့သည်။",
        "နေဝင်ချိန်တွင် သူတို့နှစ်ဦး ကျောက်စိမ်းမြို့ထဲသို့ အတူတူ လျှောက်လာခဲ့ကြသည်။",
    )

    val sourceText: String get() = source.joinToString("\n")
    val translatedText: String get() = translated.joinToString("\n")

    fun glossary(
        sourceTerm: String,
        translatedTerm: String,
        status: MemoryStatus = MemoryStatus.CONFIRMED,
    ) = GlossaryMemory(
        id = "g-$sourceTerm",
        novelId = novel,
        sourceTerm = sourceTerm,
        translatedTerm = translatedTerm,
        category = GlossaryCategory.SPECIAL_TERM,
        status = status,
        sourceChapter = 1,
    )

    fun character(
        name: String,
        myanmarName: String?,
        status: MemoryStatus = MemoryStatus.CONFIRMED,
        aliases: List<String> = emptyList(),
    ) = CharacterMemory(
        id = "c-$name",
        novelId = novel,
        originalName = name,
        myanmarName = myanmarName,
        aliases = aliases,
        gender = null,
        personality = "",
        role = "",
        speakingStyle = "",
        pronouns = "",
        firstAppearanceChapter = 1,
        notes = "",
        status = status,
        sourceChapter = 1,
    )

    val glossaryList = listOf(
        glossary("Azure Sect", "ကောင်းကင်ပြာဂိုဏ်း"),
        glossary("Jade City", "ကျောက်စိမ်းမြို့"),
    )

    val characterList = listOf(
        character("Lin Feng", "လင်ဖုန်း"),
        character("Su Mei", "ဆူမေ"),
    )

    /** The clean chapter with glossary and characters attached. */
    fun cleanInput(translatedParagraphs: List<String> = translated): QualityInput = QualityInput(
        sourceParagraphs = source,
        translatedParagraphs = translatedParagraphs,
        glossary = glossaryList,
        characters = characterList,
    )

    fun chunk(
        index: Int,
        text: String,
        translatedText: String?,
        startParagraph: Int,
        endParagraph: Int,
        id: String = "k$index",
    ): ChapterChunk = ChapterChunk(
        TranslationChunk(
            chunkId = ChunkId(id),
            chapterId = ChapterId("c1"),
            chunkIndex = index,
            originalText = text,
            translatedText = translatedText,
            status = if (translatedText.isNullOrBlank()) TranslationStatus.PENDING else TranslationStatus.COMPLETED,
        ),
        startParagraph,
        endParagraph,
    )

    /** One chunk per paragraph. */
    fun chunks(skip: Set<Int> = emptySet(), withTranslation: Boolean = true): List<ChapterChunk> =
        source.indices.filter { it !in skip }.map { i ->
            chunk(i, source[i], if (withTranslation) translated[i] else null, i, i)
        }

    fun issue(type: QualityIssueType, severity: QualitySeverity) = QualityIssue(
        type = type,
        severity = severity,
        description = "test",
        sourceLocation = null,
        translationLocation = null,
        suggestedAction = "test",
    )
}

fun QualityResult.issueOf(type: QualityIssueType): QualityIssue? = allIssues.firstOrNull { it.type == type }

fun QualityResult.has(type: QualityIssueType): Boolean = allIssues.any { it.type == type }
