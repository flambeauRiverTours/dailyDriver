package com.example.dailydriver.shell

import org.junit.Assert.assertEquals
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
}
