package com.example.dailydriver.notes

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * A note with local changes the server hasn't accepted yet. Design doc §7.1: every local
 * mutation writes one of these *in the same transaction* as the note itself, which is what makes
 * an interrupted sync safe to resume — there is no window where a note is changed but unqueued.
 *
 * **Keyed by [noteId], so it coalesces** (design check-in, 2026-08-19). One pending row per
 * note, not one per mutation. §4.6's editor autosaves on a debounce, so a note edited for a few
 * minutes offline would otherwise queue hundreds of rows that all push the same final state,
 * straight into the fixed free-tier RU/s budget §6.3 exists to protect. Push reads the note's
 * current state, so nothing is duplicated here either.
 *
 * **No operation column.** With coalescing, create/update/delete is derivable from the note
 * itself: `rev == null` means the server has never seen it (create), `deleted` means a tombstone
 * to propagate (delete), otherwise it's an update. A stored operation type would be a second
 * source of truth that could disagree with the note — the exact thing §6.2 avoids for checklist
 * items.
 */
@Entity(
    tableName = "outbox",
    foreignKeys = [
        ForeignKey(
            entity = Note::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class OutboxEntry(
    @PrimaryKey val noteId: String,
    /**
     * The [Note.rev] this pending edit was made against, or null if the note has never synced.
     * §7.2: the server compares this to its current `rev` and, when it's stale, accepts the newer
     * write and returns a conflict marker so the client can materialise the loser as a conflict
     * copy. Captured when the row is first queued and deliberately *not* refreshed by subsequent
     * edits — see `NoteDao.enqueue`.
     */
    val baseRev: Long?,
    val queuedAt: Long,
)
