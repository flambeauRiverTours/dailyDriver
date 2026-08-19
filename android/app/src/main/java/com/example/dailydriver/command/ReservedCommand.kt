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
}
