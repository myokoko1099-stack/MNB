package com.mnb.browser.translation.usecase

import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.MockTranslationProvider
import com.mnb.browser.translation.NovelId
import com.mnb.browser.translation.SourceLanguage
import com.mnb.browser.translation.TranslationError
import com.mnb.browser.translation.TranslationProvider
import com.mnb.browser.translation.TranslationRequest
import com.mnb.browser.translation.TranslationResult
import com.mnb.browser.translation.TranslationStatus
import com.mnb.browser.translation.chunking.ChapterChunkRepository
import com.mnb.browser.translation.chunking.ChunkSaveResult
import com.mnb.browser.translation.chunking.ChunkingConfig
import com.mnb.browser.translation.chunking.ChunkingEngine
import com.mnb.browser.translation.chunking.ChunkingOutcome
import com.mnb.browser.translation.chunking.ParagraphSplitter
import com.mnb.browser.translation.db.ChapterRepository
import com.mnb.browser.translation.db.DbTestBase
import com.mnb.browser.translation.db.NovelRepository
import com.mnb.browser.translation.db.TranslationRepository
import com.mnb.browser.translation.memory.CharacterDraft
import com.mnb.browser.translation.memory.GlossaryCategory
import com.mnb.browser.translation.memory.MemoryRepository
import com.mnb.browser.translation.memory.MemoryStatus
import com.mnb.browser.translation.quality.QualityCheckType
import com.mnb.browser.translation.quality.QualityEvaluator
import com.mnb.browser.translation.quality.QualityInput
import com.mnb.browser.translation.quality.QualityIssue
import com.mnb.browser.translation.quality.QualityIssueType
import com.mnb.browser.translation.quality.QualityResult
import com.mnb.browser.translation.quality.QualitySeverity
import com.mnb.browser.translation.quality.SemanticStatus
import com.mnb.browser.translation.quality.TranslationQualityChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun okResult(req: TranslationRequest, providerId: String) = TranslationResult.Success(
    translatedText = "T:" + req.text,
    providerId = providerId,
    isMock = false,
    sourceLanguage = req.sourceLanguage,
    targetLanguage = req.targetLanguage,
)

private class ScriptedProvider(
    override val id: String = "scripted",
    override val supportedSourceLanguages: Set<SourceLanguage> = SourceLanguage.values().toSet(),
    private val behaviour: (TranslationRequest) -> TranslationResult = { okResult(it, "scripted") },
) : TranslationProvider {
    override val displayName = "Scripted"
    override val isMock = false
    val requests = mutableListOf<TranslationRequest>()

    override fun translate(request: TranslationRequest): TranslationResult {
        requests += request
        return behaviour(request)
    }
}

class InstantTranslationPipelineTest : DbTestBase() {
    private val n1 = NovelId("n1")
    private val c1 = ChapterId("c1")
    private val engine = ChunkingEngine(ChunkingConfig(maxChunkChars = 60))

    // Each paragraph fits one chunk (max 60) and two never fit together.
    private val paragraphs = listOf(
        "Arden reached Zephyr at dawn today.",
        "Nothing else happens in this paragraph.",
        "The last paragraph closes the chapter.",
    )
    private val text = paragraphs.joinToString(ParagraphSplitter.SEPARATOR)

    @Before fun seedNovel() {
        NovelRepository(db).save(novelInfo("n1"))
    }

    private fun request(body: String = text, lang: String? = "en", novel: NovelId = n1) =
        TranslateChapterRequest(c1, novel, 3, body, lang)

    private fun pipeline(
        provider: TranslationProvider = ScriptedProvider(),
        memory: MemoryRepository = MemoryRepository(db),
        checkQuality: (QualityInput) -> QualityResult = TranslationQualityChecker()::check,
        chunker: ((ChapterId) -> ChunkingOutcome)? = null,
    ): InstantTranslationPipeline {
        val chunkRepo = ChapterChunkRepository(db, engine)
        return InstantTranslationPipeline(
            provider = provider,
            chapters = ChapterRepository(db),
            chunkRepository = chunkRepo,
            translations = TranslationRepository(db),
            memory = memory,
            checkQuality = checkQuality,
            chunker = chunker ?: chunkRepo::chunkChapter,
        )
    }

