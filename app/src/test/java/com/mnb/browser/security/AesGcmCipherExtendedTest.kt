package com.mnb.browser.security

import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Phase 06-A4: more AES-GCM encryption / decryption cases on top of [AesGcmCipherTest].
 * Pure JVM: the Keystore is replaced by [SoftwareKeyProvider].
 */
class AesGcmCipherExtendedTest {
    private val aad = "test:context:v1"

    private fun cipher(provider: KeyProvider = SoftwareKeyProvider(), context: String = aad) =
        AesGcmCipher(provider, context)

    private fun assertReason(expected: CipherException.Reason, block: () -> Unit) {
        try {
            block()
            fail("expected CipherException($expected)")
        } catch (e: CipherException) {
            assertEquals(expected, e.reason)
            assertEquals(expected.name, e.message)
        }
    }

    // ---- round trips ----

    @Test
    fun emptyPlaintextRoundTrips() {
        val c = cipher()
        val envelope = c.encrypt(ByteArray(0))
        assertEquals(HEADER + IV + TAG, envelope.size)
        assertEquals(0, c.decrypt(envelope).size)
    }

    @Test
    fun envelopeSizeIsHeaderPlusIvPlusPlaintextPlusTag() {
        val c = cipher()
        for (n in listOf(1, 16, 17, 100, 512)) {
            val envelope = c.encrypt(ByteArray(n) { 7 })
            assertEquals("plain size $n", HEADER + IV + n + TAG, envelope.size)
        }
    }

    @Test
    fun everyByteValueRoundTrips() {
        val c = cipher()
        val plain = ByteArray(256) { it.toByte() }
        assertArrayEquals(plain, c.decrypt(c.encrypt(plain)))
    }

    @Test
    fun aLargePlaintextRoundTrips() {
        val c = cipher()
        val plain = ByteArray(64 * 1024) { (it * 31).toByte() }
        assertArrayEquals(plain, c.decrypt(c.encrypt(plain)))
    }

    @Test
    fun nonAsciiTextRoundTripsAsUtf8Bytes() {
        val c = cipher()
        val plain = "k\u00E9y-\u1019\u103C\u1014\u103A\u1019\u102C-\uD83D\uDE00".toByteArray(Charsets.UTF_8)
        assertArrayEquals(plain, c.decrypt(c.encrypt(plain)))
    }

    @Test
    fun twoCipherObjectsWithTheSameKeyAndContextAreInterchangeable() {
        val provider = SoftwareKeyProvider()
        val plain = TEST_API_KEY.toByteArray()
        val envelope = cipher(provider).encrypt(plain)
        assertArrayEquals(plain, cipher(provider).decrypt(envelope))
    }

    @Test
    fun anEmptyContextWorksButIsNotInterchangeableWithANonEmptyOne() {
        val provider = SoftwareKeyProvider()
        val plain = TEST_API_KEY.toByteArray()
        val envelope = cipher(provider, "").encrypt(plain)
        assertArrayEquals(plain, cipher(provider, "").decrypt(envelope))
        assertReason(CipherException.Reason.DECRYPT_FAILED) { cipher(provider, "x").decrypt(envelope) }
    }

    @Test
    fun theContextIsCaseSensitive() {
        val provider = SoftwareKeyProvider()
        val envelope = cipher(provider, "mnb:ctx").encrypt(ByteArray(8) { 1 })
        assertReason(CipherException.Reason.DECRYPT_FAILED) { cipher(provider, "MNB:CTX").decrypt(envelope) }
    }

    // ---- IV and randomness ----

    @Test
    fun manyEncryptionsOfTheSameTextNeverRepeatAnIv() {
        val c = cipher()
        val plain = TEST_API_KEY.toByteArray()
        val ivs = HashSet<List<Byte>>()
        repeat(200) { ivs.add(c.encrypt(plain).copyOfRange(HEADER, HEADER + IV).toList()) }
        assertEquals(200, ivs.size)
    }

