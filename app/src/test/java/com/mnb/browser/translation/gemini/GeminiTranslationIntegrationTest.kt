package com.mnb.browser.translation.gemini

import com.mnb.browser.security.ApiKey
import com.mnb.browser.security.ApiKeyLookup
import com.mnb.browser.security.ApiKeyProvider
import com.mnb.browser.security.SecureStorageError
import com.mnb.browser.translation.MockTranslationProvider
import com.mnb.browser.translation.NovelId
import com.mnb.browser.translation.ChapterId
import com.mnb.browser.translation.SourceLanguage
import com.mnb.browser.translation.TranslationError
import com.mnb.browser.translation.TranslationProvider
import com.mnb.browser.translation.TranslationRequest
import com.mnb.browser.translation.TranslationResult
import com.mnb.browser.translation.TranslationService
import com.mnb.browser.translation.TranslationStatus
import com.mnb.browser.translation.chunking.ChapterChunkRepository
import com.mnb.browser.translation.db.ChapterRepository
import com.mnb.browser.translation.db.DbTestBase
import com.mnb.browser.translation.db.TranslationRepository
import com.mnb.browser.translation.memory.MemoryRepository
import com.mnb.browser.translation.usecase.InstantTranslationOutcome
import com.mnb.browser.translation.usecase.InstantTranslationPipeline
import com.mnb.browser.translation.usecase.TranslateChapterRequest
import com.mnb.browser.reader.translation.ProviderReadiness
import com.mnb.browser.reader.translation.TranslationProviderGuard
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 06-B2: the whole chain TranslationService -> GeminiTranslationProvider ->
 * DirectGeminiProvider, with a fake HTTP layer and fake key providers (no network, no Keystore),
 * plus the instant pipeline against a real in-memory database. Runs under Robolectric, where the
 * test thread IS the Android main thread: that makes the real main-thread guard observable.
 */
class GeminiTranslationIntegrationTest : DbTestBase() {

    private class FakeHttp(var result: GeminiHttpResult) : GeminiHttpClient {
        var calls = 0
        var last: GeminiHttpRequest? = null
        val threads = mutableListOf<Thread>()

        override fun post(request: GeminiHttpRequest): GeminiHttpResult {
            calls++
            last = request
            threads += Thread.currentThread()
            return result
        }
    }

    private class CountingKeys(private val lookup: () -> ApiKeyLookup) : ApiKeyProvider {
        var calls = 0
        override fun lookup(): ApiKeyLookup {
            calls++
            return lookup.invoke()
        }
    }

    private fun okBody(text: String = "translated text") =
        """{"candidates":[{"content":{"parts":[{"text":"$text"}]},"finishReason":"STOP"}]}"""

    private fun ok(text: String = "translated text") =
        GeminiHttpResult.Response(GeminiHttpResponse(200, okBody(text)))

    private fun availableKeys() = CountingKeys { ApiKeyLookup.Available(ApiKey(FAKE_KEY)) }

    private fun adapter(
        http: FakeHttp,
        keys: ApiKeyProvider = availableKeys(),
        onMain: () -> Boolean = { false },
    ) = GeminiTranslationProvider(DirectGeminiProvider(keys, http), onMain)

    private val request = TranslationRequest("Hello world", SourceLanguage.ENGLISH)

    // ================= TranslationService -> Gemini =================

    @Test
    fun aRequestThroughTheServiceReachesGeminiAndComesBackTranslated() {
        val http = FakeHttp(ok("မင်္ဂလာပါ"))
        val result = TranslationService(adapter(http)).translate(request)
        assertTrue(result.toString(), result is TranslationResult.Success)
        result as TranslationResult.Success
        assertEquals("မင်္ဂလာပါ", result.translatedText)
        assertEquals(DirectGeminiProvider.ID, result.providerId)
        assertFalse(result.isMock)
        assertEquals(1, http.calls)
    }

    @Test
    fun theKeyOnlyTravelsInTheHeaderOfTheRequest() {
        val http = FakeHttp(ok())
        TranslationService(adapter(http)).translate(request)
        val sent = http.last!!
        assertEquals(FAKE_KEY, sent.headers[GeminiConfig.API_KEY_HEADER])
        assertFalse(sent.url.contains(FAKE_KEY))
        assertFalse(sent.body.contains(FAKE_KEY))
        assertTrue(sent.url.startsWith("https://"))
    }

    @Test
    fun theServiceStillValidatesBeforeGeminiIsCalled() {
        val http = FakeHttp(ok())
        val keys = availableKeys()
        val result = TranslationService(adapter(http, keys)).translate(TranslationRequest(" ", SourceLanguage.ENGLISH))
        assertEquals(TranslationError.EMPTY_TEXT, (result as TranslationResult.Failure).error)
        assertEquals(0, http.calls)
        assertEquals(0, keys.calls)
    }