    private fun InstantTranslationOutcome.completed() = this as InstantTranslationOutcome.Completed
    private fun InstantTranslationOutcome.failed() = this as InstantTranslationOutcome.Failed
    private fun storedStatus() = TranslationRepository(db).getTranslation(c1)?.status

    // 1. Full chapter translation
    @Test fun fullChapterIsTranslatedWithMockProviderAndStoredAsCompleted() {
        val done = pipeline(MockTranslationProvider()).translate(request(), 10L).completed()
        val expected = paragraphs.joinToString(ParagraphSplitter.SEPARATOR) { MockTranslationProvider.MOCK_PREFIX + it }
        assertEquals(expected, done.translation.translatedText)
        assertEquals(TranslationStatus.COMPLETED, done.translation.status)
        assertTrue(done.translation.metadata.isMock)
        assertEquals(1, done.translation.metadata.translationVersion)
        assertNotNull(done.quality)
    }

    // 2. Multiple chunks
    @Test fun longChapterIsSplitIntoSeveralChunksAndEachIsStored() {
        val provider = ScriptedProvider()
        val done = pipeline(provider).translate(request(), 1L).completed()
        assertEquals(3, done.chunkCount)
        assertEquals(3, provider.requests.size)
        assertTrue(provider.requests.all { it.text.length <= 60 })
        val stored = TranslationRepository(db).getChunks(c1)
        assertEquals(3, stored.size)
        assertTrue(stored.all { it.status == TranslationStatus.COMPLETED })
    }

    // 3. Chunk order
    @Test fun chunksAreSentAndReassembledInOrder() {
        val provider = ScriptedProvider()
        val done = pipeline(provider).translate(request(), 1L).completed()
        assertEquals(paragraphs, provider.requests.map { it.text })
        assertEquals(
            paragraphs.joinToString(ParagraphSplitter.SEPARATOR) { "T:$it" },
            done.translation.translatedText,
        )
    }

    // 4. Context retrieval
    @Test fun eachChunkGetsOnlyTheMemoryItMentionsAndCarriesTheNovelId() {
        val memory = MemoryRepository(db)
        memory.saveCharacter(n1, CharacterDraft("Arden", myanmarName = "အာဒင်", status = MemoryStatus.CONFIRMED))
        memory.saveGlossary(n1, "Zephyr", "ဇက်ဖာ", GlossaryCategory.PLACE_NAME, MemoryStatus.CONFIRMED)
        val provider = ScriptedProvider()
        pipeline(provider, memory).translate(request(), 1L).completed()

        val first = provider.requests[0].context!!
        assertEquals(n1, first.novelId)
        assertEquals(listOf("Arden"), first.characters.map { it.name })
        assertEquals(listOf("Zephyr"), first.glossary.map { it.sourceTerm })
        val second = provider.requests[1].context!!
        assertTrue(second.characters.isEmpty())
        assertTrue(second.glossary.isEmpty())
    }

    @Test fun memoryOfAnotherNovelIsNeverUsed() {
        NovelRepository(db).save(novelInfo("n2"))
        val memory = MemoryRepository(db)
        memory.saveCharacter(NovelId("n2"), CharacterDraft("Mirelle", myanmarName = "မီရဲလ်", status = MemoryStatus.CONFIRMED))
        val provider = ScriptedProvider()
        pipeline(provider, memory).translate(request(body = "Mirelle walked in.\n\nNothing else here."), 1L).completed()
        assertTrue(provider.requests.all { it.context!!.characters.isEmpty() })
    }

    @Test fun uncertainMemoryIsNotPassedAsSettled() {
        val memory = MemoryRepository(db)
        memory.saveCharacter(n1, CharacterDraft("Arden", myanmarName = "အာဒင်"))
        val provider = ScriptedProvider()
        pipeline(provider, memory).translate(request(), 1L).completed()
        assertTrue(provider.requests[0].context!!.characters.isEmpty())
    }

