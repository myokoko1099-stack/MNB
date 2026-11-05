package com.mnb.browser.security

import android.content.Context
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedPreferencesEncryptedBlobStorageTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private fun storage() = SharedPreferencesEncryptedBlobStorage(context)
    private fun prefs() = context.getSharedPreferences(
        SharedPreferencesEncryptedBlobStorage.DEFAULT_PREFS_NAME, Context.MODE_PRIVATE,
    )

    @Test fun emptyStorageReadsNull() {
        val s = storage()
        s.clear()
        assertFalse(s.exists())
        assertNull(s.read())
    }

    @Test fun writtenBlobIsReadBackByteForByte() {
        val blob = ByteArray(40) { (it * 7).toByte() }
        val s = storage()
        assertTrue(s.write(blob))
        assertTrue(s.exists())
        assertArrayEquals(blob, storage().read()) // a second instance = app restart
    }

    @Test fun writeReplacesAndClearEmpties() {
        val s = storage()
        s.write(byteArrayOf(1, 2, 3))
        s.write(byteArrayOf(9, 9))
        assertArrayEquals(byteArrayOf(9, 9), s.read())
        assertTrue(s.clear())
        assertFalse(s.exists())
        assertTrue(s.clear()) // already empty is still success
    }

    @Test fun invalidStoredTextIsReportedAsCorrupted() {
        prefs().edit().putString(SharedPreferencesEncryptedBlobStorage.DEFAULT_ENTRY_KEY, "@@@ not base64 @@@").commit()
        try {
            storage().read()
            fail("expected StoredBlobCorruptedException")
        } catch (e: StoredBlobCorruptedException) {
            // expected
        }
    }

    @Test fun usesItsOwnFileNotTheReadersFile() {
        storage().write(byteArrayOf(1, 2, 3))
        val reader = context.getSharedPreferences("mnb_reader", Context.MODE_PRIVATE)
        assertTrue(reader.all.isEmpty())
    }

    @Test fun fullStackLeavesNoPlainKeyInThePreferencesFile() {
        val provider = SoftwareKeyProvider()
        fun newStore() = SecureApiKeyStore(
            AesGcmCipher(provider, SecureApiKeyStore.ASSOCIATED_DATA),
            storage(),
        )
        assertTrue(newStore().save(TEST_API_KEY) is SecureResult.Success)

        val stored = prefs().all
        assertEquals(1, stored.size)
        for ((name, value) in stored) {
            assertFalse(name.contains(TEST_API_KEY))
            assertFalse(value.toString().contains(TEST_API_KEY))
            assertFalse(value.toString().contains("AIza"))
        }
        val read = newStore().read() as SecureResult.Success
        assertEquals(TEST_API_KEY, read.value.reveal())

        assertTrue(newStore().delete() is SecureResult.Success)
        assertTrue(prefs().all.isEmpty())
    }
}
