package com.flambeauriver.dailydriver.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "schema-export-test.db"

/**
 * Guards the exported Room schema (T151).
 *
 * The failure this exists to catch is a quiet one. `exportSchema` was off for the whole of T018
 * and T019 because turning it on broke the build; if it ever silently stops exporting again — a
 * lost `ksp.useKSP2=true`, a Room upgrade that reintroduces the classloader conflict — the
 * committed JSON doesn't disappear, it just **stops tracking the entities**. Every later migration
 * would then be written and reviewed against a schema that no longer describes the database, which
 * is worse than having no schema at all.
 *
 * [MigrationTestHelper] creating the database from the exported JSON is what makes that
 * detectable: it validates the schema it reads against the compiled entity definitions, so a stale
 * file fails here rather than at the first real migration.
 *
 * There is no migration to test yet — version 1 is the only version, and T018 deliberately made
 * this a *new* database file rather than a migration of `aliases.db` (see [DailyDriverDatabase]).
 * When a version 2 arrives, its migration test belongs in this file.
 */
@RunWith(AndroidJUnit4::class)
class SchemaExportTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        DailyDriverDatabase::class.java,
    )

    @Test
    fun theExportedSchemaMatchesTheCompiledEntities() {
        // Throws if app/schemas/…/1.json is missing, unreadable, or describes a database that no
        // longer matches the @Entity classes. Closing it is what the next migration test would
        // reopen at a higher version.
        helper.createDatabase(TEST_DB, 1).close()
    }
}