    // 5. Quality checker integration
    @Test fun qualityCheckerReceivesSourceChunksGlossaryAndCharacters() {
        val memory = MemoryRepository(db)
        memory.saveCharacter(n1, CharacterDraft("Arden", myanmarName = "အာဒင်", status = MemoryStatus.CONFIRMED))
        memory.saveGlossary(n1, "Zephyr", "ဇက်ဖာ", GlossaryCategory.PLACE_NAME, MemoryStatus.CONFIRMED)
        var seen: QualityInput? = null
        val done = pipeline(memory = memory, checkQuality = { seen = it; TranslationQualityChecker().check(it) })
            .translate(request(), 1L).completed()

        assertEquals(paragraphs, seen!!.sourceParagraphs)
        assertEquals(3, seen!!.chunks.size)
        assertEquals(listOf("Zephyr"), seen!!.glossary.map { it.sourceTerm })
        assertEquals(listOf("Arden"), seen!!.characters.map { it.originalName })
        assertNotNull(done.quality)
        assertNull(done.qualityError)
        assertEquals(SemanticStatus.NOT_EVALUATED, done.quality!!.semantic)
        assertFalse(QualityCheckType.CHUNK_SEQUENCE in done.quality!!.skippedChecks)
    }

    // 6. Empty chapter
    @Test fun emptyChapterIsRejectedWithoutStoringAnything() {
        val provider = ScriptedProvider()
        for (bad in listOf("", "   ", "\n\n")) {
            assertEquals(InstantTranslationFailure.EMPTY_INPUT, pipeline(provider).translate(request(body = bad), 1L).failed().reason)
        }
        assertEquals(0, provider.requests.size)
        assertNull(ChapterRepository(db).get(c1))
    }

    // 7. Unsupported language
    @Test fun unsupportedLanguageIsRejectedBeforeAnythingIsStored() {
        val provider = ScriptedProvider()
        for (code in listOf("fr", "my", "", null)) {
            assertEquals(InstantTranslationFailure.UNSUPPORTED_LANGUAGE, pipeline(provider).translate(request(lang = code), 1L).failed().reason)
        }
        val narrow = ScriptedProvider(supportedSourceLanguages = setOf(SourceLanguage.ENGLISH))
        assertEquals(InstantTranslationFailure.UNSUPPORTED_LANGUAGE, pipeline(narrow).translate(request(lang = "ko"), 1L).failed().reason)
        assertEquals(0, provider.requests.size + narrow.requests.size)
        assertNull(ChapterRepository(db).get(c1))
    }

    // 8. Provider failure
    @Test fun providerFailureForEveryChunkIsReportedAndNothingIsCompleted() {
        val provider = ScriptedProvider { TranslationResult.Failure(TranslationError.PROVIDER_FAILURE, "scripted", "offline") }
        val failed = pipeline(provider).translate(request(), 1L).failed()
        assertEquals(InstantTranslationFailure.PROVIDER_FAILURE, failed.reason)
        assertEquals(0, failed.completedChunks)
        assertEquals(listOf(0, 1, 2), failed.failedChunks.map { it.chunkIndex })
        assertEquals(TranslationStatus.FAILED, storedStatus())
        assertNull(TranslationRepository(db).getTranslation(c1)!!.translatedText)
    }

    @Test fun providerExceptionIsReportedAsProviderFailure() {
        val provider = ScriptedProvider { throw IllegalStateException("boom") }
        val failed = pipeline(provider).translate(request(), 1L).failed()
        assertEquals(InstantTranslationFailure.PROVIDER_FAILURE, failed.reason)
        assertEquals("boom", failed.failedChunks[0].message)
    }

