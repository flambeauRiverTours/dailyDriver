package com.flambeauriver.dailydriver.command

/** Gap between the usage column and its description, in a monospace shell. */
private const val COLUMN_GAP = "  "

/**
 * The `:help` / `?` output (T145), and the content T010's T3 fallback points at rather than
 * duplicating.
 *
 * The command list is generated from T140's registry ([reservedCommandHelp]) so it can't drift
 * from what's actually dispatchable. The footer is static because aliases and bare-name fuzzy
 * launching aren't registry entries — they're T0/T1 behaviors with no token to hang a
 * [CommandHelp] off — but they're most of what the launcher does day to day, so a command-only
 * listing would document the smaller half of the app.
 */
fun helpText(): String = buildString {
    append(formatCommandHelp(reservedCommandHelp))
    append("\n\n")
    append("alias <name> = <command>${COLUMN_GAP}define an alias\n")
    append("type an app name to launch it — close matches work")
}

/**
 * Renders `usage  description` rows with the usage column padded to a common width, so
 * descriptions align in the shell's monospace font. Separate from [helpText] so the alignment
 * logic is testable against synthetic entries rather than only against the live registry.
 */
fun formatCommandHelp(entries: List<CommandHelp>): String {
    val width = entries.maxOfOrNull { it.usage.length } ?: return ""
    return entries.joinToString("\n") { it.usage.padEnd(width) + COLUMN_GAP + it.description }
}
