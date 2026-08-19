package com.example.dailydriver.shell

import com.example.dailydriver.alias.DefineAliasResult
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

    @Test
    fun `submit is a no-op when input is an alias definition — ShellScreen handles it, not this fallback`() {
        val viewModel = ShellViewModel()
        viewModel.onInputChange("alias gm = chrome")

        viewModel.onSubmit()

        assertTrue(viewModel.transcript.entries.isEmpty())
        assertEquals("alias gm = chrome", viewModel.input)
    }

    @Test
    fun `onAliasDefined records the input and the described outcome, then clears input`() {
        val viewModel = ShellViewModel()
        viewModel.onInputChange("alias gm = chrome")

        viewModel.onAliasDefined("alias gm = chrome", DefineAliasResult.Created("gm", "chrome"))

        val entries = viewModel.transcript.entries
        assertEquals("alias gm = chrome", entries[0].text)
        assertEquals("alias 'gm' created", entries[1].text)
        assertEquals("", viewModel.input)
    }

    @Test
    fun `expanding an alias to a reserved command enters that command's mode`() {
        // The case that proves deferring resolution was wrong: ":apps" already works today, so
        // an alias pointing at it is immediately useful with nothing downstream needed.
        val viewModel = ShellViewModel()
        viewModel.onInputChange("a")

        viewModel.onAliasExpanded(":apps")

        assertEquals(ReservedCommand.Apps(""), viewModel.reservedCommand)
        // Auto-spaced on the way in, exactly as if ":apps" had been typed, so the next keystroke
        // starts the filter query rather than corrupting the token.
        assertEquals(":apps ", viewModel.input)
    }

    @Test
    fun `expanding an alias to a reserved command records nothing — the UI is the response`() {
        val viewModel = ShellViewModel()
        viewModel.onInputChange("a")

        viewModel.onAliasExpanded(":apps")

        assertTrue(viewModel.transcript.entries.isEmpty())
    }

    @Test
    fun `expanding an alias to plain text dispatches it immediately, in one Enter`() {
        // Aliases exist for muscle memory (design doc §4.2, T0's < 5ms tier) — expanding into the
        // input line and making the user press Enter again would defeat the point.
        val viewModel = ShellViewModel()
        viewModel.onInputChange("b")

        viewModel.onAliasExpanded("maps")

        assertEquals("maps", viewModel.transcript.entries[0].text)
        assertEquals("", viewModel.input)
    }

    @Test
    fun `onAliasDefined records the input it was given, not whatever is currently in the field`() {
        // The Room write is async; the user may have already started typing something else by
        // the time the result comes back. The transcript must show what was actually submitted.
        val viewModel = ShellViewModel()
        viewModel.onInputChange("something new")

        viewModel.onAliasDefined("alias gm = chrome", DefineAliasResult.Created("gm", "chrome"))

        assertEquals("alias gm = chrome", viewModel.transcript.entries[0].text)
    }

    // --- T142: :settings ------------------------------------------------------------------------

    @Test
    fun `colon-settings parses as the Settings reserved command`() {
        val viewModel = ShellViewModel()

        viewModel.onInputChange(":settings")

        assertEquals(ReservedCommand.Settings, viewModel.reservedCommand)
    }

    @Test
    fun `onSettingsLaunched clears the input and writes nothing to the transcript`() {
        // Mirrors onAppLaunched: :settings hands off to another app entirely, so there's no
        // input to return to and no transcript entry — the UI is the system Settings app opening.
        val viewModel = ShellViewModel()
        viewModel.onInputChange(":settings")

        viewModel.onSettingsLaunched()

        assertEquals("", viewModel.input)
        assertTrue(viewModel.transcript.entries.isEmpty())
    }

    // --- T009: T1/T2 escalation on an unmatched submit ---------------------------------------

    @Test
    fun `single-word input with no match gets the T3 placeholder`() {
        // ShellScreen only ever calls onSubmit() as the fallthrough once T0 and its own T1 check
        // have both missed — so any single word reaching here is exactly T3's case.
        val viewModel = ShellViewModel()
        viewModel.onInputChange("zzzzzz")

        viewModel.onSubmit()

        assertEquals("no command match (T3)", viewModel.transcript.entries[1].text)
    }

    @Test
    fun `multi-word input with no match gets the T2 placeholder instead of T3's`() {
        // Design doc §4.2: whitespace with no T0/T1 hit skips straight to T2 — multi-word input
        // is almost always prose, not an app name T3's search would have any better luck with.
        val viewModel = ShellViewModel()
        viewModel.onInputChange("call mom tonight")

        viewModel.onSubmit()

        assertEquals("no natural-language understanding yet (T2)", viewModel.transcript.entries[1].text)
    }
}