    @Test
    fun twoEncryptionsOfTheSameTextGiveDifferentCiphertext() {
        val c = cipher()
        val plain = TEST_API_KEY.toByteArray()
        val a = c.encrypt(plain)
        val b = c.encrypt(plain)
        assertNotEquals(
            a.copyOfRange(HEADER + IV, a.size).toList(),
            b.copyOfRange(HEADER + IV, b.size).toList(),
        )
    }

    // ---- tampering ----

    @Test
    fun changingAnySingleByteAfterTheHeaderIsDetected() {
        val c = cipher()
        val envelope = c.encrypt(TEST_API_KEY.toByteArray())
        for (i in HEADER until envelope.size) {
            val modified = envelope.copyOf()
            modified[i] = (modified[i].toInt() xor 0x01).toByte()
            assertReason(CipherException.Reason.DECRYPT_FAILED) { c.decrypt(modified) }
        }
    }

    @Test
    fun changingTheHeaderIsReportedAsMalformed() {
        val c = cipher()
        val envelope = c.encrypt(TEST_API_KEY.toByteArray())
        for (i in 0 until HEADER) {
            val modified = envelope.copyOf()
            modified[i] = (modified[i].toInt() xor 0x01).toByte()
            assertReason(CipherException.Reason.MALFORMED_ENVELOPE) { c.decrypt(modified) }
        }
    }

    @Test
    fun extraBytesAppendedToTheEnvelopeAreDetected() {
        val c = cipher()
        val envelope = c.encrypt(TEST_API_KEY.toByteArray())
        assertReason(CipherException.Reason.DECRYPT_FAILED) { c.decrypt(envelope + byteArrayOf(0)) }
    }

    @Test
    fun bytesRemovedFromTheStartOfTheCiphertextAreDetected() {
        val c = cipher()
        val envelope = c.encrypt(TEST_API_KEY.toByteArray())
        val shortened = envelope.copyOfRange(0, HEADER + IV) + envelope.copyOfRange(HEADER + IV + 1, envelope.size)
        assertReason(CipherException.Reason.DECRYPT_FAILED) { c.decrypt(shortened) }
    }

    @Test
    fun swappingInAnotherMessagesTagIsDetected() {
        val c = cipher()
        val a = c.encrypt(TEST_API_KEY.toByteArray())
        val b = c.encrypt(TEST_API_KEY.toByteArray())
        // Same length, so a's ciphertext with b's tag has a valid shape but must not verify.
        val mixed = a.copyOf()
        System.arraycopy(b, b.size - TAG, mixed, mixed.size - TAG, TAG)
        assertReason(CipherException.Reason.DECRYPT_FAILED) { c.decrypt(mixed) }
    }

    // ---- inputs are left alone ----

    @Test
    fun encryptDoesNotChangeTheInputArray() {
        val plain = TEST_API_KEY.toByteArray()
        val copy = plain.copyOf()
        cipher().encrypt(plain)
        assertArrayEquals(copy, plain)
    }

    @Test
    fun decryptDoesNotChangeTheEnvelope() {
        val c = cipher()
        val envelope = c.encrypt(TEST_API_KEY.toByteArray())
        val copy = envelope.copyOf()
        c.decrypt(envelope)
        assertArrayEquals(copy, envelope)
    }

    @Test
    fun aFailedDecryptDoesNotChangeTheEnvelopeEither() {
        val c = cipher()
        val envelope = c.encrypt(TEST_API_KEY.toByteArray())
        envelope[envelope.size - 1] = (envelope[envelope.size - 1].toInt() xor 0x01).toByte()
        val copy = envelope.copyOf()
        try {
            c.decrypt(envelope)
            fail("expected failure")
        } catch (e: CipherException) {
            // expected
        }
        assertArrayEquals(copy, envelope)
    }

    // ---- key handling ----

