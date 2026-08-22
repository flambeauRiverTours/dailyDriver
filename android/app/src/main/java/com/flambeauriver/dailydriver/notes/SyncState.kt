package com.flambeauriver.dailydriver.notes

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The pull watermark from design doc §7.1's sync cycle — "push outbox → pull `?since={rev}` →
 * apply → advance watermark". [lastPulledRev] is the `since` value for the next pull.
 *
 * A single-row table rather than DataStore or SharedPreferences, specifically so advancing the
 * watermark can happen in the *same transaction* as applying the pulled notes. Split across two
 * stores, a crash between "applied the notes" and "saved the watermark" would re-pull and
 * re-apply them, or worse, skip them.
 *
 * Zero means "never pulled anything" — the server's `rev` sequence starts above it, so a first
 * sync asks for everything.
 */
@Entity(tableName = "sync_state")
data class SyncState(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val lastPulledRev: Long = 0L,
) {
    companion object {
        /** There is only ever one row; the fixed key is what enforces that. */
        const val SINGLETON_ID = 0
    }
}
