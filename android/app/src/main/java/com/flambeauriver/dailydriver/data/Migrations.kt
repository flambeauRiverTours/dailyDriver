package com.flambeauriver.dailydriver.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.flambeauriver.dailydriver.command.CommandLogEntry
import com.flambeauriver.dailydriver.command.CommandUsage

/**
 * 1 → 2: adds the derived `tasks` index (T157, ADR-0007).
 *
 * **Creates the table empty and does not backfill it**, which is the interesting decision here.
 *
 * Backfilling would mean parsing every note body in SQL, which is not something SQL can do — the
 * checklist and due-date grammars live in Kotlin and are pinned by a shared fixture corpus. The
 * alternative, running the Kotlin derivation over every note inside the migration, would put
 * arbitrary application code in the one place that must never fail: a migration that throws leaves
 * a database no version can open.
 *
 * Leaving it empty is safe because the index holds nothing the bodies don't already say. Every
 * note write refills its own rows through [com.flambeauriver.dailydriver.notes.NoteDao], so the index
 * converges as notes are touched, and `NotesRepository.rebuildTaskIndex()` fills it in one pass
 * without being load-bearing for the upgrade itself.
 *
 * The DDL is written by hand to match what Room generates for `TaskRow`. `exportSchema` is on
 * (T151) and `MigrationTestHelper` compares the migrated database against the exported schema, so
 * a mismatch between this and the entity fails a test rather than surfacing as a corrupt database
 * on someone's phone.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `tasks` (
                `noteId` TEXT NOT NULL,
                `position` INTEGER NOT NULL,
                `text` TEXT NOT NULL,
                `checked` INTEGER NOT NULL,
                `due` TEXT NOT NULL,
                PRIMARY KEY(`noteId`, `position`),
                FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_due` ON `tasks` (`due`)")
    }
}

/**
 * 2 → 3: adds the `command_log` training-data table (T014).
 *
 * Nothing to backfill: the log records what happens from now on, and inventing history for it
 * would be inventing training data, which is exactly the opposite of why it exists.
 *
 * As with 1 → 2, the DDL is hand-written to match what Room generates for [CommandLogEntry], and
 * `MigrationTestHelper` compares the result against the exported schema, so drift fails a test
 * rather than a device.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `command_log` (
                `input` TEXT NOT NULL,
                `target` TEXT NOT NULL,
                `tier` TEXT NOT NULL,
                `count` INTEGER NOT NULL,
                `firstSeen` INTEGER NOT NULL,
                `lastSeen` INTEGER NOT NULL,
                PRIMARY KEY(`input`, `target`)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_command_log_tier` ON `command_log` (`tier`)")
    }
}

/**
 * 3 → 4: adds the derived `command_usage` index (T183).
 *
 * **Created empty and not backfilled**, though unlike 1 → 2 it easily could be — `command_log`
 * already holds every resolution ever recorded, and one `INSERT ... SELECT target, SUM(count)`
 * would seed it exactly.
 *
 * It is left empty anyway, for the reason `MIGRATION_1_2` gives: a migration is the one place that
 * must never fail, and the less it does the less there is to fail. The index holds nothing the log
 * does not already imply, it refills from ordinary use within a day of daily-driving, and
 * `CommandUsageDao.clear()` plus a rebuild is available if it ever needs seeding deliberately.
 * Ranking with no data is not broken — it falls back to the coverage order that shipped before
 * T182, which is exactly the old behaviour.
 *
 * DDL hand-written to match what Room generates for [CommandUsage]; `exportSchema` is on and
 * `MigrationTestHelper` compares the two, so drift fails a test rather than a device.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `command_usage` (
                `target` TEXT NOT NULL,
                `count` INTEGER NOT NULL,
                `lastUsed` INTEGER NOT NULL,
                PRIMARY KEY(`target`)
            )
            """.trimIndent(),
        )
    }
}