    @Test
    fun aGeminiHttpFailureReachesTheCallerAsAFixedProviderFailure() {
        val http = FakeHttp(GeminiHttpResult.Response(GeminiHttpResponse(500, "server said $FAKE_KEY and Hello world")))
        val result = TranslationService(adapter(http)).translate(request)
        result as TranslationResult.Failure
        assertEquals(TranslationError.PROVIDER_FAILURE, result.error)
        assertEquals(GeminiError.SERVER_ERROR.message, result.message)
        assertFalse(result.toString().contains(FAKE_KEY))
        assertFalse(result.toString().contains("Hello world"))
    }

    // ================= missing / unusable key =================

    @Test
    fun aMissingKeySendsNothingAndGivesAFixedMessage() {
        val http = FakeHttp(ok())
        val result = TranslationService(adapter(http, CountingKeys { ApiKeyLookup.Missing })).translate(request)
        result as TranslationResult.Failure
        assertEquals(TranslationError.PROVIDER_FAILURE, result.error)
        assertEquals(GeminiError.MISSING_API_KEY.message, result.message)
        assertEquals(0, http.calls)
    }

    @Test
    fun anUnreadableKeySendsNothingForEveryStorageError() {
        for (error in SecureStorageError.values()) {
            val http = FakeHttp(ok())
            val result = TranslationService(adapter(http, CountingKeys { ApiKeyLookup.Unavailable(error) })).translate(request)
            result as TranslationResult.Failure
            assertEquals(error.name, GeminiError.API_KEY_UNAVAILABLE.message, result.message)
            assertEquals(error.name, 0, http.calls)
        }
    }

    @Test
    fun aKeyStoreThatThrowsSendsNothing() {
        val http = FakeHttp(ok())
        val keys = CountingKeys { throw IllegalStateException("keystore exploded $FAKE_KEY") }
        val result = TranslationService(adapter(http, keys)).translate(request)
        result as TranslationResult.Failure
        assertEquals(0, http.calls)
        assertFalse(result.toString().contains(FAKE_KEY))
    }

    @Test
    fun anErrorWithAKeyInItsTextNeverReachesTheResult() {
        val http = FakeHttp(GeminiHttpResult.Response(GeminiHttpResponse(403, """{"error":{"message":"bad key $FAKE_KEY"}}""")))
        val result = TranslationService(adapter(http)).translate(request)
        result as TranslationResult.Failure
        assertEquals(GeminiError.API_KEY_REJECTED.message, result.message)
        assertFalse(result.toString().contains(FAKE_KEY))
    }

    // ================= main thread =================

    @Test
    fun underRobolectricTheTestThreadIsTheAndroidMainThread() {
        // Proves the production guard really recognises the main thread (and that this class tests it).
        assertTrue(GeminiTranslationProvider.currentThreadIsAndroidMain())
    }

    @Test
    fun theProductionGuardRefusesToTranslateOnTheMainThread() {
        val http = FakeHttp(ok())
        val keys = availableKeys()
        val provider = GeminiTranslationProvider(DirectGeminiProvider(keys, http)) // real guard
        val result = TranslationService(provider).translate(request)
        result as TranslationResult.Failure
        assertEquals(GeminiTranslationProvider.MAIN_THREAD_MESSAGE, result.message)
        assertEquals(0, http.calls)
        assertEquals("the key must not even be read on the main thread", 0, keys.calls)
    }

    @Test
    fun onABackgroundThreadTheProductionGuardAllowsTheRequest() {
        val http = FakeHttp(ok())
        val provider = GeminiTranslationProvider(DirectGeminiProvider(availableKeys(), http)) // real guard
        val holder = AtomicReference<TranslationResult?>(null)
        val worker = Thread { holder.set(TranslationService(provider).translate(request)) }
        worker.start()
        worker.join(10_000)
        assertTrue(holder.get().toString(), holder.get() is TranslationResult.Success)
        assertEquals(1, http.threads.size)
        assertFalse(http.threads.single() === Thread.currentThread())
    }

    // ================= instant pipeline (real database) =================

    @Test
    fun theInstantPipelineStoresAGeminiTranslationAsARealOne() {
        seedChapter(chapterId = "c1", text = "Hello world")
        val http = FakeHttp(ok("မင်္ဂလာပါ ကမ္ဘာ"))
        val pipeline = pipelineFor(adapter(http))
        val outcome = runOffMainThread {
            pipeline.translate(TranslateChapterRequest(ChapterId("c1"), NovelId("n1"), 1, "Hello world", "en"), 100L)
        }
        assertTrue(outcome.toString(), outcome is InstantTranslationOutcome.Completed)
        val stored = (outcome as InstantTranslationOutcome.Completed).translation
        assertEquals(TranslationStatus.COMPLETED, stored.status)
        assertEquals("မင်္ဂလာပါ ကမ္ဘာ", stored.translatedText)
        assertEquals(DirectGeminiProvider.ID, stored.metadata.providerId)
        assertFalse(stored.metadata.isMock)
        assertNotNull(TranslationRepository(db).getTranslation(ChapterId("c1")))
        assertTrue(http.calls >= 1)
    }

