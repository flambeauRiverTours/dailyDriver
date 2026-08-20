package com.example.dailydriver.notes

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Reads and mutations for notes, their tags, and the outbox rows they generate.
 *
 * **An abstract class, not an interface, and the raw operations are `protected` on purpose.**
 * android/CLAUDE.md's rule is "do not add a table that can be mutated outside a transaction that
 * also touches the outbox" — a rule which, written as a comment, survives exactly as long as the
 * next person reads it. Making [insert]/[replaceTags]/[enqueue] unreachable from outside turns it
 * into something the compiler enforces: callers can only reach the `@Transaction` methods below,
 * and each of those already does the right thing.
 */
@Dao
abstract class NoteDao {

    // --- reads ---------------------------------------------------------------------------------

    /** Live list for T025, newest first, tombstones excluded. */
    @Query("SELECT * FROM notes WHERE deleted = 0 ORDER BY updatedAt DESC")
    abstract fun observeAll(): Flow<List<Note>>

    /** Includes tombstones — sync needs to see them, the UI filters them out. */
    @Query("SELECT * FROM notes WHERE id = :id")
    abstract suspend fun byId(id: String): Note?

    @Query("SELECT tag FROM note_tags WHERE noteId = :noteId ORDER BY tag")
    abstract suspend fun tagsOf(noteId: String): List<String>

    /** Exact tag match against the indexed join table — see [NoteTag] for why not `LIKE`. */
    @Query(
        """
        SELECT notes.* FROM notes
        JOIN note_tags ON note_tags.noteId = notes.id
        WHERE note_tags.tag = :tag AND notes.deleted = 0
        ORDER BY notes.updatedAt DESC
        """,
    )
    abstract fun observeByTag(tag: String): Flow<List<Note>>

    // --- local mutations: always enqueue ----------------------------------------------------------

    /**
     * Creates or updates a note from local editing, and queues it for sync. Tags are rewritten
     * wholesale rather than diffed — they're derived from the body (T019), so the incoming set is
     * authoritative by definition and a diff would only be a slower way to reach the same rows.
     */
    @Transaction
    open suspend fun upsertLocal(note: Note, tags: List<String>, queuedAt: Long) {
        insert(note)
        replaceTags(note.id, tags)
        enqueue(OutboxEntry(noteId = note.id, baseRev = note.rev, queuedAt = queuedAt))
    }

    /**
     * Soft-deletes a note and queues the tombstone (§7.1 — deletions propagate, they don't just
     * vanish locally). No-ops on an unknown id rather than queueing an outbox row pointing at a
     * note that doesn't exist, which the foreign key would reject anyway.
     */
    @Transaction
    open suspend fun markDeletedLocal(id: String, updatedAt: Long, queuedAt: Long) {
        val existing = byId(id) ?: return
        setDeleted(id, updatedAt)
        enqueue(OutboxEntry(noteId = id, baseRev = existing.rev, queuedAt = queuedAt))
    }

    // --- remote application: never enqueues --------------------------------------------------------

    /**
     * Applies a note pulled from the server. **Deliberately does not touch the outbox** — this is
     * already the server's state, so queueing it would push it straight back and, worse, would
     * make every pull look like a local edit for conflict purposes.
     */
    @Transaction
    open suspend fun applyRemote(note: Note, tags: List<String>) {
        insert(note)
        replaceTags(note.id, tags)
    }

    /**
     * Records the `rev` the server assigned to a pushed note and drains its outbox row — but
     * **only if the note hasn't changed since the push started**.
     *
     * That guard is what makes coalescing safe. A push reads the note, sends it, and comes back
     * with a rev; if the user edited the note in that window, the outbox row was already there
     * (coalescing means no second row appeared) and clearing it unconditionally would strand the
     * newer edit forever. Comparing `updatedAt` against what was actually sent leaves the row in
     * place instead, so the next cycle picks the edit up.
     */
    @Transaction
    open suspend fun confirmPushed(noteId: String, assignedRev: Long, pushedUpdatedAt: Long) {
        setRev(noteId, assignedRev)
        clearOutboxIfUnchanged(noteId, pushedUpdatedAt)
    }

    // --- raw operations, reachable only through the transactions above -------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insert(note: Note)

    @Query("UPDATE notes SET deleted = 1, updatedAt = :updatedAt WHERE id = :id")
    protected abstract suspend fun setDeleted(id: String, updatedAt: Long)

    @Query("UPDATE notes SET rev = :rev WHERE id = :id")
    protected abstract suspend fun setRev(id: String, rev: Long)

    @Query("DELETE FROM note_tags WHERE noteId = :noteId")
    protected abstract suspend fun clearTags(noteId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertTags(tags: List<NoteTag>)

    /**
     * `IGNORE`, not `REPLACE`, and that choice is load-bearing: it keeps the [OutboxEntry.baseRev]
     * captured when the note *first* became dirty. That's the rev the server must compare against
     * to detect a conflict (§7.2) — overwriting it on every subsequent keystroke-debounced save
     * would keep resetting the comparison point and quietly make conflicts undetectable.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun enqueue(entry: OutboxEntry)

    @Query(
        """
        DELETE FROM outbox
        WHERE noteId = :noteId
          AND (SELECT updatedAt FROM notes WHERE id = :noteId) = :pushedUpdatedAt
        """,
    )
    protected abstract suspend fun clearOutboxIfUnchanged(noteId: String, pushedUpdatedAt: Long)

    protected open suspend fun replaceTags(noteId: String, tags: List<String>) {
        clearTags(noteId)
        insertTags(tags.distinct().map { NoteTag(noteId = noteId, tag = it) })
    }
}
