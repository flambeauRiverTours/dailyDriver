package com.flambeauriver.dailydriver.command

import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Serialising the command log for §8's hand-labelling step (T166).
 *
 * **Pure, and separate from [CommandLogExporter] on purpose.** Where the bytes land is an Android
 * concern that needs a device to exercise; what the bytes *say* is the part that has to be right,
 * and this half is unit-testable.
 *
 * **⚠️ The shape below is a contract with `scripts/export-command-log.sh`**, which produces the
 * same JSONL from a pulled database. Two producers, one consumer (T075's labelling step), so the
 * keys must match exactly — **change one and change the other.** The script's header points back
 * here for the same reason. This is the same arrangement the tag-parsing fixtures have across
 * Kotlin and C#, for the same reason: the seam is where the bug will live.
 */

/** Key names, listed once so the test that pins them can't drift from the code that writes them. */
internal val COMMAND_LOG_KEYS = listOf("input", "resolution", "tier", "count", "firstSeen", "lastSeen")

/**
 * The whole log as JSON Lines: one object per line, trailing newline, in the order given.
 *
 * Built in memory rather than streamed, which is safe *because* the table deduplicates (T014). An
 * undeduplicated log would grow with every keystroke-session and this would be the wrong shape;
 * one row per distinct input keeps it to hundreds of rows after months of daily driving.
 */
fun commandLogJsonl(entries: List<CommandLogEntry>): String = buildString {
    for (entry in entries) {
        append(entry.toJsonLine())
        append('\n')
    }
}

private fun CommandLogEntry.toJsonLine(): String = JSONObject().apply {
    put("input", input)
    // Named "resolution", not "target", and null rather than "" when nothing resolved — both
    // match the script. The rename is deliberate: this records what the deterministic tiers *did*,
    // not a §4.3 intent label, and calling it a target invites a reader to treat it as one.
    put("resolution", if (target.isEmpty()) JSONObject.NULL else target)
    put("tier", tier)
    put("count", count)
    put("firstSeen", firstSeen)
    put("lastSeen", lastSeen)
}.toString()

/**
 * Stamped to the second, so successive exports sort chronologically and never collide.
 *
 * **The time is there for uniqueness, not curiosity.** MediaStore de-duplicates a repeated display
 * name by appending ` (1)` *after* the extension — `…-2026-08-22.jsonl (1)` — which a `*.jsonl`
 * glob then skips, so the second export of a day would quietly go missing from anything collecting
 * them. Making the name unique here means MediaStore never has to.
 */
fun commandLogFileName(at: LocalDateTime): String =
    "dailydriver-command-log-${FILE_STAMP.format(at)}.jsonl"

private val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")

/**
 * What an export contained.
 *
 * [unresolved] is called out separately because it is the number worth watching: those are the T3
 * rows, §8's `unknown` examples, and the ones that cannot be invented convincingly.
 */
data class CommandLogStats(val distinct: Int, val submissions: Int, val unresolved: Int)

fun commandLogStats(entries: List<CommandLogEntry>): CommandLogStats = CommandLogStats(
    distinct = entries.size,
    submissions = entries.sumOf { it.count },
    unresolved = entries.count { it.tier == EscalationTier.T3.name },
)

/**
 * What the transcript says after an export.
 *
 * **Reports the count even when it is zero, and the file is written anyway.** A silent no-op on an
 * empty log would reproduce exactly the failure T166 exists to fix: a route that looks like it
 * worked, telling you nothing, until you find out a week later that it never did. Seeing
 * `0 inputs` still confirms the export path itself is intact.
 */
fun exportedText(stats: CommandLogStats, location: String): String = buildString {
    append("exported ${stats.distinct} ${plural(stats.distinct, "input")}")
    append(" (${stats.submissions} ${plural(stats.submissions, "submission")}")
    append(", ${stats.unresolved} unresolved)")
    append("\n$location")
}

private fun plural(count: Int, noun: String) = if (count == 1) noun else "${noun}s"
