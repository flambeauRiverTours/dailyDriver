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
import com.example.dailydriver.notes.TaskDao
import com.example.dailydriver.notes.TaskRow

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
 * **`exportSchema = true` (T151).** The exported JSON under `app/schemas/` is what makes future
 * migrations reviewable in a diff and testable via `MigrationTestHelper`. T018 enabled this and
 * had to back it out — it exported once and then failed *every* subsequent build with
 * `AbstractMethodError: …FieldBundle$$serializer… typeParametersSerializers()`. The cause turned
 * out to be the KSP processor's classloader rather than Room or a bad dependency version: KSP1
 * loads processors into the Kotlin compiler's own classloader, where the compiler-embedded
 * kotlinx-serialization shadows the newer one Room 2.8.4 compiles its schema-bundle serializers
 * against. Reading an existing schema back is the path that touches those serializers, which is
 * why a clean build looked fine and the next one didn't. Switching to KSP2 (`ksp.useKSP2=true`)
 * gives the processor its own classloader and resolves it — see the note in `gradle.properties`.
 *
 * **If that error ever reappears, do not flip this back to `false`**: it means the KSP2 flag was
 * lost, and that flag is the thing to restore.
 *
 * Version 1 was a *new* database file, not a migration of `aliases.db`. Room migrations operate
 * within one file, so the old aliases are carried across by [LegacyAliasImport] instead.
 *
 * **Version 2 adds the derived `tasks` index (T157, ADR-0007).** It holds no information the note
 * bodies don't already carry, which is what makes [MIGRATION_1_2] able to create it empty and let
 * [NoteDao] refill it as notes are written. See the migration for why it is not rebuilt eagerly.
 */
@Database(
    entities = [
        Alias::class,
        Note::class,
        NoteTag::class,
        OutboxEntry::class,
        SyncState::class,
        TaskRow::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class DailyDriverDatabase : RoomDatabase() {
    abstract fun aliasDao(): AliasDao
    abstract fun noteDao(): NoteDao
    abstract fun outboxDao(): OutboxDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun taskDao(): TaskDao

    companion object {
        const val NAME = "dailydriver.db"
    }
}
