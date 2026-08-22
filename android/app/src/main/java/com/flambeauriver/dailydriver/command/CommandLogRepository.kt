package com.flambeauriver.dailydriver.command

import android.content.Context
import com.flambeauriver.dailydriver.data.DatabaseProvider

/**
 * The T0/T1/T3 training-data log (T014, §4.2).
 *
 * **Never leaves the device.** No outbox row, no place in §6.2's document model, no C# contract.
 * [CommandLogEntry.input] is everything typed at the home screen, so this is a stronger version of
 * the exclusion aliases already have (T097): aliases don't sync because syncing them is useless,
 * this doesn't because syncing it would be wrong.
 */
class CommandLogRepository(context: Context) {

    private val dao = DatabaseProvider.get(context).commandLogDao()

    /**
     * Records one resolution.
     *
     * Failures are swallowed deliberately. This sits on the path of every command, and a launcher
     * that refused to open an app because its training log could not be written would have its
     * priorities backwards — the log is a by-product, and the launcher is the product.
     */
    suspend fun record(input: String, resolution: CommandResolution, now: Long) {
        runCatching {
            dao.record(
                input = input,
                target = resolution.target,
                tier = resolution.tier.name,
                now = now,
            )
        }
    }

    /** The export §8 hand-labels, most-typed first. */
    suspend fun all(): List<CommandLogEntry> = dao.all()

    /** What nothing resolved: §8's out-of-scope examples, collected rather than invented. */
    suspend fun unresolved(): List<CommandLogEntry> = dao.unresolved()

    suspend fun clear() = dao.clear()
}
