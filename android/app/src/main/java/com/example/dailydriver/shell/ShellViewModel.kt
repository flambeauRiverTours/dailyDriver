package com.example.dailydriver.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
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
        input = value
    }

    fun onSubmit() {
        // Selection in apps mode happens by tapping a row (AppListView), not by submitting —
        // submitting here would otherwise record ":apps chrome" as a bogus command.
        if (appsCommand != null) return
        transcript = transcript.record(input, NO_PIPELINE_YET)
        input = ""
    }

    private companion object {
        /** Placeholder until the T0–T3 cascade lands (T140 onward). */
        const val NO_PIPELINE_YET = "no command pipeline yet (T140)"
    }
}
