package com.flambeauriver.dailydriver.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.flambeauriver.dailydriver.command.CommandLogEntry

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
