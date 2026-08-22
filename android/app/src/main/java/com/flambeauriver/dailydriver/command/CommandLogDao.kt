package com.flambeauriver.dailydriver.command

import androidx.room.Dao
import androidx.room.Query

/**
 * The command log (T014).
 *
 * **No insert or update is exposed** — [record] is the only way in, and it is an upsert that
 * increments rather than replaces. A plain `@Insert` would either fail on the second occurrence or
 * reset the count, and both would silently lose the frequency this table exists to carry.
 */
@Dao
interface CommandLogDao {

    /**
     * Records one resolution, incrementing the count if this input has resolved this way before.
     *
     * `ON CONFLICT DO UPDATE` rather than read-then-write: the increment has to be atomic, since
     * this fires on every command and a lost update is a silently wrong training weight.
     * `firstSeen` is left alone on conflict, so the row keeps when the phrasing first appeared.
     */
    @Query(
        """
        INSERT INTO command_log (input, target, tier, count, firstSeen, lastSeen)
        VALUES (:input, :target, :tier, 1, :now, :now)
        ON CONFLICT(input, target) DO UPDATE SET
            count = count + 1,
            lastSeen = :now,
            tier = :tier
        """,
    )
    suspend fun record(input: String, target: String, tier: String, now: Long)

    /** Everything collected, most-typed first — the export T075 hand-labels. */
    @Query("SELECT * FROM command_log ORDER BY count DESC, lastSeen DESC")
    suspend fun all(): List<CommandLogEntry>

    /** What the deterministic tiers could not answer: §8's out-of-scope, abstention examples. */
    @Query("SELECT * FROM command_log WHERE tier = 'T3' ORDER BY count DESC")
    suspend fun unresolved(): List<CommandLogEntry>

    /** Clears the log. Everything typed at a home screen is in here; erasing it must be possible. */
    @Query("DELETE FROM command_log")
    suspend fun clear()
}