    // 9. Partial failure (and resume)
    @Test fun oneFailedChunkIsReportedAndFinishedChunksAreReusedOnRetry() {
        val broken = ScriptedProvider { req ->
            if (req.text == paragraphs[1]) TranslationResult.Failure(TranslationError.PROVIDER_FAILURE, "scripted", "timeout")
            else okResult(req, "scripted")
        }
        val failed = pipeline(broken).translate(request(), 1L).failed()
        assertEquals(InstantTranslationFailure.PARTIAL_FAILURE, failed.reason)
        assertEquals(2, failed.completedChunks)
        assertEquals(listOf(1), failed.failedChunks.map { it.chunkIndex })
        assertEquals(
            listOf(TranslationStatus.COMPLETED, TranslationStatus.FAILED, TranslationStatus.COMPLETED),
            TranslationRepository(db).getChunks(c1).sortedBy { it.chunkIndex }.map { it.status },
        )

        val healthy = ScriptedProvider()
        val done = pipeline(healthy).translate(request(), 2L).completed()
        assertEquals(listOf(paragraphs[1]), healthy.requests.map { it.text })
        assertEquals(2, done.reusedChunkCount)
        assertEquals(1, done.translation.metadata.translationVersion)
    }

    // 10. Database failure
    @Test fun missingNovelIsReportedAsDatabaseFailure() {
        val provider = ScriptedProvider()
        val failed = pipeline(provider).translate(request(novel = NovelId("nope")), 1L).failed()
        assertEquals(InstantTranslationFailure.DATABASE_FAILURE, failed.reason)
        assertEquals(0, provider.requests.size)
    }

    @Test fun failureWhileSavingAChunkIsReportedAsDatabaseFailure() {
        val provider = ScriptedProvider { req ->
            ChapterRepository(db).delete(c1)
            okResult(req, "scripted")
        }
        val failed = pipeline(provider).translate(request(), 1L).failed()
        assertEquals(InstantTranslationFailure.DATABASE_FAILURE, failed.reason)
        assertNull(TranslationRepository(db).getTranslation(c1))
    }

    // 11. Duplicate request
    @Test fun secondRequestForTheSameChapterWhileOneRunsIsRejected() {
        lateinit var pipe: InstantTranslationPipeline
        var inner: InstantTranslationOutcome? = null
        val provider = ScriptedProvider { req ->
            if (inner == null) inner = pipe.translate(request(), 2L)
            okResult(req, "scripted")
        }
        pipe = pipeline(provider)
        val outer = pipe.translate(request(), 1L)
        assertTrue(outer is InstantTranslationOutcome.Completed)
        assertEquals(InstantTranslationFailure.DUPLICATE_REQUEST, inner!!.failed().reason)
        assertTrue(pipe.translate(request(), 3L).completed().reusedWholeChapter)
    }

    // 12. Existing translation reuse
    @Test fun finishedTranslationIsReusedWithoutCallingTheProvider() {
        val provider = ScriptedProvider()
        val pipe = pipeline(provider)
        val first = pipe.translate(request(), 1L).completed()
        val second = pipe.translate(request(), 2L).completed()
        assertEquals(3, provider.requests.size)
        assertTrue(second.reusedWholeChapter)
        assertEquals(first.translation, second.translation)
        assertNotNull(second.quality)
    }

    @Test fun translationByAnotherProviderOrOfChangedTextIsNotReused() {
        pipeline(MockTranslationProvider()).translate(request(), 1L).completed()
        val real = ScriptedProvider(id = "real") { okResult(it, "real") }
        val done = pipeline(real).translate(request(), 2L).completed()
        assertEquals(3, real.requests.size)
        assertEquals("real", done.translation.metadata.providerId)

        val changed = pipeline(real).translate(request(body = "Changed one.\n\nChanged two."), 3L).completed()
        assertFalse(changed.reusedWholeChapter)
        assertEquals(2, changed.translation.metadata.translationVersion)
    }

    // 13. Status transitions
    @Test fun chapterGoesProcessingThenCompletedOrFailed() {
        val seen = mutableListOf<TranslationStatus?>()
        val provider = ScriptedProvider { req ->
            seen += storedStatus()
            okResult(req, "scripted")
        }
        pipeline(provider).translate(request(), 1L).completed()
        assertEquals(listOf(TranslationStatus.PROCESSING), seen.distinct())
        assertEquals(TranslationStatus.COMPLETED, storedStatus())

        val failing = ScriptedProvider(id = "other") { TranslationResult.Failure(TranslationError.PROVIDER_FAILURE, "other") }
        pipeline(failing).translate(request(), 2L).failed()
        assertEquals(TranslationStatus.FAILED, storedStatus())
    }

