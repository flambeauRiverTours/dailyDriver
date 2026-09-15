package com.flambeauriver.dailydriver.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.flambeauriver.dailydriver.command.CommandEnvironment
import com.flambeauriver.dailydriver.command.ReservedCommand
import com.flambeauriver.dailydriver.command.autoSpaceableTokens
import com.flambeauriver.dailydriver.command.dispatchCommand
import com.flambeauriver.dailydriver.command.isTrainingExample
import com.flambeauriver.dailydriver.command.parseReservedCommand
import com.flambeauriver.dailydriver.command.resolutionOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Holds the shell's transient state and dispatches what is typed at it.
 *
 * A ViewModel is the right lifetime: it survives configuration changes but dies with the process,
 * which is exactly the scrollback semantics the design doc asks for (§4.1 — "cleared on process
 * death").
 *
 * **This owns dispatch again as of T178.** It previously did not: `onSubmit` was a twelve-branch
 * `when` in which eleven branches were a bare `return`, each commented to explain that
 * `ShellScreen` had already intercepted the command. Everything a command needs beyond state now
 * arrives through [CommandEnvironment], so the decisions live here and the composable renders.
 */
class ShellViewModel(
    private val env: CommandEnvironment,
    /**
     * Where dispatch runs. Null means [viewModelScope], which is what production uses.
     *
     * Overridable because `viewModelScope` dispatches on `Dispatchers.Main`, which a plain JVM
     * unit test has no looper for — a test passes an unconfined scope so dispatch completes
     * inline. Resolved in a property initializer rather than as a default argument, since a
     * constructor default cannot reach `this`.
     */
    scope: CoroutineScope? = null,
) : ViewModel() {

    private val dispatchScope: CoroutineScope = scope ?: viewModelScope

    var transcript by mutableStateOf(Transcript())
        private set

    var input by mutableStateOf("")
        private set

    /** Non-null while the input matches a T0 reserved command — see [parseReservedCommand]. */
    val reservedCommand: ReservedCommand? get() = parseReservedCommand(input)

    /**
     * How often each target has been run, for ranking candidates (T183/T184).
     *
     * **Held as state and refreshed after each command rather than read per keystroke.** Ranking
     * runs on every character typed and is a pure function over a plain map (see `rankCandidates`);
     * awaiting a Room read there would put the database on the launcher's fast path, which §4.2
     * exists to keep clear. The table only changes when a command runs, so refreshing there is
     * both sufficient and off the critical path.
     *
     * Loaded once at construction so ranking is correct on the first keystroke after a cold start,
     * not only after the first command.
     */
    var usageCounts by mutableStateOf(emptyMap<String, Int>())
        private set

    init {
        refreshUsageCounts()
    }

    private fun refreshUsageCounts() {
        dispatchScope.launch { usageCounts = env.usageCounts() }
    }

    fun onInputChange(value: String) {
        // Clear on the transition into apps mode specifically, not any reserved command — real
        // terminal `clear` semantics, not a per-keystroke reset, and scoped to Apps because other
        // commands have no reason to want it (`:settings` fires an intent and leaves; `:note`
        // opens an overlay *over* the visible transcript). The transcript never comes back once
        // cleared; leaving apps mode by typing something else doesn't restore it.
        val enteringAppsMode = parseReservedCommand(value) is ReservedCommand.Apps &&
            reservedCommand !is ReservedCommand.Apps
        if (enteringAppsMode) {
            transcript = Transcript()
        }

        // Auto-separate a command token from its argument the moment a registered token is
        // completed by typing forward (`value.length > input.length` guards this) — but NOT when
        // arriving at the bare token by backspacing down from something longer, or the space you
        // just deleted would reappear and backspace would get stuck.
        //
        // `autoSpaceableTokens`, not every token: a token another one extends (`:note` vs
        // `:notes`) must not auto-space, or typing the longer one is impossible — the space lands
        // mid-word.
        val justCompletedToken = value in autoSpaceableTokens && value.length > input.length
        input = if (justCompletedToken) "$value " else value
    }

    /**
     * Runs whatever is at the prompt.
     *
     * **The input is captured and cleared before anything is awaited.** Half the commands await a
     * Room or file write, and by the time one returns the user may already be typing something
     * else — so the transcript must show what was actually submitted. Before T178 this was a
     * `submittedInput` parameter threaded through five separate ViewModel methods and a matching
     * comment at four call sites in `ShellScreen`.
     */
    fun onSubmit() {
        val submitted = input
        if (submitted.isBlank()) {
            input = ""
            return
        }
        run(submitted)
    }

    /**
     * Acts on input without Enter — the one-second typing pause (T163) and a tapped row.
     *
     * **Every path that acts on typed input must go through here**, because this is where the
     * T014 log is written. That rule exists because it was once broken: T014 originally logged
     * only the Enter handler, which silently biased the training set toward input typed fast
     * enough to beat the pause timer. By T178 there were three logging call sites and a fourth
     * path — opening a sole note on the pause — that logged nothing at all.
     */
    fun onResolved(submitted: String) {
        run(submitted)
    }

    private fun run(submitted: String) {
        dispatchScope.launch {
            // Fire-and-forget by contract: the log is a by-product of running a command, and
            // making the command wait on a database write would put it on the launcher's fast
            // path, which §4.2 exists to keep clear.
            if (isTrainingExample(submitted)) {
                env.recordResolution(submitted.trim(), resolutionOf(submitted, env))
            }
            val outcome = dispatchCommand(submitted, env)
            outcome.transcript?.let { transcript = transcript.record(submitted, it) }
            // **Whether the prompt clears is the outcome's call, not the caller's.** A browse view
            // with nothing to commit to keeps what was typed — clearing `:apps ca` would close the
            // list you are reading and throw away the filter that produced it.
            //
            // Guarded on the input still being what was submitted, so a slow command (`:export`
            // reads the database and writes a file) cannot wipe something typed while it ran.
            if (outcome.clearsInput && input == submitted) input = ""
            // After the command, not before: `recordResolution` is fire-and-forget, so the count
            // this picks up may still be the pre-command one. That is fine — it is a ranking
            // input, not a ledger, and it converges on the next command.
            refreshUsageCounts()
        }
    }

    /**
     * Clears the prompt after the UI has taken over without a command running — the editor
     * opening on a tapped note, an app opened by tapping a row.
     *
     * Clearing is load-bearing for the app case: were the input left at `:apps chrome`, returning
     * to the launcher — or a rotation, which rebuilds the composition — would re-trigger
     * auto-launch on the still-unique match and bounce straight back out of the launcher.
     */
    fun onHandedOff() {
        input = ""
    }

    companion object {
        fun factory(env: CommandEnvironment) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ShellViewModel(env) as T
        }
    }
}
