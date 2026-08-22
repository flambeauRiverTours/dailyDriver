package com.flambeauriver.dailydriver.shell

/**
 * One line in the shell scrollback. [Input] is what the user typed (rendered with the prompt
 * glyph); [Output] is what came back.
 */
sealed interface TranscriptEntry {

    val text: String

    data class Input(override val text: String) : TranscriptEntry

    data class Output(override val text: String) : TranscriptEntry
}
