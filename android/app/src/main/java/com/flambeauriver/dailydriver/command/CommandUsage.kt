package com.flambeauriver.dailydriver.command

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * How often each thing has been run, for ranking (T183).
 *
 * **A derived index, not a store** — the same shape as `note_tags` and `tasks` (ADR-0007). It
 * holds nothing that `command_log` does not already imply; it exists so ranking is an indexed
 * lookup instead of a `GROUP BY` over the training data on every keystroke, and it is rebuildable
 * from that log at any time.
 *
 * **Keyed on [target], which is a different question from the one `command_log` answers.** The log
 * is keyed `(input, target)` because it records phrasings — `chr`, `chrome` and `chro` are three
 * separate training examples that happen to share an outcome. Ranking wants the opposite: one
 * number for "how often do I end up at Chrome", summed across every way of asking. Keying this on
 * target alone is that sum, maintained incrementally.
 *
 * **Never synced**, for the same reason the log is not: it is a record of what you type at a home
 * screen. It also has no outbox row and no place in §6.2.
 */
@Entity(tableName = "command_usage")
data class CommandUsage(
    /** Matches [CommandResolution.target] and [Candidate.target] — one identity, not a third one. */
    @PrimaryKey val target: String,
    val count: Int,
    val lastUsed: Long,
)

/**
 * The usage index (T183).
 *
 * **No insert or update is exposed** — [record] is the only way in, for the same reason
 * `CommandLogDao` gives: a plain `@Insert` would either fail on the second use or reset the count,
 * and both silently destroy the frequency this table exists to carry.
 */
@Dao
interface CommandUsageDao {

    /**
     * Records one use, incrementing if this target has been run before.
     *
     * `ON CONFLICT DO UPDATE` rather than read-then-write: this fires on the launcher's fast path,
     * and a lost update is a silently wrong ranking rather than a visible error.
     */
    @Query(
        """
        INSERT INTO command_usage (target, count, lastUsed)
        VALUES (:target, 1, :now)
        ON CONFLICT(target) DO UPDATE SET
            count = count + 1,
            lastUsed = :now
        """,
    )
    suspend fun record(target: String, now: Long)

    /**
     * Every count, for the matcher to rank with.
     *
     * Returned whole rather than queried per candidate: the table has one row per distinct thing
     * ever run — tens of rows, not thousands — so a single read beats a lookup per keystroke per
     * candidate, and it keeps `rankCandidates` a pure function over a plain map.
     */
    @Query("SELECT * FROM command_usage")
    suspend fun all(): List<CommandUsage>

    /** Clears the index. Rebuildable from `command_log`, so this is safe rather than destructive. */
    @Query("DELETE FROM command_usage")
    suspend fun clear()
}
