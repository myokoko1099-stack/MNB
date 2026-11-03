package com.mnb.browser.translation.memory

import com.mnb.browser.translation.NovelId
import com.mnb.browser.translation.db.DbTestBase
import com.mnb.browser.translation.db.NovelRepository
import org.junit.Before

/** Real in-memory SQLite (Robolectric) with two stored novels, so isolation can be tested. */
abstract class MemoryTestBase : DbTestBase() {
    protected val n1 = NovelId("n1")
    protected val n2 = NovelId("n2")
    protected lateinit var repo: MemoryRepository
    private var counter = 0

    @Before fun setUpMemory() {
        NovelRepository(db).save(novelInfo("n1"))
        NovelRepository(db).save(novelInfo("n2"))
        counter = 0
        repo = MemoryRepository(db, newId = { "id${++counter}" }, clock = { 5000L })
    }

    protected fun created(result: CharacterSaveResult): CharacterMemory =
        (result as CharacterSaveResult.Created).character

    protected fun confirmedCharacter(
        novel: NovelId,
        name: String,
        myanmar: String? = null,
        aliases: List<String> = emptyList(),
        chapter: Int? = 1,
    ): CharacterMemory = created(
        repo.saveCharacter(
            novel,
            CharacterDraft(
                originalName = name,
                myanmarName = myanmar,
                aliases = aliases,
                status = MemoryStatus.CONFIRMED,
                sourceChapter = chapter,
            ),
        ),
    )
}
