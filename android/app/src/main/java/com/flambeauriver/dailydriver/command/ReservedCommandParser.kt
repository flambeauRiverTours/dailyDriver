package com.flambeauriver.dailydriver.command

/** Command token for `:apps`, exposed for ShellViewModel's auto-space-on-completion behavior. */
const val APPS_TOKEN = ":apps"

/** Command token for `:settings`, exposed for the same reason as [APPS_TOKEN]. */
const val SETTINGS_TOKEN = ":settings"

/** Command token for `:note`, exposed for the same reason as [APPS_TOKEN]. */
const val NOTE_TOKEN = ":note"

/**
 * Command token for `:notes`, exposed for the same reason as [APPS_TOKEN].
 *
 * **The first token that is a strict prefix of another** ([NOTE_TOKEN] plus an `s`). That's safe
 * because `matchCommandToken` requires an exact match or token-then-*space*, so `:notes` never
 * satisfies `:note` — but it means registry order genuinely doesn't matter here, and there's a
 * test pinning that rather than leaving it to luck.
 */
const val NOTES_TOKEN = ":notes"

const val TASK_TOKEN = ":task"

const val TASKS_TOKEN = ":tasks"

/**
 * Lowercase, unlike the `:tasksDone` this was asked for.
 *
 * Every other token here is lowercase, and matching is case-sensitive, so a capital would mean a
 * shift-tap in the middle of a command on a phone keyboard. [TASKS_DONE_CAMEL] is registered as a
 * hidden alias so the camelCase spelling still works, the same arrangement `?` has with `:help`.
 */
const val TASKS_DONE_TOKEN = ":tasksdone"

const val TASKS_DONE_CAMEL = ":tasksDone"

/** Command token for `:alias`, exposed for the same reason as [APPS_TOKEN]. */
const val ALIAS_TOKEN = ":alias"

/** Command token for `:notif`, exposed for the same reason as [APPS_TOKEN]. */
const val NOTIF_TOKEN = ":notif"

/** Command token for `:help`, exposed for the same reason as [APPS_TOKEN]. */
const val HELP_TOKEN = ":help"

/**
 * The bare-`?` shorthand for [HELP_TOKEN] — the one registered token with no `:` prefix, which
 * T140's registry already anticipated. It resolves to the same [ReservedCommand.Help] variant;
 * having two tokens for one command is exactly what a token→builder registry makes cheap.
 */
const val HELP_SHORTHAND = "?"

/**
 * How one command documents itself in `:help`'s output (T145). [usage] is what you'd type,
 * including any argument placeholder — a bare token can't express `:apps [query]`, so this is
 * spelled out rather than derived from [RegisteredCommand.token].
 */
data class CommandHelp(val usage: String, val description: String)

private data class RegisteredCommand(
    val token: String,
    /** Null hides this token from `:help` — see [HELP_SHORTHAND], documented by its long form. */
    val help: CommandHelp?,
    val build: (query: String) -> ReservedCommand,
)

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
    RegisteredCommand(APPS_TOKEN, CommandHelp(":apps [query]", "browse and launch apps")) { query ->
        ReservedCommand.Apps(query)
    },
    // Query is discarded — Settings takes none (see its doc comment) — but the builder still
    // takes one so every RegisteredCommand shares the same shape.
    RegisteredCommand(SETTINGS_TOKEN, CommandHelp(":settings", "open system settings")) {
        ReservedCommand.Settings
    },
    RegisteredCommand(NOTE_TOKEN, CommandHelp(":note [name]", "write a note")) { query ->
        ReservedCommand.Note(query)
    },
    RegisteredCommand(NOTES_TOKEN, CommandHelp(":notes [query]", "browse and search notes")) { query ->
        ReservedCommand.Notes(query)
    },
    RegisteredCommand(TASK_TOKEN, CommandHelp(":task <text> [@when]", "add a task, due today by default")) { query ->
        ReservedCommand.Task(query)
    },
    RegisteredCommand(TASKS_TOKEN, CommandHelp(":tasks", "what's outstanding, soonest first")) {
        ReservedCommand.Tasks
    },
    RegisteredCommand(TASKS_DONE_TOKEN, CommandHelp(":tasksdone", "what's been completed")) {
        ReservedCommand.TasksDone
    },
    // Hidden, like `?` — the entry above documents the spelling that matters.
    RegisteredCommand(TASKS_DONE_CAMEL, help = null) { ReservedCommand.TasksDone },
    RegisteredCommand(ALIAS_TOKEN, CommandHelp(":alias", "list defined aliases")) {
        ReservedCommand.Alias
    },
    RegisteredCommand(NOTIF_TOKEN, CommandHelp(":notif", "browse notifications")) {
        ReservedCommand.Notif
    },
    RegisteredCommand(HELP_TOKEN, CommandHelp(":help, ?", "show this help")) {
        ReservedCommand.Help
    },
    // Hidden from the listing — the entry above already documents both spellings on one line,
    // which reads better than two rows saying the same thing.
    RegisteredCommand(HELP_SHORTHAND, help = null) { ReservedCommand.Help },
)

/** Every registered command's token. */
val reservedCommandTokens: List<String> = registry.map { it.token }

/**
 * The tokens `ShellViewModel` may auto-space on completion — those that **no other token
 * extends**.
 *
 * T140 generalised auto-space to every token, which was correct while no token was a prefix of
 * another. `:notes` (T025) broke that: typing it character by character reaches `:note` first,
 * auto-space turns it into `:note ` on the spot, and the following `s` lands *after* the space —
 * producing `:note s`, a new note titled "s". **`:notes` would have been untypeable.**
 *
 * Excluding extendable tokens costs `:note` its auto-space, so `:note standup` now needs the
 * space typed. That is one keystroke, and the thing it buys back is a reachable command.
 */
val autoSpaceableTokens: List<String> = reservedCommandTokens.filter { token ->
    reservedCommandTokens.none { other -> other != token && other.startsWith(token) }
}

/**
 * What `:help` lists, in registry order (T145). Generated rather than hand-written so a command
 * can't silently go undocumented: registering it *is* documenting it, and the next command to
 * land can't forget to update a separate help constant.
 */
val reservedCommandHelp: List<CommandHelp> = registry.mapNotNull { it.help }

/**
 * Recognizes a T0 reserved command against raw shell input, or returns null if [input] doesn't
 * match anything registered — the fallthrough point where T1's fuzzy matching (T008) takes over,
 * and past that the T2/T3 escalation in `ShellViewModel.onSubmit`.
 */
/**
 * The token [input] matched, or null if none did (T014).
 *
 * Separate from [parseReservedCommand] because the command log wants to record *which command*
 * ran, not the parsed variant with its argument — `":note standup"` and `":note groceries"` are
 * the same command with different slots, and collapsing them is what makes the log a usable
 * frequency table rather than a list of every note title ever created.
 *
 * Shares [matchCommandToken] with the parser, so the two cannot disagree about what matched.
 */
fun reservedCommandToken(input: String): String? =
    registry.firstOrNull { matchCommandToken(input, it.token) != null }?.token

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
