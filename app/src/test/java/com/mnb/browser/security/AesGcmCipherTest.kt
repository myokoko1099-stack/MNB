package com.mnb.browser.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AesGcmCipherTest {
    private val aad = "test:context:v1"
    private val plain = TEST_API_KEY.toByteArray(Charsets.UTF_8)

    private fun cipher(provider: KeyProvider = SoftwareKeyProvider(), context: String = aad) =
        AesGcmCipher(provider, context)

    private fun assertReason(expected: CipherException.Reason, block: () -> Unit) {
        try {
            block()
            fail("expected CipherException($expected)")
        } catch (e: CipherException) {
            assertEquals(expected, e.reason)
            // The exception text is the reason name only, never any data.
            assertEquals(expected.name, e.message)
        }
    }

    @Test fun roundTripReturnsTheOriginalBytes() {
        val c = cipher()
        assertArrayEquals(plain, c.decrypt(c.encrypt(plain)))
    }

    @Test fun envelopeNeverContainsThePlainText() {
        val envelope = cipher().encrypt(plain)
        assertFalse(envelope.containsSequence(plain))
        assertFalse(envelope.containsSequence("AIza".toByteArray()))
    }

    @Test fun everyEncryptionUsesAFreshIvAndGivesADifferentEnvelope() {
        val c = cipher()
        val a = c.encrypt(plain)
        val b = c.encrypt(plain)
        assertNotEquals(a.toList(), b.toList())
        assertNotEquals(a.copyOfRange(2, 14).toList(), b.copyOfRange(2, 14).toList())
        assertEquals(1, a[0].toInt())
        assertEquals(12, a[1].toInt())
    }

    @Test fun modifiedCiphertextIsRejected() {
        val c = cipher()
        val envelope = c.encrypt(plain)
        envelope[envelope.size - 1] = (envelope[envelope.size - 1].toInt() xor 0x01).toByte()
        assertReason(CipherException.Reason.DECRYPT_FAILED) { c.decrypt(envelope) }
    }

    @Test fun modifiedIvIsRejected() {
        val c = cipher()
        val envelope = c.encrypt(plain)
        envelope[2] = (envelope[2].toInt() xor 0x01).toByte()
        assertReason(CipherException.Reason.DECRYPT_FAILED) { c.decrypt(envelope) }
    }

    @Test fun envelopeFromAnotherContextIsRejected() {
        val provider = SoftwareKeyProvider()
        val envelope = cipher(provider, "context:A").encrypt(plain)
        assertReason(CipherException.Reason.DECRYPT_FAILED) { cipher(provider, "context:B").decrypt(envelope) }
    }

    @Test fun anotherKeyCannotDecrypt() {
        val envelope = cipher(SoftwareKeyProvider()).encrypt(plain)
        assertReason(CipherException.Reason.DECRYPT_FAILED) { cipher(SoftwareKeyProvider()).decrypt(envelope) }
    }

    @Test fun decryptWithoutKeyFailsAndNeverCreatesOne() {
        val provider = SoftwareKeyProvider()
        val envelope = cipher(provider).encrypt(plain)
        provider.secretKey = null
        assertReason(CipherException.Reason.KEY_UNAVAILABLE) { cipher(provider).decrypt(envelope) }
        assertNull(provider.secretKey)
    }

    @Test fun malformedEnvelopesAreRejected() {
        val c = cipher()
        val good = c.encrypt(plain)
        val wrongVersion = good.copyOf().also { it[0] = 2 }
        val wrongIvLength = good.copyOf().also { it[1] = 16 }
        for (bad in listOf(ByteArray(0), ByteArray(1), ByteArray(29), good.copyOf(29), wrongVersion, wrongIvLength)) {
            assertReason(CipherException.Reason.MALFORMED_ENVELOPE) { c.decrypt(bad) }
        }
    }

    @Test fun truncatedEnvelopeLongEnoughToParseStillFailsAuthentication() {
        val c = cipher()
        val good = c.encrypt(plain)
        assertReason(CipherException.Reason.DECRYPT_FAILED) { c.decrypt(good.copyOf(good.size - 3)) }
    }

    @Test fun anUnusableKeyIsReplacedOnceWhenEncrypting() {
        val provider = BrokenThenGoodKeyProvider()
        val c = cipher(provider)
        val envelope = c.encrypt(plain)
        assertEquals(1, provider.deleteCalls)
        assertArrayEquals(plain, c.decrypt(envelope))
    }

    @Test fun encryptFailureIsReportedAsCipherExceptionNotRawException() {
        val provider = object : KeyProvider {
            override fun getKey(): javax.crypto.SecretKey? = null
            override fun getOrCreateKey(): javax.crypto.SecretKey = throw java.security.ProviderException("no keystore")
            override fun deleteKey() {}
        }
        assertReason(CipherException.Reason.ENCRYPT_FAILED) { cipher(provider).encrypt(plain) }
    }

    @Test fun deleteKeyRemovesTheKeyAndOldEnvelopesBecomeUnreadable() {
        val provider = SoftwareKeyProvider()
        val c = cipher(provider)
        val envelope = c.encrypt(plain)
        c.deleteKey()
        assertTrue(provider.secretKey == null)
        assertReason(CipherException.Reason.KEY_UNAVAILABLE) { c.decrypt(envelope) }
    }
}
