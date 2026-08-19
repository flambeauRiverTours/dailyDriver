package com.example.dailydriver.command

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
