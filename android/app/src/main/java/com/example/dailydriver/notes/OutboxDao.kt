package com.example.dailydriver.notes

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Read access to the pending-sync queue. Writes deliberately aren't here — outbox rows are only
 * ever created or drained inside [NoteDao]'s transactions, alongside the note change that caused
 * them (§7.1). A DAO that could enqueue on its own would be a way to break that pairing.
 *
 * The sync algorithm that consumes this is T069.
 */
@Dao
interface OutboxDao {

    /** Oldest first, so a backlog pushes in the order the edits actually happened. */
    @Query("SELECT * FROM outbox ORDER BY queuedAt")
    suspend fun pending(): List<OutboxEntry>

    /**
     * Joined against the notes themselves, since pushing needs the current state rather than the
     * queue row — the outbox stores a pointer, not a payload snapshot.
     */
    @Query(
        """
        SELECT notes.* FROM notes
        JOIN outbox ON outbox.noteId = notes.id
        ORDER BY outbox.queuedAt
        """,
    )
    suspend fun pendingNotes(): List<Note>

    /** For a sync-status affordance — the desktop client has one (T062); Android may want it too. */
    @Query("SELECT COUNT(*) FROM outbox")
    fun observePendingCount(): Flow<Int>
}
