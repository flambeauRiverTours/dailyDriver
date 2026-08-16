package com.example.dailydriver.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.dailydriver.alias.DefineAliasResult
import com.example.dailydriver.alias.describeDefineAliasResult
import com.example.dailydriver.alias.parseAliasDefinition
import com.example.dailydriver.command.ReservedCommand
import com.example.dailydriver.command.parseReservedCommand
import com.example.dailydriver.command.reservedCommandTokens

/**
 * Holds the shell's transient state. A ViewModel is the right lifetime here: it survives
 * configuration changes but dies with the process, which is exactly the scrollback semantics the
 * design doc asks for (§4.1 — "cleared on process death").
 */
class ShellViewModel : ViewModel() {

    var transcript by mutableStateOf(Transcript())
        private set

    var input by mutableStateOf("")
        private set

    /** Non-null while the input matches a T0 reserved command — see [parseReservedCommand]. */
    val reservedCommand: ReservedCommand? get() = parseReservedCommand(input)

    fun onInputChange(value: String) {
        // Clear on the transition into apps mode specifically, not any reserved command — real
        // terminal `clear` semantics, not a per-keystroke reset, and scoped to Apps because
        // future commands (:settings fires an intent and leaves; :note opens an overlay *over*
        // the visible transcript per the design doc) have no reason to want this. The transcript
        // never comes back once cleared; leaving apps mode by typing something else doesn't
        // restore it.
        val enteringAppsMode = parseReservedCommand(value) is ReservedCommand.Apps &&
            reservedCommand !is ReservedCommand.Apps
        if (enteringAppsMode) {
            transcript = Transcript()
        }

        // Auto-separate the command token from its argument the moment any registered token is
        // completed by typing forward (value.length > input.length guards this) — but NOT when
        // arriving at the bare token by backspacing down from something longer, or the space you
        // just deleted would reappear immediately and backspace would get stuck. Generalized
        // across every registered token, not just :apps: harmless even for a command that turns
        // out to take no argument, and saves each of T142-T145 from reimplementing it.
        val justCompletedToken = value in reservedCommandTokens && value.length > input.length
        input = if (justCompletedToken) "$value " else value
    }

    fun onSubmit() {
        // In apps mode, ShellScreen intercepts Enter to launch a sole match before this is
        // reached; anything that gets here has no unique target, so it's a no-op rather than
        // recording ":apps chrome" as a bogus command. Generalized to any reserved command: Enter
        // behavior for a matched-but-not-yet-built command (:settings, etc.) belongs to that
        // command's own task, not to this fallback.
        if (reservedCommand != null) return
        // Same reasoning, for alias definitions: ShellScreen intercepts these before onSubmit is
        // called (it needs to await the Room write, which this synchronous method can't do) and
        // calls onAliasDefined with the result. Guarded here too so onSubmit stays correct even
        // if called directly.
        if (parseAliasDefinition(input) != null) return
        transcript = transcript.record(input, NO_PIPELINE_YET)
        input = ""
    }

    /**
     * Leaves apps mode after an app has been opened, by any route (tap, Enter, or the typing-pause
     * auto-launch). Clearing the input is load-bearing: were it left at `:apps chrome`, returning
     * to the launcher — or a rotation, which rebuilds the composition — would re-trigger
     * auto-launch on the still-unique match and bounce straight back out of the launcher.
     */
    fun onAppLaunched() {
        input = ""
    }

    /**
     * Records the outcome of an alias-definition attempt and clears the input. [submittedInput]
     * is what the user actually typed, passed in rather than read from [input] — the Room write
     * this follows is async, so by the time the result is known the user may have already moved
     * on to typing something else, and the transcript must show what was actually submitted.
     */
    fun onAliasDefined(submittedInput: String, result: DefineAliasResult) {
        transcript = transcript.record(submittedInput, describeDefineAliasResult(result))
        input = ""
    }

    private companion object {
        /** Placeholder for the T1 fuzzy-match fallthrough (T008), which doesn't exist yet. */
        const val NO_PIPELINE_YET = "no command pipeline yet (T008)"
    }
}
