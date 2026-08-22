package com.flambeauriver.dailydriver.notes

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Reads over the derived task index (T157).
 *
 * **Read-only by design.** Writing is [NoteDao]'s job, inside the transaction that writes the note
 * the tasks were derived from — the same arrangement that stops a tag set disagreeing with the
 * body it came from. There is deliberately no insert or update here for a caller to reach for.
 *
 * Every query joins `notes` and filters `deleted = 0`. A soft-deleted note keeps its rows (the
 * cascade only fires on a hard delete), so excluding them has to happen here; the payoff is that
 * undoing a delete brings its tasks back without rebuilding anything.
 */
@Dao
interface TaskDao {

    /**
     * Outstanding tasks, soonest first. This is `:tasks`.
     *
     * Ordering falls back to note and position so the list is stable when several tasks share a
     * date, which is the common case for "today" — otherwise rows would shuffle between emissions
     * for no visible reason.
     */
    @Query(
        """
        SELECT t.noteId AS noteId, n.title AS noteTitle, t.position AS position,
               t.text AS text, t.checked AS checked, t.due AS due
        FROM tasks t
        JOIN notes n ON n.id = t.noteId
        WHERE n.deleted = 0 AND t.checked = 0
        ORDER BY t.due ASC, n.title ASC, t.position ASC
        """,
    )
    fun observeOutstanding(): Flow<List<TaskListItem>>

    /**
     * Completed tasks, most recently due first. This is `:tasksdone`.
     *
     * Newest-first because a finished list is read to check what you got done lately, which is the
     * opposite of the question the outstanding list answers.
     */
    @Query(
        """
        SELECT t.noteId AS noteId, n.title AS noteTitle, t.position AS position,
               t.text AS text, t.checked AS checked, t.due AS due
        FROM tasks t
        JOIN notes n ON n.id = t.noteId
        WHERE n.deleted = 0 AND t.checked = 1
        ORDER BY t.due DESC, n.title ASC, t.position ASC
        """,
    )
    fun observeCompleted(): Flow<List<TaskListItem>>

    /** Everything indexed for one note, for tests and for verifying a rebuild. */
    @Query("SELECT * FROM tasks WHERE noteId = :noteId ORDER BY position")
    suspend fun rowsFor(noteId: String): List<TaskRow>
}
