package com.mnb.browser.security

import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/** A fake Gemini-style key. Not a real credential. */
const val TEST_API_KEY = "AIzaSyTEST-fake_key_0123456789abcdefghijk"

fun newSoftwareKey(): SecretKey =
    KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

/** JVM stand-in for the Android Keystore (which does not exist in unit tests). */
class SoftwareKeyProvider(var key: SecretKey? = null) : KeyProvider {
    var deleteCalls = 0
    var failDelete = false

    override fun getKey(): SecretKey? = key

    override fun getOrCreateKey(): SecretKey = key ?: newSoftwareKey().also { key = it }

    override fun deleteKey() {
        if (failDelete) throw IllegalStateException("delete failed")
        deleteCalls++
        key = null
    }
}

/** First hands out a key AES rejects (5 bytes), and a good one once it has been deleted. */
class BrokenThenGoodKeyProvider : KeyProvider {
    private var broken = true
    private var good: SecretKey? = null
    var deleteCalls = 0

    override fun getKey(): SecretKey? = if (broken) SecretKeySpec(ByteArray(5), "AES") else good

    override fun getOrCreateKey(): SecretKey {
        if (broken) return SecretKeySpec(ByteArray(5), "AES")
        return good ?: newSoftwareKey().also { good = it }
    }

    override fun deleteKey() {
        deleteCalls++
        broken = false
    }
}

class MemoryBlobStorage : EncryptedBlobStorage {
    var blob: ByteArray? = null
    var failWrites = false
    var failClear = false
    var readError: Exception? = null
    var writeCount = 0

    override fun exists(): Boolean = blob != null

    override fun read(): ByteArray? {
        readError?.let { throw it }
        return blob?.copyOf()
    }

    override fun write(blob: ByteArray): Boolean {
        if (failWrites) return false
        this.blob = blob.copyOf()
        writeCount++
        return true
    }

    override fun clear(): Boolean {
        if (failClear) return false
        blob = null
        return true
    }
}

/** A cipher whose every call blows up with an unexpected exception whose message holds a secret-like text. */
class ExplodingCipher : AeadCipher {
    override fun encrypt(plain: ByteArray): ByteArray = throw RuntimeException("boom $TEST_API_KEY")
    override fun decrypt(envelope: ByteArray): ByteArray = throw RuntimeException("boom $TEST_API_KEY")
    override fun deleteKey() {
        throw RuntimeException("boom $TEST_API_KEY")
    }
}

fun ByteArray.containsSequence(needle: ByteArray): Boolean {
    if (needle.isEmpty()) return true
    for (i in 0..(size - needle.size)) {
        var match = true
        for (j in needle.indices) {
            if (this[i + j] != needle[j]) { match = false; break }
        }
        if (match) return true
    }
    return false
}
