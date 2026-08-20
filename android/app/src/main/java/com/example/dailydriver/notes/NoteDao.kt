package com.example.dailydriver.notes

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
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

    /**
     * Free-text search over title and body (T026), newest first.
     *
     * `LIKE`, not FTS: a personal note store is hundreds of rows, and an FTS table would mean a
     * schema migration — awkward while T151 has schema export broken. Revisit if it gets slow.
     *
     * **`ESCAPE` is load-bearing.** `%` and `_` are `LIKE` wildcards, so without it a search for
     * "50%" matches every note and "snake_case" matches "snakeXcase". Callers must pass the term
     * through [escapeLike]; the pairing is enforced by [NotesRepository], which is the only
     * caller.
     *
     * Case-insensitivity comes from SQLite's `LIKE`, which folds ASCII only — a search for "CAFÉ"
     * won't match "café". Accepted rather than adding `COLLATE` gymnastics for a personal tool.
     */
    @Query(
        """
        SELECT * FROM notes
        WHERE deleted = 0
          AND (title LIKE '%' || :escapedTerm || '%' ESCAPE '\'
            OR body LIKE '%' || :escapedTerm || '%' ESCAPE '\')
        ORDER BY updatedAt DESC
        """,
    )
    abstract fun observeMatching(escapedTerm: String): Flow<List<Note>>

    /**
     * Finds a live note by title for `:note <name>`'s open-or-create (T025).
     *
     * `COLLATE NOCASE` because the user is typing a name from memory, not copying an identifier —
     * `:note Standup` should find the note they called "standup". Tombstones are excluded, so
     * naming a new note after a deleted one creates a fresh one rather than resurrecting it.
     */
    @Query("SELECT * FROM notes WHERE deleted = 0 AND title = :title COLLATE NOCASE LIMIT 1")
    abstract suspend fun byTitle(title: String): Note?

    // --- local mutations: always enqueue ----------------------------------------------------------

    /**
     * Creates or updates a note from local editing, and queues it for sync.
     *
     * **Tags are derived here rather than passed in** (T019). Taking them as a parameter would
     * let a caller supply a set that disagrees with the body, which is exactly the "one source of
     * truth" property §6.2 protects for checklist items — the same argument applies to tags.
     * Deriving them inside the transaction makes it structurally impossible, in the same spirit as
     * the `protected` raw operations below.
     *
     * They're rewritten wholesale rather than diffed: the body is authoritative, so the freshly
     * parsed set already *is* the answer and a diff would only be a slower route to it.
     */
    @Transaction
    open suspend fun upsertLocal(note: Note, queuedAt: Long) {
        insert(note)
        replaceTags(note.id, parseTags(note.body))
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
     *
     * Unlike [upsertLocal], this *does* take tags: they're whatever the server sent. That
     * asymmetry is deliberate and becomes load-bearing if tag assignment moves server-side —
     * locally-authored notes still need tags immediately (§7.1 is local-first, and T026's search
     * would otherwise miss anything unsynced), while synced notes defer to what the server says.
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

    /**
     * `@Upsert`, **not** `@Insert(onConflict = REPLACE)`, and this is not a style preference —
     * the REPLACE version was a real bug, caught by `coalescingKeepsTheBaseRevFromWhenTheNote…`.
     *
     * SQLite implements `INSERT OR REPLACE` as *delete the conflicting row, then insert*. Both
     * `outbox` and `note_tags` hold `ON DELETE CASCADE` foreign keys onto `notes`, so that delete
     * cascaded: every re-save silently destroyed the note's pending outbox row, which was then
     * re-created with a freshly captured [OutboxEntry.baseRev]. The `IGNORE` on [enqueue] was
     * correct and simply unreachable, because there was never a row left to conflict with — so
     * the conflict comparison point §7.2 depends on reset on every debounced autosave, and
     * `applyRemote` would have wiped a pending local edit's queue entry outright.
     *
     * `@Upsert` issues an UPDATE on conflict instead. No delete, no cascade, row identity intact.
     */
    @Upsert
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
