package com.example.dailydriver.command

import androidx.room.Entity
import androidx.room.Index

/**
 * One distinct thing typed at the prompt, and what the deterministic tiers did with it (T014).
 *
 * **This is the fine-tuning set's real-usage seed** (§4.2, §8). Synthetic examples cover the
 * phrasings you think of; this covers the ones you actually type, which is the half that cannot be
 * invented convincingly.
 *
 * **Deduplicated, with a count** (design check-in, 2026-08-21). One row per distinct
 * (input, target) pair rather than one per keystroke-session. Two reasons, and the second is the
 * important one: months of daily driving would otherwise grow without bound, and a fine-tune on an
 * undeduplicated log would be dominated by whatever app you open most — hundreds of identical
 * "spotify" examples teaching the model mostly that. A count keeps the frequency signal as a
 * number, where a training pipeline can weight it deliberately instead of inheriting it.
 *
 * **Never synced, and this is a hard rule.** [input] is literally everything typed at the home
 * screen. It is deliberately not in the outbox and has no place in §6.2's document model; the
 * exclusion is the same one aliases have (T097), for a stronger reason.
 *
 * Casing is preserved. What someone typed is the training input, and normalising it here would
 * throw away a variation the model has to handle at inference.
 */
@Entity(
    tableName = "command_log",
    primaryKeys = ["input", "target"],
    // "what did T3 fail to resolve" is the query that produces §8's `unknown` examples, so it is
    // the one that earns an index.
    indices = [Index(value = ["tier"])],
)
data class CommandLogEntry(
    val input: String,
    val target: String,
    val tier: String,
    val count: Int,
    val firstSeen: Long,
    val lastSeen: Long,
)
