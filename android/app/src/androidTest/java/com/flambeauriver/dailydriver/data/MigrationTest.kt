package com.flambeauriver.dailydriver.data

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
    fun migrate3To4AddsTheUsageIndexAndValidatesAgainstTheExportedSchema() {
        helper.createDatabase(TEST_DB_4, 3).close()

        val migrated = helper.runMigrationsAndValidate(TEST_DB_4, 4, true, MIGRATION_3_4)

        // Created empty, though unlike 2 → 3 it could have been seeded from command_log with one
        // INSERT ... SELECT. Left empty on purpose: a migration is the one place that must never
        // fail, ranking with no data simply falls back to the pre-T182 coverage order, and the
        // index refills from ordinary use.
        migrated.query("SELECT COUNT(*) FROM command_usage").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.close()
    }

    @Test
    fun migrate3To4PreservesTheCommandLogItDoesNotRead() {
        // ⚠️ The usage index is derived from the same events the log records, so the obvious
        // mistake is to "move" the data. It must not: the log is training data with its own
        // per-phrasing rows, and the index is a separate per-target sum. Both survive.
        helper.createDatabase(TEST_DB_5, 3).apply {
            execSQL(
                "INSERT INTO command_log (input, target, tier, count, firstSeen, lastSeen) " +
                    "VALUES ('chr', 'com.android.chrome', 'T1', 7, 1, 2)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB_5, 4, true, MIGRATION_3_4)

        migrated.query("SELECT count FROM command_log WHERE input = 'chr'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(7, cursor.getInt(0))
        }
        migrated.close()
    }

    @Test
    fun migratingAllTheWayFrom1To4Works() {
        // The upgrade an existing install actually performs. Running the migrations individually
        // proves each step; running them in sequence proves they compose.
        helper.createDatabase(TEST_DB_3, 1).close()

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB_3, 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
        )

        migrated.query("SELECT COUNT(*) FROM tasks").use { assertTrue(it.moveToFirst()) }
        migrated.query("SELECT COUNT(*) FROM command_log").use { assertTrue(it.moveToFirst()) }
        migrated.query("SELECT COUNT(*) FROM command_usage").use { assertTrue(it.moveToFirst()) }
        migrated.close()
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
        const val TEST_DB_2 = "migration-test-2.db"
        const val TEST_DB_3 = "migration-test-3.db"
        const val TEST_DB_4 = "migration-test-4.db"
        const val TEST_DB_5 = "migration-test-5.db"
    }
}
