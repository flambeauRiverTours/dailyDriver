package com.example.dailydriver.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.dailydriver.alias.Alias
import com.example.dailydriver.alias.AliasDao
import com.example.dailydriver.notes.Note
import com.example.dailydriver.notes.NoteDao
import com.example.dailydriver.notes.NoteTag
import com.example.dailydriver.notes.NoteType
import com.example.dailydriver.notes.OutboxDao
import com.example.dailydriver.notes.OutboxEntry
import com.example.dailydriver.notes.SyncState
import com.example.dailydriver.notes.SyncStateDao

/** Room has no built-in enum mapping; [NoteType] is stored by name so the column stays readable. */
class Converters {
    @TypeConverter
    fun toNoteType(value: String): NoteType = NoteType.valueOf(value)

    @TypeConverter
    fun fromNoteType(type: NoteType): String = type.name
}

/**
 * The app's single Room database (T018), replacing T007's aliases-only one.
 *
 * **One database, not two** (design check-in, 2026-08-19). Notes and the outbox *have* to share
 * one, because §7.1's guarantee is that a note change and its outbox row commit in the same
 * transaction — and a transaction can't span two SQLite files. Aliases don't need to be here, but
 * putting them anywhere else buys a second connection pool and a second migration history in
 * exchange for nothing.
 *
 * **`exportSchema = true`, unlike T007.** The schema is about to become load-bearing: notes will
 * evolve, and every future change needs a real migration rather than a destructive rebuild. The
 * exported JSON in `app/schemas/` is what makes those migrations reviewable and testable
 * (`room-testing`'s `MigrationTestHelper` reads it), so it's committed alongside the code.
 *
 * Still version 1: this is a *new* database file, not a migration of `aliases.db`. Room
 * migrations operate within one file, so the old aliases are carried across by
 * [LegacyAliasImport] instead.
 */
@Database(
    entities = [
        Alias::class,
        Note::class,
        NoteTag::class,
        OutboxEntry::class,
        SyncState::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class DailyDriverDatabase : RoomDatabase() {
    abstract fun aliasDao(): AliasDao
    abstract fun noteDao(): NoteDao
    abstract fun outboxDao(): OutboxDao
    abstract fun syncStateDao(): SyncStateDao

    companion object {
        const val NAME = "dailydriver.db"
    }
}
