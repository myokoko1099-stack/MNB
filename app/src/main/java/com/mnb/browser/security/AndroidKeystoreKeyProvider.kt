package com.mnb.browser.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * A non-exportable 256-bit AES key in the Android Keystore, used only for AES-GCM. The key
 * material never enters the app process. No user authentication is required, so the key keeps
 * working after a lock-screen change.
 */
class AndroidKeystoreKeyProvider(private val alias: String) : KeyProvider {

    @Synchronized
    override fun getKey(): SecretKey? = try {
        loadKey()
    } catch (e: UnrecoverableKeyException) {
        null
    }

    @Synchronized
    override fun getOrCreateKey(): SecretKey {
        val existing = try {
            loadKey()
        } catch (e: UnrecoverableKeyException) {
            // The entry exists but can no longer be used: replace it.
            deleteKey()
            null
        }
        return existing ?: generateKey()
    }

    @Synchronized
    override fun deleteKey() {
        val store = openKeyStore()
        if (store.containsAlias(alias)) store.deleteEntry(alias)
    }

    private fun loadKey(): SecretKey? = openKeyStore().getKey(alias, null) as? SecretKey

    private fun generateKey(): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true)
            .build()
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(spec)
        return generator.generateKey()
    }

    private fun openKeyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val KEY_SIZE_BITS = 256
    }
}