    @Test
    fun aKeyProviderThatThrowsWhenReadingGivesKeyUnavailableWithoutItsMessage() {
        val envelope = cipher().encrypt(TEST_API_KEY.toByteArray())
        val provider = object : KeyProvider {
            override fun getKey(): SecretKey? = throw IllegalStateException("secret $TEST_API_KEY")
            override fun getOrCreateKey(): SecretKey = throw IllegalStateException("secret $TEST_API_KEY")
            override fun deleteKey() {}
        }
        try {
            cipher(provider).decrypt(envelope)
            fail("expected CipherException")
        } catch (e: CipherException) {
            assertEquals(CipherException.Reason.KEY_UNAVAILABLE, e.reason)
            assertEquals("KEY_UNAVAILABLE", e.message)
            assertFalse(e.toString().contains(TEST_API_KEY))
        }
    }

    @Test
    fun aKeyThatAesRejectsOnDecryptIsADecryptFailure() {
        val provider = SoftwareKeyProvider()
        val envelope = cipher(provider).encrypt(TEST_API_KEY.toByteArray())
        provider.secretKey = SecretKeySpec(ByteArray(5), "AES")
        assertReason(CipherException.Reason.DECRYPT_FAILED) { cipher(provider).decrypt(envelope) }
    }

    @Test
    fun aKeyThatStaysBrokenIsReplacedOnlyOnceAndThenEncryptFails() {
        val provider = AlwaysBrokenKeyProvider()
        assertReason(CipherException.Reason.ENCRYPT_FAILED) { cipher(provider).encrypt(ByteArray(4)) }
        assertEquals(1, provider.deleteCalls)
    }

    @Test
    fun aHealthyKeyIsNeverDeletedWhileEncrypting() {
        val provider = SoftwareKeyProvider()
        val c = cipher(provider)
        repeat(5) { c.encrypt(ByteArray(8)) }
        assertEquals(0, provider.deleteCalls)
    }

    @Test
    fun encryptCreatesTheKeyOnFirstUseAndReusesItAfterwards() {
        val provider = SoftwareKeyProvider()
        assertNull(provider.secretKey)
        val c = cipher(provider)
        c.encrypt(ByteArray(8))
        val first = provider.secretKey
        assertTrue(first != null)
        c.encrypt(ByteArray(8))
        assertTrue(first === provider.secretKey)
    }

    @Test
    fun afterTheKeyIsReplacedOldEnvelopesCanNoLongerBeRead() {
        val provider = SoftwareKeyProvider()
        val c = cipher(provider)
        val oldEnvelope = c.encrypt(TEST_API_KEY.toByteArray())
        c.deleteKey()
        c.encrypt(TEST_API_KEY.toByteArray()) // creates a new key
        assertReason(CipherException.Reason.DECRYPT_FAILED) { c.decrypt(oldEnvelope) }
    }

    @Test
    fun deletingTheKeyTwiceIsHarmless() {
        val provider = SoftwareKeyProvider()
        val c = cipher(provider)
        c.encrypt(ByteArray(8))
        c.deleteKey()
        c.deleteKey()
        assertNull(provider.secretKey)
    }

    // ---- redaction ----

    @Test
    fun cipherExceptionsNeverContainDataForAnyReason() {
        for (reason in CipherException.Reason.values()) {
            val e = CipherException(reason, IllegalStateException("secret $TEST_API_KEY"))
            assertEquals(reason.name, e.message)
            // Only the cause (kept for debugging) holds text; the exception's own text does not.
            assertFalse(e.message!!.contains(TEST_API_KEY))
        }
    }

    /** Hands out a key AES always rejects, and stays broken after [deleteKey]. */
    private class AlwaysBrokenKeyProvider : KeyProvider {
        var deleteCalls = 0
        private val broken: SecretKey = SecretKeySpec(ByteArray(5), "AES")
        override fun getKey(): SecretKey? = broken
        override fun getOrCreateKey(): SecretKey = broken
        override fun deleteKey() {
            deleteCalls++
        }
    }

    private companion object {
        const val HEADER = 2
        const val IV = 12
        const val TAG = 16
    }
}
