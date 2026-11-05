package com.mnb.browser.security

import javax.crypto.SecretKey

/** Owns one AES key. The real implementation keeps it inside the Android Keystore. */
interface KeyProvider {
    /** The existing key, or null when there is none or it can no longer be used. Never creates. */
    fun getKey(): SecretKey?

    /** The existing key, or a freshly generated one. A key that exists but is unusable is replaced. */
    fun getOrCreateKey(): SecretKey

    /** Deletes the key if present. Does nothing when there is none. */
    fun deleteKey()
}