    // 14. Cancellation
    @Test fun cancellationStopsBetweenChunksKeepsFinishedChunksAndCanBeResumed() {
        val provider = ScriptedProvider()
        val cancelled = pipeline(provider).translate(request(), 1L, isCancelled = { provider.requests.size >= 1 })
        assertEquals(InstantTranslationOutcome.Cancelled(3, 1), cancelled)
        assertEquals(1, provider.requests.size)
        assertEquals(TranslationStatus.CANCELLED, storedStatus())
        assertNull(TranslationRepository(db).getTranslation(c1)!!.translatedText)

        val done = pipeline(provider).translate(request(), 2L).completed()
        assertEquals(3, provider.requests.size)
        assertEquals(1, done.reusedChunkCount)
    }

    @Test fun cancellingBeforeTheFirstChunkCallsNoProvider() {
        val provider = ScriptedProvider()
        val out = pipeline(provider).translate(request(), 1L, isCancelled = { true })
        assertEquals(InstantTranslationOutcome.Cancelled(3, 0), out)
        assertEquals(0, provider.requests.size)
    }

    // 15. Quality warning preservation
    @Test fun qualityWarningsAndBlockingIssuesAreKeptInTheResultAndDoNotBlockCompletion() {
        val warning = QualityIssue(QualityIssueType.TEXT_ARTIFACT, QualitySeverity.MINOR, "Style note", null, null, "Review wording")
        val major = QualityIssue(QualityIssueType.POSSIBLE_MISSING_CONTENT, QualitySeverity.MAJOR, "Looks short", null, null, "Check length")
        val evaluator = object : QualityEvaluator {
            override val id = "test-evaluator"
            override fun evaluate(input: QualityInput) = listOf(warning, major)
        }
        val checker = TranslationQualityChecker(evaluators = listOf(evaluator))
        val done = pipeline(checkQuality = checker::check).translate(request(), 1L).completed()
        assertTrue(done.quality!!.warnings.contains(warning))
        assertTrue(done.quality!!.issues.contains(major))
        assertEquals(TranslationStatus.COMPLETED, done.translation.status)
    }

    @Test fun failingQualityCheckStillStoresTheTranslationAndReportsTheError() {
        val done = pipeline(checkQuality = { throw IllegalStateException("checker down") }).translate(request(), 1L).completed()
        assertNull(done.quality)
        assertEquals("checker down", done.qualityError)
        assertEquals(TranslationStatus.COMPLETED, storedStatus())
    }

    // 16. Incomplete translation cannot be completed
    @Test fun invalidChunksAreRejectedAndNothingIsTranslated() {
        val provider = ScriptedProvider()
        val broken = { id: ChapterId ->
            val all = engine.chunk(id, text)
            ChunkingOutcome(ChunkSaveResult.CREATED, all.filterIndexed { i, _ -> i != 1 })
        }
        val failed = pipeline(provider, chunker = broken).translate(request(), 1L).failed()
        assertEquals(InstantTranslationFailure.INVALID_CHUNK, failed.reason)
        assertEquals(0, provider.requests.size)
        assertNull(TranslationRepository(db).getTranslation(c1))
    }

    @Test fun blankProviderOutputForAChunkMeansTheChapterIsNotCompleted() {
        val provider = ScriptedProvider { req ->
            if (req.text == paragraphs[2]) okResult(req, "scripted").copy(translatedText = "  ") else okResult(req, "scripted")
        }
        val failed = pipeline(provider).translate(request(), 1L).failed()
        assertEquals(InstantTranslationFailure.PARTIAL_FAILURE, failed.reason)
        assertEquals(TranslationStatus.FAILED, storedStatus())
        assertNull(TranslationRepository(db).getTranslation(c1)!!.translatedText)
    }

    @Test fun requestContextDefaultsToNullSoExistingProvidersAreUnaffected() {
        assertNull(TranslationRequest("x", SourceLanguage.ENGLISH).context)
    }
}
