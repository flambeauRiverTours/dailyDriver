package com.example.dailydriver.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.dailydriver.apps.APPS_COMMAND_TOKEN
import com.example.dailydriver.apps.AppsCommand
import com.example.dailydriver.apps.parseAppsCommand

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

    /** Non-null while the input represents the `:apps` command — see [parseAppsCommand]. */
    val appsCommand: AppsCommand? get() = parseAppsCommand(input)

    fun onInputChange(value: String) {
        // Clear on the transition into apps mode only — real terminal `clear` semantics, not a
        // per-keystroke reset. The transcript never comes back once cleared; leaving apps mode by
        // typing something else doesn't restore it.
        val enteringAppsMode = parseAppsCommand(value) != null && appsCommand == null
        if (enteringAppsMode) {
            transcript = Transcript()
        }

        // Auto-separate the command from the filter query the moment ":apps" is completed by
        // typing forward (value.length > input.length guards this) — but NOT when arriving at
        // exactly ":apps" by backspacing down from something longer, or the space you just
        // deleted would reappear immediately and backspace would get stuck.
        val justCompletedAppsToken = value == APPS_COMMAND_TOKEN && value.length > input.length
        input = if (justCompletedAppsToken) "$value " else value
    }

    fun onSubmit() {
        // In apps mode, ShellScreen intercepts Enter to launch a sole match before this is
        // reached; anything that gets here has no unique target, so it's a no-op rather than
        // recording ":apps chrome" as a bogus command.
        if (appsCommand != null) return
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

    private companion object {
        /** Placeholder until the T0–T3 cascade lands (T140 onward). */
        const val NO_PIPELINE_YET = "no command pipeline yet (T140)"
    }
}
