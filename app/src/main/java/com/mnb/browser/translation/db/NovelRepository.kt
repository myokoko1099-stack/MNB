package com.mnb.browser.translation.db

import com.mnb.browser.translation.NovelId
import com.mnb.browser.translation.NovelTranslationInfo

/** Stores novels. Blocking: call off the main thread. */
class NovelRepository(db: MnbDatabase) {
    private val dao = db.novelDao()

    /** Inserts the novel, or updates it in place when the id already exists (children are kept). */
    fun save(info: NovelTranslationInfo) = dao.upsert(info.toEntity())

    fun get(novelId: NovelId): NovelTranslationInfo? = dao.get(novelId.value)?.toModel()

    /** Oldest first. Rows with an unsupported stored language are skipped. */
    fun getAll(): List<NovelTranslationInfo> = dao.getAll().mapNotNull { it.toModel() }

    /** Deletes the novel and, by cascade, all its chapters, translations, chunks and jobs. */
    fun delete(novelId: NovelId): Boolean = dao.delete(novelId.value) > 0
}
