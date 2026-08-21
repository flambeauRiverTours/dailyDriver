package com.example.dailydriver.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.dailydriver.alias.DefineAliasResult
import com.example.dailydriver.alias.describeDefineAliasResult
import com.example.dailydriver.alias.parseAliasDefinition
import com.example.dailydriver.command.ReservedCommand
import com.example.dailydriver.command.escalationTier
import com.example.dailydriver.command.helpText
import com.example.dailydriver.command.parseReservedCommand
import com.example.dailydriver.command.autoSpaceableTokens
import com.example.dailydriver.command.unmatchedResponse

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
        // autoSpaceableTokens, not every token: a token another one extends (`:note` vs `:notes`)
        // must not auto-space, or typing the longer one is impossible — the space lands mid-word.
        val justCompletedToken = value in autoSpaceableTokens && value.length > input.length
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
            // With access granted, ShellScreen shows the :notif browse view and Enter has no
            // sole-match-style target to commit to (tap-only) — a no-op, same as Apps' own
            // no-sole-match case. Without access, ShellScreen intercepts Enter before it ever
            // reaches here (see onNotifAccessSettingsOpened) — reaching here in that state would
            // mean that interception broke, so the same no-op is still the safe default.
            is ReservedCommand.Notif -> return
            // T143 recorded a placeholder here until T020 existed; now ShellScreen intercepts
            // this and opens the editor, so reaching onSubmit would mean that broke. No-op
            // defensively rather than recording ":note" as a bogus command, same as Settings.
            is ReservedCommand.Note -> return
            // Browse mode, like Apps and Notif — ShellScreen shows the list and selection is by
            // tapping a row, so Enter has nothing to commit to and must not record ":notes foo"
            // as a command that ran.
            is ReservedCommand.Notes -> return
            // Like the alias-definition path: adding a task awaits a Room write, which this
            // synchronous method can't do, so ShellScreen intercepts and calls onTaskAdded with
            // the result. No-op here so onSubmit stays correct if it is ever reached directly.
            is ReservedCommand.Task -> return
            // Browse modes, like Notes and Notif — ShellScreen shows the list, selection is by
            // tapping a row, and Enter must not record ":tasks" as a command that ran.
            is ReservedCommand.Tasks -> return
            is ReservedCommand.TasksDone -> return
            // T145. Like Note, this wants onSubmit's normal record-and-clear — helpText() is
            // pure and needs no Context or repository, so there's nothing for ShellScreen to
            // resolve first the way :alias needs the live aliases map.
            is ReservedCommand.Help -> {
                transcript = transcript.record(input, helpText())
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
        // T009 (design doc §4.2) classifies the fork — whitespace means prose, so T2; otherwise
        // T3. T010 turns that classification into real output: see [unmatchedResponse] for why
        // T3 is help-only rather than a second app search, and why T2 degrades here instead of
        // dead-ending while the SLM is absent.
        transcript = transcript.record(input, unmatchedResponse(input, escalationTier(input)))
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
     * Clears the prompt once `:note` has handed off to the editor (T020). Nothing is recorded to
     * the transcript: the editor appearing *is* the response, the same reasoning
     * [onAliasExpanded] uses for a reserved command, and a `:note` line in the scrollback would
     * be noise you'd scroll past forever.
     */
    fun onNoteEditorOpened() {
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
     * Leaves `:notif` mode after tapping a notification fires its `contentIntent` and hands off
     * to whatever app that opens — same reasoning as [onAppLaunched]/[onSettingsLaunched].
     */
    fun onNotificationOpened() {
        input = ""
    }

    /**
     * Leaves the input at rest after `:notif` (without access) deep-links to Settings ->
     * Notification access — same reasoning as [onSettingsLaunched], distinctly named per call
     * site rather than reused, matching this file's existing one-liner-per-command precedent.
     */
    fun onNotifAccessSettingsOpened() {
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

    /**
     * Records the outcome of `:task` (T155), which [ShellScreen] performs because it awaits a Room
     * write — same shape as [onAliasDefined], and captured input for the same reason: by the time
     * the write returns, the live `input` may already be something else.
     *
     * Echoes the resolved date rather than what was typed, so `@fri` is confirmed back as a real
     * day. That is the only feedback that a shortcut was understood at all.
     */
    fun onTaskAdded(submittedInput: String, text: String, due: String) {
        transcript = transcript.record(submittedInput, "added: $text (due $due)")
        input = ""
    }

    /** `:task` with nothing after it. Says so rather than creating an empty task. */
    fun onEmptyTask(submittedInput: String) {
        transcript = transcript.record(submittedInput, "nothing to add — try :task buy milk @fri")
        input = ""
    }

}
