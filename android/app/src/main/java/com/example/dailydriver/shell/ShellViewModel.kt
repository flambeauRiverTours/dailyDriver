package com.example.dailydriver.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

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

    fun onInputChange(value: String) {
        input = value
    }

    fun onSubmit() {
        transcript = transcript.record(input, NO_PIPELINE_YET)
        input = ""
    }

    private companion object {
        /** Placeholder until the T0–T3 cascade lands (T006 onward). */
        const val NO_PIPELINE_YET = "no command pipeline yet (T006)"
    }
}