    @Test
    fun aPipelineWithAMissingKeyStoresNoTranslation() {
        seedChapter(chapterId = "c1", text = "Hello world")
        val http = FakeHttp(ok())
        val pipeline = pipelineFor(adapter(http, CountingKeys { ApiKeyLookup.Missing }))
        val outcome = runOffMainThread {
            pipeline.translate(TranslateChapterRequest(ChapterId("c1"), NovelId("n1"), 1, "Hello world", "en"), 100L)
        }
        assertTrue(outcome.toString(), outcome is InstantTranslationOutcome.Failed)
        assertEquals(0, http.calls)
        val stored = TranslationRepository(db).getTranslation(ChapterId("c1"))
        assertTrue(stored == null || stored.status != TranslationStatus.COMPLETED)
    }

    // ================= mock regression =================

    @Test
    fun theMockProviderStillWorksThroughTheServiceAndNeverTouchesGemini() {
        val result = TranslationService(MockTranslationProvider()).translate(request)
        result as TranslationResult.Success
        assertEquals(MockTranslationProvider.MOCK_PREFIX + "Hello world", result.translatedText)
        assertTrue(result.isMock)
        assertEquals(MockTranslationProvider.ID, result.providerId)
    }

    @Test
    fun theMockProviderIsStillBlockedByTheReaderGuard() {
        assertEquals(ProviderReadiness.MockOnly, TranslationProviderGuard.check(MockTranslationProvider(), SourceLanguage.ENGLISH))
        assertEquals(ProviderReadiness.NotConfigured, TranslationProviderGuard.check(null, SourceLanguage.ENGLISH))
        assertEquals(ProviderReadiness.Ready, TranslationProviderGuard.check(adapter(FakeHttp(ok())), SourceLanguage.ENGLISH))
    }

    // ================= wiring =================

    @Test
    fun withoutASavedKeyTheWiringGivesNoProvider() {
        val wiring = GeminiTranslationWiring(availableKeys(), keyIsSaved = { false })
        assertNull(wiring.providerOrNull())
        assertNull(wiring.readerProviderId())
    }

    @Test
    fun withASavedKeyTheWiringGivesTheSameGeminiProviderEveryTime() {
        val wiring = GeminiTranslationWiring(availableKeys(), keyIsSaved = { true })
        val first = wiring.providerOrNull()
        assertNotNull(first)
        assertSame(first, wiring.providerOrNull())
        assertSame(first, wiring.provider)
        assertEquals(DirectGeminiProvider.ID, wiring.readerProviderId())
        assertFalse(first!!.isMock)
    }

    @Test
    fun theWiringNeverReadsTheKeyItself() {
        val keys = availableKeys()
        val wiring = GeminiTranslationWiring(keys, keyIsSaved = { true })
        wiring.providerOrNull()
        wiring.readerProviderId()
        assertEquals(0, keys.calls)
    }

    @Test
    fun aFailingKeyStatusCheckGivesNoProviderInsteadOfThrowing() {
        val wiring = GeminiTranslationWiring(availableKeys(), keyIsSaved = { throw IllegalStateException("boom") })
        assertNull(wiring.providerOrNull())
    }

    @Test
    fun theWiringUsesTheGivenFactoryOnlyOnce() {
        var built = 0
        val wiring = GeminiTranslationWiring(
            keys = availableKeys(),
            keyIsSaved = { true },
            providerFactory = { built++; MockTranslationProvider() },
        )
        wiring.providerOrNull(); wiring.providerOrNull(); wiring.provider
        assertEquals(1, built)
    }

    // ================= helpers =================

    private fun pipelineFor(provider: TranslationProvider) = InstantTranslationPipeline(
        provider = provider,
        chapters = ChapterRepository(db),
        chunkRepository = ChapterChunkRepository(db),
        translations = TranslationRepository(db),
        memory = MemoryRepository(db),
    )

    /** Runs [block] on a plain thread (the real guard refuses the Robolectric main thread). */
    private fun <T> runOffMainThread(block: () -> T): T {
        val holder = AtomicReference<Result<T>?>(null)
        val thread = Thread { holder.set(runCatching(block)) }
        thread.start()
        thread.join(30_000)
        return holder.get()!!.getOrThrow()
    }

    companion object {
        const val FAKE_KEY = "fake-test-key-0123456789"
    }
}
