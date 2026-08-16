package com.example.dailydriver.command

/** A recognized T0 reserved command. One variant per command — more arrive as T141–T145 land. */
sealed interface ReservedCommand {
    data class Apps(val query: String) : ReservedCommand
}
