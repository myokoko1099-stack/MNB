package com.mnb.browser.translation.db

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Plain JVM guard for the migration list. Version 1 is the first schema, so there is nothing to
 * migrate yet; this fails as soon as a version is added without a matching, gap-free migration.
 */
class MnbMigrationsTest {
    @Test fun migrationsFormAnUnbrokenChainUpToTheCurrentVersion() {
        val sorted = MnbMigrations.ALL.sortedBy { it.startVersion }
        var version = 1
        for (m in sorted) {
            assertEquals("gap or overlap before migration ${m.startVersion}->${m.endVersion}", version, m.startVersion)
            assertEquals("migrations must go up one version at a time", version + 1, m.endVersion)
            version = m.endVersion
        }
        assertEquals(MnbMigrations.CURRENT_VERSION, version)
    }
}
