package com.mnb.browser.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 04-I2 models must work with the Phase 04-I1 core unchanged. */
class TranslationCoreCompatibilityTest {
    private val chunk = TranslationChunk(ChunkId("k1"), ChapterId("c1"), 0, "Hello")

    @Test fun chunkBuildsAValidCoreRequest() {
        val request = chunk.toRequest(SourceLanguage.ENGLISH)
        assertEquals("Hello", request.text)
        assertEquals(TargetLanguage.MYANMAR, request.targetLanguage)
        assertNull(request.validate())
    }

    @Test fun oversizedChunkIsStillCaughtByTheCore() {
        val big = TranslationChunk(ChunkId("k2"), ChapterId("c1"), 1, "a".repeat(TranslationRequest.MAX_TEXT_LENGTH + 1))
        assertEquals(TranslationError.TEXT_TOO_LONG, big.toRequest(SourceLanguage.ENGLISH).validate())
    }

    @Test fun mockResultFeedsMetadataAndChunkCompletion() {
        val result = TranslationService(MockTranslationProvider())
            .translate(chunk.toRequest(SourceLanguage.ENGLISH)) as TranslationResult.Success
        val meta = TranslationMetadata.fromResult(result, nowMillis = 42L)
        assertEquals("mock", meta.providerId)
        assertTrue(meta.isMock)
        assertEquals(42L, meta.createdAtMillis)

        val done = chunk.copy(translatedText = result.translatedText, status = TranslationStatus.COMPLETED)
        assertEquals(TranslationStatus.COMPLETED, done.status)
        assertTrue(done.translatedText!!.startsWith(MockTranslationProvider.MOCK_PREFIX))
    }
}
