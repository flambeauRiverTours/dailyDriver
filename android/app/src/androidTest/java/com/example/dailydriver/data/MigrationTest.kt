package com.example.dailydriver.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The 1 → 2 migration against real SQLite (T157).
 *
 * This is the project's first real migration, and the reason `exportSchema` had to be working
 * first (T151). [MigrationTestHelper] validates the migrated database against the exported schema,
 * so hand-written DDL that drifts from the `TaskRow` entity fails here rather than on a device.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        DailyDriverDatabase::class.java,
    )

    @Test
    fun migrate1To2AddsTheTaskIndexAndValidatesAgainstTheExportedSchema() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO notes (id, type, title, body, createdAt, updatedAt, deleted, rev, sourceDevice)
                VALUES ('n1', 'CHECKLIST', 'tasks', '- [ ] milk @2026-08-25', 1, 1, 0, NULL, 'test')
                """.trimIndent(),
            )
        }

        // validateDroppedTables/validateMigration: helper throws if the resulting schema does not
        // match 2.json exactly.
        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        migrated.query("SELECT COUNT(*) FROM tasks").use { cursor ->
            assertTrue(cursor.moveToFirst())
            // Created empty on purpose: the migration cannot parse Markdown, and running Kotlin
            // inside a migration risks leaving a database no version can open. The index refills
            // as notes are written, or in one pass via rebuildAllTasks().
            assertEquals(0, cursor.getInt(0))
        }

        // The pre-existing note survived the upgrade, which is the part that actually matters to
        // someone with unsynced notes on their phone.
        migrated.query("SELECT body FROM notes WHERE id = 'n1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("- [ ] milk @2026-08-25", cursor.getString(0))
        }
        migrated.close()
    }

    @Test
    fun migrate2To3AddsTheCommandLogAndValidatesAgainstTheExportedSchema() {
        helper.createDatabase(TEST_DB_2, 2).close()

        val migrated = helper.runMigrationsAndValidate(TEST_DB_2, 3, true, MIGRATION_2_3)

        // Created empty, and deliberately not backfilled: the log records what happens from now
        // on, and inventing history for it would be inventing training data.
        migrated.query("SELECT COUNT(*) FROM command_log").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.close()
    }

    @Test
    fun migratingAllTheWayFrom1To3Works() {
        // The upgrade an existing install actually performs. Running the migrations individually
        // proves each step; running them in sequence proves they compose.
        helper.createDatabase(TEST_DB_3, 1).close()

        val migrated = helper.runMigrationsAndValidate(TEST_DB_3, 3, true, MIGRATION_1_2, MIGRATION_2_3)

        migrated.query("SELECT COUNT(*) FROM tasks").use { assertTrue(it.moveToFirst()) }
        migrated.query("SELECT COUNT(*) FROM command_log").use { assertTrue(it.moveToFirst()) }
        migrated.close()
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
        const val TEST_DB_2 = "migration-test-2.db"
        const val TEST_DB_3 = "migration-test-3.db"
    }
}
