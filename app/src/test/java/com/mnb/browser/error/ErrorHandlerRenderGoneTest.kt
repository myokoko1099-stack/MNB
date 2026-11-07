package com.mnb.browser.error

import com.mnb.browser.state.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 2F: the message shown for a tab whose renderer keeps crashing. */
class ErrorHandlerRenderGoneTest {

    @Test fun theErrorOffersRetryForTheCrashedPage() {
        val error = ErrorHandler.renderGone("https://example.com/page")
        assertEquals(ErrorKind.UNKNOWN, error.kind)
        assertEquals("https://example.com/page", error.failingUrl)
        assertTrue(error.title.isNotBlank())
        assertTrue(error.message.isNotBlank())
    }

    @Test fun withoutAnAddressThereIsNothingToRetry() {
        assertNull(ErrorHandler.renderGone(null).failingUrl)
        assertNull(ErrorHandler.renderGone("   ").failingUrl)
        assertNotNull(ErrorHandler.renderGone(null).title)
    }
}
