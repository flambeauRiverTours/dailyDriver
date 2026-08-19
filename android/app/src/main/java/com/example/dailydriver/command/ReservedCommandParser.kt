package com.example.dailydriver.command

/** Command token for `:apps`, exposed for ShellViewModel's auto-space-on-completion behavior. */
const val APPS_TOKEN = ":apps"

/** Command token for `:settings`, exposed for the same reason as [APPS_TOKEN]. */
const val SETTINGS_TOKEN = ":settings"

/** Command token for `:note`, exposed for the same reason as [APPS_TOKEN]. */
const val NOTE_TOKEN = ":note"

/** Command token for `:alias`, exposed for the same reason as [APPS_TOKEN]. */
const val ALIAS_TOKEN = ":alias"

/** Command token for `:notif`, exposed for the same reason as [APPS_TOKEN]. */
const val NOTIF_TOKEN = ":notif"

private data class RegisteredCommand(val token: String, val build: (query: String) -> ReservedCommand)

/**
 * The T0 registry — reserved-command tokens tried in order, first match wins, exact-match only
 * (genuinely different from T1's fuzzy matching, T008, which this falls through to when nothing
 * here matches). This is the mechanism T141–T145 plug into: each adds one [RegisteredCommand]
 * entry here and one [ReservedCommand] variant, nothing else needs to change.
 *
 * Replaces the `:apps`-only one-off parser pulled forward at T012 so the list view had something
 * real to wire up to before this registry existed — that one-off is retired, not kept alongside
 * this as a second mechanism.
 */
private val registry = listOf(
    RegisteredCommand(APPS_TOKEN) { query -> ReservedCommand.Apps(query) },
    // Query is discarded — Settings takes none (see its doc comment) — but the builder still
    // takes one so every RegisteredCommand shares the same shape.
    RegisteredCommand(SETTINGS_TOKEN) { ReservedCommand.Settings },
    RegisteredCommand(NOTE_TOKEN) { query -> ReservedCommand.Note(query) },
    RegisteredCommand(ALIAS_TOKEN) { ReservedCommand.Alias },
    RegisteredCommand(NOTIF_TOKEN) { ReservedCommand.Notif },
)

/** Every registered command's token — e.g. for ShellViewModel's auto-space-on-completion behavior. */
val reservedCommandTokens: List<String> = registry.map { it.token }

/**
 * Recognizes a T0 reserved command against raw shell input, or returns null if [input] doesn't
 * match anything registered — the fallthrough point where T1 (T008, not yet built) plugs in.
 * `ShellViewModel`'s placeholder output on submit is that fallthrough's current stand-in.
 */
fun parseReservedCommand(input: String): ReservedCommand? {
    for (entry in registry) {
        val query = matchCommandToken(input, entry.token) ?: continue
        return entry.build(query)
    }
    return null
}

/**
 * True if [input] is exactly [token], or [token] followed by a space and more text — the shape
 * every T0 reserved command shares. Returns the remainder if so (untrimmed at the end — a
 * trailing space is still being typed toward the next word and must survive), or null if [input]
 * doesn't match this token at all.
 */
private fun matchCommandToken(input: String, token: String): String? {
    val tokenWithSpace = "$token "
    return when {
        input == token -> ""
        input.startsWith(tokenWithSpace) -> input.removePrefix(tokenWithSpace).trimStart()
        else -> null
    }
}
