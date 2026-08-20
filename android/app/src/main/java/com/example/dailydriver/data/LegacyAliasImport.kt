package com.example.dailydriver.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.core.content.contentValuesOf
import androidx.sqlite.db.SupportSQLiteDatabase

/** The T007 database, superseded by [DailyDriverDatabase] at T018. */
private const val LEGACY_DATABASE_NAME = "aliases.db"

/**
 * Copies aliases out of T007's `aliases.db` the first time [DailyDriverDatabase] is created, then
 * deletes the old file.
 *
 * **Why this exists rather than a Room migration:** T018 merges notes into the same database as
 * aliases, and the merged database gets a new filename. Room migrations run *within* one SQLite
 * file — pointing Room at a new name doesn't migrate the old one, it creates an empty database
 * and leaves the old file orphaned. Without this step the rename would silently drop every alias
 * the user had defined.
 *
 * Runs in `onCreate`, so it fires exactly once on the transition and never again: on a fresh
 * install there's no legacy file to find, and after a successful import the file is gone.
 *
 * Failures are swallowed deliberately. This is a convenience carry-over of a handful of
 * device-local, trivially re-creatable rows (§4.7 — aliases are never synced, so there's no
 * remote copy to reconcile with either). Taking the home screen down over a corrupt legacy
 * database would be a far worse outcome than losing them.
 */
fun importLegacyAliases(context: Context, target: SupportSQLiteDatabase) {
    if (!context.getDatabasePath(LEGACY_DATABASE_NAME).exists()) return

    try {
        context.openOrCreateDatabase(LEGACY_DATABASE_NAME, Context.MODE_PRIVATE, null).use { legacy ->
            legacy.rawQuery("SELECT name, command FROM aliases", null).use { cursor ->
                while (cursor.moveToNext()) {
                    target.insert(
                        "aliases",
                        // SQLiteDatabase.CONFLICT_REPLACE, *not* Room's OnConflictStrategy.REPLACE
                        // — SupportSQLiteDatabase takes the platform constants, and the two
                        // enumerations don't share values (1 vs 5), so mixing them up silently
                        // applies the wrong conflict algorithm rather than failing to compile.
                        SQLiteDatabase.CONFLICT_REPLACE,
                        contentValuesOf(
                            "name" to cursor.getString(0),
                            "command" to cursor.getString(1),
                        ),
                    )
                }
            }
        }
    } catch (e: SQLiteException) {
        // Legacy file unreadable or missing its table — nothing worth carrying over.
    }

    context.deleteDatabase(LEGACY_DATABASE_NAME)
}
