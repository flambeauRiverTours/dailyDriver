package com.example.dailydriver.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.dailydriver.alias.DefineAliasResult
import com.example.dailydriver.alias.describeDefineAliasResult
import com.example.dailydriver.alias.parseAliasDefinition
import com.example.dailydriver.command.EscalationTier
import com.example.dailydriver.command.ReservedCommand
import com.example.dailydriver.command.escalationTier
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
        // Exhaustive over ReservedCommand (no `else`): Kotlin flags a missing branch here even as
        // a statement, which won't fail the build (allWarningsAsErrors isn't set) but does mean
        // T144/T145 can't add a new variant without the compiler pointing back at this decision.
        when (reservedCommand) {
            // ShellScreen intercepts Enter to launch a sole match before this is reached;
            // anything that gets here has no unique target, so it's a no-op rather than
            // recording ":apps chrome" as a bogus command.
            is ReservedCommand.Apps -> return
            // ShellScreen intercepts Settings before onSubmit is ever called — firing the intent
            // needs a Context this ViewModel doesn't have. Reaching here would mean that
            // interception broke; no-op defensively rather than recording something wrong.
            is ReservedCommand.Settings -> return
            // Same reasoning as Settings — ShellScreen intercepts Alias before onSubmit is ever
            // called, since it needs the live aliases map this ViewModel doesn't hold (T144).
            is ReservedCommand.Alias -> return
            // T143: the real editor overlay is T020, which doesn't exist yet. Recorded as a
            // normal exchange (not silently dropped) so the launcher gives an honest answer
            // instead of looking broken — same shape as the T2/T3 placeholders below.
            is ReservedCommand.Note -> {
                transcript = transcript.record(input, NOTE_NOT_BUILT_YET)
                input = ""
                return
            }
            null -> Unit
        }
        // Same reasoning, for alias definitions: ShellScreen intercepts these before onSubmit is
        // called (it needs to await the Room write, which this synchronous method can't do) and
        // calls onAliasDefined with the result. Guarded here too so onSubmit stays correct even
        // if called directly.
        if (parseAliasDefinition(input) != null) return
        // T009 (design doc §4.2): whitespace is the T2/T3 fork. Neither tier has a real handler
        // yet (T2 needs the SLM, T3 is T010), so this only decides which placeholder to show —
        // but it's the same decision real routing will make later, so the boundary is exercised
        // now rather than deferred wholesale.
        val placeholder = when (escalationTier(input)) {
            EscalationTier.T2 -> T2_NOT_BUILT_YET
            EscalationTier.T3 -> T3_NOT_BUILT_YET
        }
        transcript = transcript.record(input, placeholder)
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
     * Leaves the input at rest after `:settings` fires the system Settings intent and hands off
     * to another app entirely (T142) — there's nothing to keep at the prompt to return to, and no
     * transcript entry either, same reasoning as [onAppLaunched] for a launched app.
     */
    fun onSettingsLaunched() {
        input = ""
    }

    /**
     * Records the `:alias` listing [ShellScreen] already formatted (it holds the live aliases
     * map this ViewModel doesn't) and clears the input — same shape as [onAliasDefined], which
     * likewise records an outcome computed elsewhere rather than resolving it itself.
     */
    fun onAliasListRequested(listing: String) {
        transcript = transcript.record(input, listing)
        input = ""
    }

    /**
     * Substitutes a resolved alias's [expansion] into the input and dispatches it exactly as if
     * it had been typed — one Enter, not two, since aliases exist for muscle memory (design doc
     * §4.2, T0's tight latency tier) and making the user confirm the expansion would defeat that.
     *
     * Reusing [onInputChange] rather than assigning `input` directly is deliberate: it carries
     * the auto-space and enter-apps-mode-clears-transcript behavior for free, so an alias to
     * `:apps` behaves identically to typing `:apps` by hand.
     *
     * Cannot recurse. An alias can never point at another alias — [evaluateAliasDefinition]
     * rejects that at definition time — so exactly one substitution ever happens.
     */
    fun onAliasExpanded(expansion: String) {
        onInputChange(expansion)
        // A reserved command's response *is* the UI reacting to the new input (`:apps` opening
        // the browse view), so there's nothing to record and nothing further to dispatch.
        if (reservedCommand != null) return
        onSubmit()
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
        /** Placeholder for prose input that would escalate to the SLM (T2), not built yet. */
        const val T2_NOT_BUILT_YET = "no natural-language understanding yet (T2)"

        /** Placeholder for single-word input with no T3 app-search-or-help handler yet (T010). */
        const val T3_NOT_BUILT_YET = "no command match (T3)"

        /** Placeholder for `:note` (T143) until the real editor overlay (T020) exists. */
        const val NOTE_NOT_BUILT_YET = "will open notes in the future (T020)"
    }
}
