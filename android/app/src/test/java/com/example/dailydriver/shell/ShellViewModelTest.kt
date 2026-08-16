package com.example.dailydriver.shell

import com.example.dailydriver.command.ReservedCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellViewModelTest {

    @Test
    fun `starts with an empty transcript and empty input`() {
        val viewModel = ShellViewModel()

        assertTrue(viewModel.transcript.entries.isEmpty())
        assertEquals("", viewModel.input)
    }

    @Test
    fun `submitting records the command and clears the input line`() {
        val viewModel = ShellViewModel()
        viewModel.onInputChange("chrome")

        viewModel.onSubmit()

        assertEquals("chrome", viewModel.transcript.entries.first().text)
        assertEquals("", viewModel.input)
    }

    @Test
    fun `submitting blank input clears the line without touching the transcript`() {
        val viewModel = ShellViewModel()
        viewModel.onInputChange("   ")

        viewModel.onSubmit()

        assertTrue(viewModel.transcript.entries.isEmpty())
        assertEquals("", viewModel.input)
    }

    @Test
    fun `every submission gets a response line, so entries come in pairs`() {
        val viewModel = ShellViewModel()

        repeat(3) {
            viewModel.onInputChange("cmd$it")
            viewModel.onSubmit()
        }

        assertEquals(6, viewModel.transcript.entries.size)
    }

    @Test
    fun `changing input does not affect the transcript until submit`() {
        val viewModel = ShellViewModel()

        viewModel.onInputChange("chrome")

        assertTrue(viewModel.transcript.entries.isEmpty())
        assertEquals("chrome", viewModel.input)
    }

    @Test
    fun `submitting twice in a row with no new input does not duplicate entries`() {
        val viewModel = ShellViewModel()
        viewModel.onInputChange("chrome")
        viewModel.onSubmit()
        val sizeAfterFirstSubmit = viewModel.transcript.entries.size

        viewModel.onSubmit() // input is already "" — the real-world double-tap-Go case

        assertEquals(sizeAfterFirstSubmit, viewModel.transcript.entries.size)
    }

    @Test
    fun `submitting produces an input entry followed by an output entry`() {
        val viewModel = ShellViewModel()
        viewModel.onInputChange("chrome")

        viewModel.onSubmit()

        val entries = viewModel.transcript.entries
        assertTrue(entries[0] is TranscriptEntry.Input)
        assertTrue(entries[1] is TranscriptEntry.Output)
    }

    @Test
    fun `reservedCommand is null outside apps mode, parsed when inside it`() {
        val viewModel = ShellViewModel()

        assertNull(viewModel.reservedCommand)

        viewModel.onInputChange(":apps chrome")

        assertEquals(ReservedCommand.Apps("chrome"), viewModel.reservedCommand)
    }

    @Test
    fun `entering apps mode clears an existing transcript`() {
        val viewModel = ShellViewModel()
        viewModel.onInputChange("chrome")
        viewModel.onSubmit()
        assertTrue(viewModel.transcript.entries.isNotEmpty())

        viewModel.onInputChange(":apps")

        assertTrue(viewModel.transcript.entries.isEmpty())
    }

    @Test
    fun `the clear happens once on the transition, not on every keystroke while already in apps mode`() {
        val viewModel = ShellViewModel()
        viewModel.onInputChange(":apps")
        val transcriptAfterEntering = viewModel.transcript

        viewModel.onInputChange(":apps c")
        viewModel.onInputChange(":apps ch")

        assertSame(transcriptAfterEntering, viewModel.transcript)
    }

    @Test
    fun `leaving apps mode does not restore the cleared transcript`() {
        val viewModel = ShellViewModel()
        viewModel.onInputChange("chrome")
        viewModel.onSubmit()
        viewModel.onInputChange(":apps")
        assertTrue(viewModel.transcript.entries.isEmpty())

        viewModel.onInputChange("something else entirely")

        assertTrue(viewModel.transcript.entries.isEmpty())
    }

    @Test
    fun `submit is a no-op while in apps mode — selection happens by tapping a row`() {
        val viewModel = ShellViewModel()
        viewModel.onInputChange(":apps chrome")

        viewModel.onSubmit()

        assertTrue(viewModel.transcript.entries.isEmpty())
        assertEquals(":apps chrome", viewModel.input)
    }

    @Test
    fun `launching an app exits apps mode by clearing the input`() {
        // Required for correctness, not tidiness: if the input stayed ":apps chrome", returning
        // to the launcher (or a rotation, which recreates the composition) would re-fire the
        // auto-launch effect and relaunch the same app, with no way back to the prompt.
        val viewModel = ShellViewModel()
        viewModel.onInputChange(":apps chrome")

        viewModel.onAppLaunched()

        assertEquals("", viewModel.input)
        assertNull(viewModel.reservedCommand)
    }

    @Test
    fun `launching an app does not write anything to the transcript`() {
        val viewModel = ShellViewModel()
        viewModel.onInputChange(":apps chrome")

        viewModel.onAppLaunched()

        assertTrue(viewModel.transcript.entries.isEmpty())
    }

    @Test
    fun `typing the final character of colon-apps auto-appends a separating space`() {
        val viewModel = ShellViewModel()
        viewModel.onInputChange(":app")

        viewModel.onInputChange(":apps")

        assertEquals(":apps ", viewModel.input)
    }

    @Test
    fun `backspacing down to exactly colon-apps does not re-add the space`() {
        // If it did, backspace would be permanently stuck at ":apps " — the space you just
        // deleted would reappear immediately, making it impossible to edit past it.
        val viewModel = ShellViewModel()
        viewModel.onInputChange(":apps chrome")

        viewModel.onInputChange(":apps")

        assertEquals(":apps", viewModel.input)
    }

    @Test
    fun `further backspacing past colon-apps is unaffected`() {
        val viewModel = ShellViewModel()
        viewModel.onInputChange(":apps")

        viewModel.onInputChange(":app")

        assertEquals(":app", viewModel.input)
    }
}
