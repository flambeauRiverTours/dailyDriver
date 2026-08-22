package com.flambeauriver.dailydriver.shell

/**
 * The most recent entries [Transcript] keeps; older ones fall off the top (T016).
 *
 * **Even, and deliberately so.** Every [Transcript.record] appends exactly two entries — the input
 * and its output — so an even cap trimmed from the front always drops whole exchanges. An odd cap
 * would eventually orphan an output whose input had already been dropped, leaving the scrollback
 * opening on an answer to a question you can no longer see.
 *
 * 200 entries is 100 commands: far more than anyone scrolls back through on a phone, which is the
 * point. The cap exists to bound a process that genuinely never dies (this is the home screen, so
 * it's resumed and paused for months rather than restarted), not to ration a scarce resource.
 */
internal const val MAX_TRANSCRIPT_ENTRIES = 200

/**
 * The shell scrollback, oldest entry first. Immutable — [record] returns a new instance, which is
 * what drives recomposition when held in Compose state.
 *
 * Ephemeral by design (design doc §4.1): never persisted, never synced, and **cleared on process
 * death** — which needs no code, because [ShellViewModel] holds this and a `ViewModel` dies with
 * its process while surviving configuration changes. That's exactly the lifetime §4.1 asks for, so
 * T016's only real work was the cap ([MAX_TRANSCRIPT_ENTRIES]).
 */
data class Transcript(val entries: List<TranscriptEntry> = emptyList()) {

    /**
     * Records a submitted command and its response. Blank input is ignored.
     *
     * Trims to [MAX_TRANSCRIPT_ENTRIES], oldest first. Applied on the way in rather than at render
     * time so the bound is on what's actually retained — trimming only in the UI would let the
     * list grow without limit behind it, which is the leak this exists to prevent.
     */
    fun record(input: String, output: String): Transcript {
        val command = input.trim()
        if (command.isEmpty()) return this
        val appended = entries + TranscriptEntry.Input(command) + TranscriptEntry.Output(output)
        return Transcript(appended.takeLast(MAX_TRANSCRIPT_ENTRIES))
    }
}
