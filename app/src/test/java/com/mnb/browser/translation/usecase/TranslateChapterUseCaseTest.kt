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
import com.mnb.browser.translation.db.ChapterRepository
import com.mnb.browser.translation.db.DbTestBase
import com.mnb.browser.translation.db.NovelRepository
import com.mnb.browser.translation.db.TranslationRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun success(req: TranslationRequest, providerId: String) = TranslationResult.Success(
    translatedText = "T:" + req.text,
    providerId = providerId,
    isMock = false,
    sourceLanguage = req.sourceLanguage,
    targetLanguage = req.targetLanguage,
)

private class FakeProvider(
    override val id: String = "fake",
    override val supportedSourceLanguages: Set<SourceLanguage> = SourceLanguage.values().toSet(),
    private val behaviour: (TranslationRequest) -> TranslationResult = { success(it, "fake") },
) : TranslationProvider {
    override val displayName = "Fake"
    override val isMock = false
    var calls = 0

    override fun translate(request: TranslationRequest): TranslationResult {
        calls++
        return behaviour(request)
    }
}

class TranslateChapterUseCaseTest : DbTestBase() {
    private val n1 = NovelId("n1")
    private val c1 = ChapterId("c1")

    @Before fun seedNovel() {
        NovelRepository(db).save(novelInfo("n1"))
    }

    private fun request(
        text: String = "Hello",
        lang: String? = "en",
        target: String = "my",
        novel: NovelId = n1,
    ) = TranslateChapterRequest(c1, novel, 1, text, lang, target)

    private fun useCase(provider: TranslationProvider = MockTranslationProvider()) =
        TranslateChapterUseCase(provider, ChapterRepository(db), TranslationRepository(db))

    private fun TranslateChapterOutcome.reason() = (this as TranslateChapterOutcome.Failed).reason

    // Mock provider result

    @Test fun mockResultIsStoredAndClearlyFlaggedAsMock() {
        val outcome = useCase().translate(request(), 10L) as TranslateChapterOutcome.Translated
        val stored = outcome.translation
        assertEquals(MockTranslationProvider.MOCK_PREFIX + "Hello", stored.translatedText)
        assertEquals(TranslationStatus.COMPLETED, stored.status)
        assertTrue(stored.metadata.isMock)
        assertEquals(MockTranslationProvider.ID, stored.metadata.providerId)
        assertEquals(1, stored.metadata.translationVersion)
    }

    // Database persistence and read

    @Test fun storedTranslationCanBeReadBack() {
        val uc = useCase()
        val translated = uc.translate(request(), 10L) as TranslateChapterOutcome.Translated

        assertEquals(translated.translation, uc.findTranslation(c1))
        val chapter = ChapterRepository(db).get(c1)!!
        assertEquals("Hello", chapter.originalText)
        assertEquals(MockTranslationProvider.MOCK_PREFIX + "Hello", chapter.translatedText)
        assertEquals(TranslationStatus.COMPLETED, chapter.status)
    }

    @Test fun unknownChapterHasNoStoredTranslation() {
        assertNull(useCase().findTranslation(ChapterId("none")))
    }

    @Test fun languageCodesAreCaseAndWhitespaceTolerant() {
        val outcome = useCase().translate(request(lang = " EN ", target = " MY "), 10L)
        assertTrue(outcome is TranslateChapterOutcome.Translated)
    }

    // Language validation

    @Test fun unknownOrMissingSourceLanguageIsRejectedBeforeAnythingIsStored() {
        val provider = FakeProvider()
        val uc = useCase(provider)
        for (code in listOf("fr", "my", "", null)) {
            assertEquals(TranslateChapterFailure.UNSUPPORTED_LANGUAGE, uc.translate(request(lang = code), 1L).reason())
        }
        assertEquals(0, provider.calls)
        assertNull(ChapterRepository(db).get(c1))
    }

    @Test fun unsupportedTargetLanguageIsRejected() {
        val provider = FakeProvider()
        val outcome = useCase(provider).translate(request(target = "en"), 1L)
        assertEquals(TranslateChapterFailure.UNSUPPORTED_LANGUAGE, outcome.reason())
        assertEquals(0, provider.calls)
    }

    @Test fun languageUnsupportedByTheProviderIsRejected() {
        val provider = FakeProvider(supportedSourceLanguages = setOf(SourceLanguage.ENGLISH))
        val outcome = useCase(provider).translate(request(lang = "ko"), 1L)
        assertEquals(TranslateChapterFailure.UNSUPPORTED_LANGUAGE, outcome.reason())
        assertEquals(0, provider.calls)
        assertNull(ChapterRepository(db).get(c1))
    }

    // Empty input

    @Test fun emptyAndBlankInputIsRejectedWithoutStoringAnything() {
        val provider = FakeProvider()
        val uc = useCase(provider)
        for (bad in listOf("", "   ", "\n\t")) {
            assertEquals(TranslateChapterFailure.EMPTY_INPUT, uc.translate(request(text = bad), 1L).reason())
        }
        assertEquals(0, provider.calls)
        assertNull(ChapterRepository(db).get(c1))
    }

    @Test fun tooLongInputIsRejected() {
        val provider = FakeProvider()
        val text = "a".repeat(TranslationRequest.MAX_TEXT_LENGTH + 1)
        assertEquals(TranslateChapterFailure.TEXT_TOO_LONG, useCase(provider).translate(request(text = text), 1L).reason())
        assertEquals(0, provider.calls)
    }

