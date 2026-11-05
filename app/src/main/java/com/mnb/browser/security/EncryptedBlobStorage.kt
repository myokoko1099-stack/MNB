package com.mnb.browser.security

/**
 * One slot of local storage for an already-encrypted value. It never sees plain text. Writes report
 * whether they really reached the disk, so a failed save is never mistaken for a successful one.
 */
interface EncryptedBlobStorage {
    fun exists(): Boolean

    /**
     * The stored bytes, or null when nothing is stored.
     * @throws StoredBlobCorruptedException when something is stored but cannot be read back.
     */
    fun read(): ByteArray?

    /** True only when the value was durably written; on false the previous value is unchanged. */
    fun write(blob: ByteArray): Boolean

    /** True when the slot is now empty (including when it already was). */
    fun clear(): Boolean
}

/** Thrown by [EncryptedBlobStorage.read] when the stored text is not valid. Carries no data. */
class StoredBlobCorruptedException(cause: Throwable? = null) : Exception("stored blob corrupted", cause)
