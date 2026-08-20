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
 * **`exportSchema = false`, reluctantly — this is a known gap, tracked as T151.** Exporting the
 * schema is what would make future migrations reviewable and testable (`MigrationTestHelper`
 * reads that JSON), and it was enabled during T018 before being backed out: on Room 2.8.4 here it
 * exports successfully exactly once, then fails *every* subsequent build with
 * `AbstractMethodError: …FieldBundle$$serializer… typeParametersSerializers()` — Room's schema
 * bundles use kotlinx-serialization and the version on the KSP processor classpath is
 * incompatible. Reading back an existing schema is what trips it, which is why a clean build
 * looks fine and the next one doesn't. Worth solving before the first real migration, not by
 * flipping this flag and rediscovering the same failure.
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
    exportSchema = false,
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