    // Request processing

    @Test fun validRequestReachesTheProviderOnceWithItsLanguages() {
        var seen: TranslationRequest? = null
        val provider = FakeProvider { req -> seen = req; success(req, "fake") }
        useCase(provider).translate(request(text = "你好", lang = "zh"), 5L)
        assertEquals(1, provider.calls)
        assertEquals(SourceLanguage.CHINESE, seen!!.sourceLanguage)
        assertEquals("你好", seen!!.text)
    }

    // Provider failure

    @Test fun providerFailureResultIsReportedAndNoTranslationIsStored() {
        val provider = FakeProvider { TranslationResult.Failure(TranslationError.PROVIDER_FAILURE, "fake", "offline") }
        val uc = useCase(provider)
        val outcome = uc.translate(request(), 1L) as TranslateChapterOutcome.Failed
        assertEquals(TranslateChapterFailure.PROVIDER_FAILURE, outcome.reason)
        assertEquals("offline", outcome.message)
        assertNull(uc.findTranslation(c1))
        assertNotNull("original text is still saved", ChapterRepository(db).get(c1))
    }

    @Test fun providerExceptionIsReportedAsProviderFailure() {
        val provider = FakeProvider { throw IllegalStateException("boom") }
        val outcome = useCase(provider).translate(request(), 1L) as TranslateChapterOutcome.Failed
        assertEquals(TranslateChapterFailure.PROVIDER_FAILURE, outcome.reason)
        assertEquals("boom", outcome.message)
    }

    @Test fun blankProviderOutputIsAProviderFailure() {
        val provider = FakeProvider { req -> success(req, "fake").copy(translatedText = "  ") }
        val uc = useCase(provider)
        assertEquals(TranslateChapterFailure.PROVIDER_FAILURE, uc.translate(request(), 1L).reason())
        assertNull(uc.findTranslation(c1))
    }

    // Duplicate requests

    @Test fun repeatingARequestReusesTheStoredTranslationWithoutCallingTheProviderAgain() {
        val provider = FakeProvider()
        val uc = useCase(provider)
        val first = uc.translate(request(), 1L) as TranslateChapterOutcome.Translated
        val second = uc.translate(request(), 2L) as TranslateChapterOutcome.AlreadyTranslated
        assertEquals(1, provider.calls)
        assertEquals(first.translation, second.translation)
        assertEquals(1, second.translation.metadata.translationVersion)
    }

    @Test fun changedTextIsTranslatedAgainAndVersionGoesUp() {
        val provider = FakeProvider()
        val uc = useCase(provider)
        uc.translate(request(text = "Old"), 1L)
        val outcome = uc.translate(request(text = "New"), 2L) as TranslateChapterOutcome.Translated
        assertEquals(2, provider.calls)
        assertEquals("T:New", outcome.translation.translatedText)
        assertEquals(2, outcome.translation.metadata.translationVersion)
        assertFalse(TranslationRepository(db).isStale(c1))
    }

    @Test fun translationByAnotherProviderIsNotReused() {
        useCase(MockTranslationProvider()).translate(request(), 1L)
        val real = FakeProvider(id = "real") { req -> success(req, "real") }
        val outcome = useCase(real).translate(request(), 2L) as TranslateChapterOutcome.Translated
        assertEquals(1, real.calls)
        assertEquals("real", outcome.translation.metadata.providerId)
        assertFalse(outcome.translation.metadata.isMock)
    }

    @Test fun requestForTheSameChapterWhileOneIsRunningIsRejected() {
        lateinit var uc: TranslateChapterUseCase
        var inner: TranslateChapterOutcome? = null
        val provider = FakeProvider { req ->
            inner = uc.translate(request(), 2L)
            success(req, "fake")
        }
        uc = useCase(provider)

        val outer = uc.translate(request(), 1L)

        assertTrue(outer is TranslateChapterOutcome.Translated)
        assertEquals(TranslateChapterFailure.DUPLICATE_REQUEST, inner!!.reason())
        assertEquals(1, provider.calls)
        // The guard is released afterwards.
        assertTrue(uc.translate(request(), 3L) is TranslateChapterOutcome.AlreadyTranslated)
    }

    @Test fun guardIsReleasedAfterAFailure() {
        var n = 0
        val provider = FakeProvider { req -> if (n++ == 0) throw IllegalStateException("boom") else success(req, "fake") }
        val uc = useCase(provider)
        assertEquals(TranslateChapterFailure.PROVIDER_FAILURE, uc.translate(request(), 1L).reason())
        assertTrue(uc.translate(request(), 2L) is TranslateChapterOutcome.Translated)
    }

    // Database failure

    @Test fun missingNovelIsReportedAsDatabaseFailure() {
        val provider = FakeProvider()
        val outcome = useCase(provider).translate(request(novel = NovelId("nope")), 1L)
        assertEquals(TranslateChapterFailure.DATABASE_FAILURE, outcome.reason())
        assertEquals(0, provider.calls)
    }

    @Test fun failureWhileSavingTheTranslationIsReportedAsDatabaseFailure() {
        // The chapter disappears while the provider is working, so the result cannot be stored.
        val provider = FakeProvider { req ->
            ChapterRepository(db).delete(c1)
            success(req, "fake")
        }
        val uc = useCase(provider)
        assertEquals(TranslateChapterFailure.DATABASE_FAILURE, uc.translate(request(), 1L).reason())
        assertNull(uc.findTranslation(c1))
    }
}
