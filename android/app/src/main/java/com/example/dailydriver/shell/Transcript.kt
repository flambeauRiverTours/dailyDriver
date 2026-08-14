package com.example.dailydriver.shell

/**
 * The shell scrollback, oldest entry first. Immutable — [record] returns a new instance, which is
 * what drives recomposition when held in Compose state.
 *
 * Ephemeral by design (design doc §4.1): never persisted, never synced. The "last N entries" cap
 * lands in T016 and belongs in [record].
 */
data class Transcript(val entries: List<TranscriptEntry> = emptyList()) {

    /** Records a submitted command and its response. Blank input is ignored. */
    fun record(input: String, output: String): Transcript {
        val command = input.trim()
        if (command.isEmpty()) return this
        return Transcript(
            entries + TranscriptEntry.Input(command) + TranscriptEntry.Output(output),
        )
    }
}
