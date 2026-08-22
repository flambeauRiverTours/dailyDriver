package com.flambeauriver.dailydriver.command

/** A recognized T0 reserved command. One variant per command — more arrive as T141–T145 land. */
sealed interface ReservedCommand {
    data class Apps(val query: String) : ReservedCommand

    /**
     * No query, unlike [Apps] — `:settings` just fires the system Settings intent and returns to
     * the prompt (T142), it has no interactive browse mode to filter. `data object`, not a plain
     * `object`, so it still gets a real `toString()`/`equals()` for free like every other variant.
     */
    data object Settings : ReservedCommand

    /**
     * `query` carries the design doc §4.6 worked example's name argument (`note standup`) for
     * whenever the real editor overlay (T020) lands and has to decide what it means — discarded
     * for now (T143), same as [Settings] discards its query, since there's nowhere yet for it to
     * go.
     */
    data class Note(val query: String) : ReservedCommand

    /**
     * `:notes [query]` — browse and search stored notes (T025/T026). Separate from [Note], which
     * *makes* one: "note" creates, "notes" shows you yours.
     *
     * `query` is the live search text, parsed by `parseNoteQuery` into a tag filter or free text.
     */
    data class Notes(val query: String) : ReservedCommand

    /**
     * `:task <text>` — adds a due-dated task (T155). The text may carry a date shortcut
     * (`buy milk @fri`); with none, the task is due today (design check-in, 2026-08-21).
     */
    data class Task(val text: String) : ReservedCommand

    /** `:tasks` — outstanding tasks, soonest first (T156). */
    data object Tasks : ReservedCommand

    /**
     * `:tasksdone` — completed tasks (T156, design check-in).
     *
     * A separate command rather than a toggle inside `:tasks`: the outstanding list answers "what
     * do I still have to do", and mixing finished work into it dilutes the one question it exists
     * to answer.
     */
    data object TasksDone : ReservedCommand

    /**
     * No query, like [Settings] — `:alias` (design doc §4.7) just lists what's currently defined,
     * nothing to filter by (T144).
     */
    data object Alias : ReservedCommand

    /**
     * No query — design check-in (2026-08-19) deliberately left `:notif` unfiltered, since
     * notification counts are usually small and there's no single obvious field to filter by the
     * way `:apps` filters by app name (T147).
     */
    data object Notif : ReservedCommand

    /**
     * No query — `:help` and its bare-`?` shorthand both resolve here (T145). One variant, two
     * registered tokens; see [HELP_SHORTHAND].
     */
    data object Help : ReservedCommand
}
