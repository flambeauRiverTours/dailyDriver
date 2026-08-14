package com.example.dailydriver.apps

/** Parsed `:apps` command — [query] is whatever follows the command token, raw and untrimmed. */
data class AppsCommand(val query: String)

private const val COMMAND = ":apps"
private const val COMMAND_WITH_SPACE = "$COMMAND "

/**
 * Recognizes the `:apps` reserved command against raw shell input. Case-sensitive on the command
 * token itself, matching the rest of T0's reserved commands.
 *
 * This is a one-off, `:apps`-specific stand-in for T140's general command dispatcher/registry,
 * pulled forward so T012's list view has something real to wire up to instead of shipping unwired
 * and unverifiable. T140 should absorb this into its registry rather than leave it duplicated.
 */
fun parseAppsCommand(input: String): AppsCommand? = when {
    input == COMMAND -> AppsCommand("")
    // trimStart only — a trailing space is still being typed toward the next word and must
    // survive, or a query like "chrome brow" would lose its word boundary mid-type.
    input.startsWith(COMMAND_WITH_SPACE) -> AppsCommand(input.removePrefix(COMMAND_WITH_SPACE).trimStart())
    else -> null
}
