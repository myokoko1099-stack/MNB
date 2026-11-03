package com.mnb.browser.translation.db

/**
 * The statements of migration 1 -> 2: they only ADD the Context Memory tables, so every table and
 * row of version 1 is left untouched. The SQL must match what Room generates for the entities in
 * MemoryEntities.kt (names, types, nullability, foreign keys, indices); Room refuses to open a
 * migrated database that differs, and MemoryMigrationTest covers that.
 */
internal object MemorySchemaV2 {
    private const val FK_NOVEL =
        "FOREIGN KEY(`novelId`) REFERENCES `novels`(`novelId`) ON UPDATE NO ACTION ON DELETE CASCADE"

    private const val FK_CHARACTER =
        "FOREIGN KEY(`characterId`) REFERENCES `memory_characters`(`characterId`) ON UPDATE NO ACTION ON DELETE CASCADE"

    private const val FK_FROM =
        "FOREIGN KEY(`fromCharacterId`) REFERENCES `memory_characters`(`characterId`) ON UPDATE NO ACTION ON DELETE CASCADE"

    private const val FK_TO =
        "FOREIGN KEY(`toCharacterId`) REFERENCES `memory_characters`(`characterId`) ON UPDATE NO ACTION ON DELETE CASCADE"

    val STATEMENTS: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `memory_characters` (`characterId` TEXT NOT NULL, `novelId` TEXT NOT NULL, " +
            "`originalName` TEXT NOT NULL, `myanmarName` TEXT, `gender` TEXT, `personality` TEXT NOT NULL, " +
            "`role` TEXT NOT NULL, `speakingStyle` TEXT NOT NULL, `pronouns` TEXT NOT NULL, " +
            "`firstAppearanceChapter` INTEGER, `notes` TEXT NOT NULL, `status` TEXT NOT NULL, " +
            "`sourceChapter` INTEGER, `createdAtMillis` INTEGER NOT NULL, `updatedAtMillis` INTEGER NOT NULL, " +
            "PRIMARY KEY(`characterId`), $FK_NOVEL)",
        "CREATE INDEX IF NOT EXISTS `index_memory_characters_novelId` ON `memory_characters` (`novelId`)",

        "CREATE TABLE IF NOT EXISTS `memory_aliases` (`aliasId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`characterId` TEXT NOT NULL, `novelId` TEXT NOT NULL, `alias` TEXT NOT NULL, " +
            "`normalizedAlias` TEXT NOT NULL, $FK_CHARACTER, $FK_NOVEL)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_memory_aliases_novelId_normalizedAlias` " +
            "ON `memory_aliases` (`novelId`, `normalizedAlias`)",
        "CREATE INDEX IF NOT EXISTS `index_memory_aliases_characterId` ON `memory_aliases` (`characterId`)",

        "CREATE TABLE IF NOT EXISTS `memory_glossary` (`termId` TEXT NOT NULL, `novelId` TEXT NOT NULL, " +
            "`sourceTerm` TEXT NOT NULL, `normalizedTerm` TEXT NOT NULL, `translatedTerm` TEXT NOT NULL, " +
            "`category` TEXT NOT NULL, `status` TEXT NOT NULL, `sourceChapter` INTEGER, " +
            "`createdAtMillis` INTEGER NOT NULL, `updatedAtMillis` INTEGER NOT NULL, " +
            "PRIMARY KEY(`termId`), $FK_NOVEL)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_memory_glossary_novelId_normalizedTerm` " +
            "ON `memory_glossary` (`novelId`, `normalizedTerm`)",

        "CREATE TABLE IF NOT EXISTS `memory_relationships` (`relationshipId` TEXT NOT NULL, `novelId` TEXT NOT NULL, " +
            "`fromCharacterId` TEXT NOT NULL, `toCharacterId` TEXT NOT NULL, `type` TEXT NOT NULL, " +
            "`description` TEXT NOT NULL, `status` TEXT NOT NULL, `startChapter` INTEGER, `endChapter` INTEGER, " +
            "`isCurrent` INTEGER NOT NULL, `createdAtMillis` INTEGER NOT NULL, " +
            "PRIMARY KEY(`relationshipId`), $FK_NOVEL, $FK_FROM, $FK_TO)",
        "CREATE INDEX IF NOT EXISTS `index_memory_relationships_novelId` ON `memory_relationships` (`novelId`)",
        "CREATE INDEX IF NOT EXISTS `index_memory_relationships_fromCharacterId` " +
            "ON `memory_relationships` (`fromCharacterId`)",
        "CREATE INDEX IF NOT EXISTS `index_memory_relationships_toCharacterId` " +
            "ON `memory_relationships` (`toCharacterId`)",

        "CREATE TABLE IF NOT EXISTS `memory_story` (`entryId` TEXT NOT NULL, `novelId` TEXT NOT NULL, " +
            "`kind` TEXT NOT NULL, `summary` TEXT NOT NULL, `normalizedSummary` TEXT NOT NULL, " +
            "`sourceChapter` INTEGER NOT NULL, `characterIds` TEXT NOT NULL, `status` TEXT NOT NULL, " +
            "`resolved` INTEGER NOT NULL, `createdAtMillis` INTEGER NOT NULL, " +
            "PRIMARY KEY(`entryId`), $FK_NOVEL)",
        "CREATE INDEX IF NOT EXISTS `index_memory_story_novelId_sourceChapter` " +
            "ON `memory_story` (`novelId`, `sourceChapter`)",

        "CREATE TABLE IF NOT EXISTS `memory_world` (`entryId` TEXT NOT NULL, `novelId` TEXT NOT NULL, " +
            "`kind` TEXT NOT NULL, `name` TEXT NOT NULL, `normalizedName` TEXT NOT NULL, " +
            "`description` TEXT NOT NULL, `status` TEXT NOT NULL, `sourceChapter` INTEGER, " +
            "`createdAtMillis` INTEGER NOT NULL, `updatedAtMillis` INTEGER NOT NULL, " +
            "PRIMARY KEY(`entryId`), $FK_NOVEL)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_memory_world_novelId_kind_normalizedName` " +
            "ON `memory_world` (`novelId`, `kind`, `normalizedName`)",

        "CREATE TABLE IF NOT EXISTS `memory_style` (`novelId` TEXT NOT NULL, `writingStyle` TEXT NOT NULL, " +
            "`tone` TEXT NOT NULL, `dialogueStyle` TEXT NOT NULL, `pronounPreferences` TEXT NOT NULL, " +
            "`namingConventions` TEXT NOT NULL, `translationPreferences` TEXT NOT NULL, " +
            "`updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`novelId`), $FK_NOVEL)",

        "CREATE TABLE IF NOT EXISTS `memory_conflicts` (`conflictId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`novelId` TEXT NOT NULL, `memoryType` TEXT NOT NULL, `memoryId` TEXT NOT NULL, " +
            "`fieldName` TEXT NOT NULL, `existingValue` TEXT NOT NULL, `proposedValue` TEXT NOT NULL, " +
            "`proposedSourceChapter` INTEGER, `status` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL, " +
            "`resolvedAtMillis` INTEGER, $FK_NOVEL)",
        "CREATE INDEX IF NOT EXISTS `index_memory_conflicts_novelId_status` ON `memory_conflicts` (`novelId`, `status`)",

        "CREATE TABLE IF NOT EXISTS `memory_history` (`historyId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`novelId` TEXT NOT NULL, `memoryType` TEXT NOT NULL, `memoryId` TEXT NOT NULL, " +
            "`action` TEXT NOT NULL, `oldValue` TEXT, `newValue` TEXT, `sourceChapter` INTEGER, " +
            "`atMillis` INTEGER NOT NULL, $FK_NOVEL)",
        "CREATE INDEX IF NOT EXISTS `index_memory_history_novelId_memoryType` " +
            "ON `memory_history` (`novelId`, `memoryType`)",
    )
}
