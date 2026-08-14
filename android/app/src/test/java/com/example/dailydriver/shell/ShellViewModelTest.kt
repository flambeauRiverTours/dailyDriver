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
}
