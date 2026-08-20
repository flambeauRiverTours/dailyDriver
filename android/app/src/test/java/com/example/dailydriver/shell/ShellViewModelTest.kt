package com.example.dailydriver.shell

import com.example.dailydriver.alias.DefineAliasResult
import com.example.dailydriver.command.ReservedCommand
import com.example.dailydriver.command.reservedCommandHelp
import com.example.dailydriver.command.reservedCommandTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    // --- T143: :note ----------------------------------------------------------------------------

    @Test
    fun `colon-note parses as the Note reserved command`() {
        val viewModel = ShellViewModel()

        viewModel.onInputChange(":note")

        assertEquals(ReservedCommand.Note(""), viewModel.reservedCommand)
    }

    @Test
    fun `submitting colon-note records nothing — ShellScreen opens the editor instead`() {
        // T143's placeholder is gone as of T020. Reaching onSubmit at all would mean the
        // interception in ShellScreen broke, so this no-ops rather than recording ":note" as a
        // command that ran.
        val viewModel = ShellViewModel()
        viewModel.onInputChange(":note")

        viewModel.onSubmit()

        assertTrue(viewModel.transcript.entries.isEmpty())
    }

    @Test
    fun `opening the editor clears the prompt and records nothing`() {
        // The editor appearing is the response — a ":note" line in the scrollback would be noise
        // you scroll past forever. Same reasoning as expanding an alias into a reserved command.
        val viewModel = ShellViewModel()
        viewModel.onInputChange(":note standup")

        viewModel.onNoteEditorOpened()

        assertEquals("", viewModel.input)
        assertTrue(viewModel.transcript.entries.isEmpty())
    }

    // --- T144: :alias ---------------------------------------------------------------------------

    @Test
    fun `colon-alias parses as the Alias reserved command`() {
        val viewModel = ShellViewModel()

        viewModel.onInputChange(":alias")

        assertEquals(ReservedCommand.Alias, viewModel.reservedCommand)
    }

    @Test
    fun `onAliasListRequested records the listing it was given and clears the input`() {
        // The listing itself is formatted by ShellScreen (it holds the live aliases map, not
        // this ViewModel) — onAliasListRequested just records whatever it's handed, same shape
        // as onAliasDefined.
        val viewModel = ShellViewModel()
        viewModel.onInputChange(":alias")

        viewModel.onAliasListRequested("gm = chrome\nmaps = google maps")

        val entries = viewModel.transcript.entries
        assertEquals(":alias", entries[0].text)
        assertEquals("gm = chrome\nmaps = google maps", entries[1].text)
        assertEquals("", viewModel.input)
    }

    // --- T145: :help / ? --------------------------------------------------------------------

    @Test
    fun `both colon-help and bare question mark parse to the same Help command`() {
        val viewModel = ShellViewModel()

        viewModel.onInputChange(":help")
        assertEquals(ReservedCommand.Help, viewModel.reservedCommand)

        viewModel.onInputChange("?")
        assertEquals(ReservedCommand.Help, viewModel.reservedCommand)
    }

    @Test
    fun `submitting colon-help records the help text and clears the input`() {
        val viewModel = ShellViewModel()
        viewModel.onInputChange(":help")

        viewModel.onSubmit()

        val entries = viewModel.transcript.entries
        assertEquals(":help", entries[0].text)
        assertTrue(entries[1].text.contains(":apps"))
        assertEquals("", viewModel.input)
    }

    @Test
    fun `the question-mark shorthand produces identical output to colon-help`() {
        val viaToken = ShellViewModel().apply {
            onInputChange(":help")
            onSubmit()
        }
        val viaShorthand = ShellViewModel().apply {
            onInputChange("?")
            onSubmit()
        }

        assertEquals(
            viaToken.transcript.entries[1].text,
            viaShorthand.transcript.entries[1].text,
        )
    }

    // --- T147: :notif -----------------------------------------------------------------------

    @Test
    fun `colon-notif parses as the Notif reserved command`() {
        val viewModel = ShellViewModel()

        viewModel.onInputChange(":notif")

        assertEquals(ReservedCommand.Notif, viewModel.reservedCommand)
    }

    @Test
    fun `submit is a no-op while colon-notif is active — tap-only, no keyboard selection`() {
        val viewModel = ShellViewModel()
        // Auto-space-on-completion (T140, generalized to every registered token) turns this into
        // ":notif " — irrelevant to what's under test here, just accounted for in the assertion.
        viewModel.onInputChange(":notif")

        viewModel.onSubmit()

        assertTrue(viewModel.transcript.entries.isEmpty())
        assertEquals(":notif ", viewModel.input)
    }

    @Test
    fun `onNotificationOpened clears the input and writes nothing to the transcript`() {
        val viewModel = ShellViewModel()
        viewModel.onInputChange(":notif")

        viewModel.onNotificationOpened()

        assertEquals("", viewModel.input)
        assertTrue(viewModel.transcript.entries.isEmpty())
    }

    @Test
    fun `onNotifAccessSettingsOpened clears the input and writes nothing to the transcript`() {
        val viewModel = ShellViewModel()
        viewModel.onInputChange(":notif")

        viewModel.onNotifAccessSettingsOpened()

        assertEquals("", viewModel.input)
        assertTrue(viewModel.transcript.entries.isEmpty())
    }

    // --- T009: T1/T2 escalation on an unmatched submit ---------------------------------------

    @Test
    fun `single-word input with no match gets T3's response, naming what failed`() {
        // ShellScreen only ever calls onSubmit() as the fallthrough once T0 and its own T1 check
        // have both missed — so any single word reaching here is exactly T3's case.
        val viewModel = ShellViewModel()
        viewModel.onInputChange("zzzzzz")

        viewModel.onSubmit()

        assertEquals(
            "no match for 'zzzzzz' — type :help or ? for commands",
            viewModel.transcript.entries[1].text,
        )
    }

    @Test
    fun `multi-word input with no match gets T2's response instead of T3's`() {
        // Design doc §4.2: whitespace with no T0/T1 hit skips straight to T2 — multi-word input
        // is almost always prose, not an app name T3's search would have any better luck with.
        val viewModel = ShellViewModel()
        viewModel.onInputChange("call mom tonight")

        viewModel.onSubmit()

        assertEquals(
            "natural language needs the SLM (not built yet) — type :help or ? for commands",
            viewModel.transcript.entries[1].text,
        )
    }

    // --- auto-space, across the whole registry ---------------------------------------------------
    //
    // T140 generalised auto-space-on-completion to every registered token, but only ":apps" was
    // ever tested for it. These run the same checks over the registry, so a token added later is
    // covered the moment it's registered.

    @Test
    fun `every registered token auto-appends a separating space when typed forward`() {
        reservedCommandTokens.forEach { token ->
            val viewModel = ShellViewModel()
            viewModel.onInputChange(token.dropLast(1))

            viewModel.onInputChange(token)

            assertEquals("'$token' didn't auto-space", "$token ", viewModel.input)
        }
    }

    @Test
    fun `backspacing down to any token does not re-add the space`() {
        // Without the `value.length > input.length` guard, backspace would be permanently stuck
        // at "<token> " — the space you just deleted would reappear immediately, making it
        // impossible to edit back past it.
        reservedCommandTokens.forEach { token ->
            val viewModel = ShellViewModel()
            viewModel.onInputChange("$token x")

            viewModel.onInputChange(token)

            assertEquals("'$token' re-added its space", token, viewModel.input)
        }
    }

    @Test
    fun `the single-character help shorthand auto-spaces like any other token`() {
        // Worth its own case: "?" is one character, so "typed forward" means growing from an
        // empty string, which is the shortest possible path through that guard.
        val viewModel = ShellViewModel()

        viewModel.onInputChange("?")

        assertEquals("? ", viewModel.input)
    }

    @Test
    fun `text that merely starts with a token is not auto-spaced`() {
        val viewModel = ShellViewModel()

        viewModel.onInputChange(":appsx")

        assertEquals(":appsx", viewModel.input)
    }

    // --- recognition across the registry --------------------------------------------------------

    @Test
    fun `every registered token is recognised as a reserved command`() {
        reservedCommandTokens.forEach { token ->
            val viewModel = ShellViewModel()

            viewModel.onInputChange(token)

            assertNotNull("'$token' wasn't recognised", viewModel.reservedCommand)
        }
    }

    @Test
    fun `plain text is never mistaken for a reserved command`() {
        val viewModel = ShellViewModel()

        listOf("chrome", "alias gm = chrome", "call mom", "", "apps", "help").forEach { input ->
            viewModel.onInputChange(input)
            assertNull("'$input' parsed as a command", viewModel.reservedCommand)
        }
    }

    // --- transitions between modes ----------------------------------------------------------------

    @Test
    fun `switching from another reserved command into apps mode clears the transcript`() {
        // The clear is scoped to *entering* Apps, and arriving from :settings is still entering.
        val viewModel = ShellViewModel()
        viewModel.onInputChange("zzz")
        viewModel.onSubmit()
        viewModel.onInputChange(":settings")
        assertTrue(viewModel.transcript.entries.isNotEmpty())

        viewModel.onInputChange(":apps")

        assertTrue(viewModel.transcript.entries.isEmpty())
    }

    @Test
    fun `leaving apps mode for another reserved command does not restore the transcript`() {
        val viewModel = ShellViewModel()
        viewModel.onInputChange("zzz")
        viewModel.onSubmit()
        viewModel.onInputChange(":apps")

        viewModel.onInputChange(":settings")

        assertTrue(viewModel.transcript.entries.isEmpty())
    }

    @Test
    fun `typing ordinary text never clears the transcript`() {
        val viewModel = ShellViewModel()
        viewModel.onInputChange("zzz")
        viewModel.onSubmit()
        val recorded = viewModel.transcript.entries.size

        viewModel.onInputChange("something")
        viewModel.onInputChange("something else")

        assertEquals(recorded, viewModel.transcript.entries.size)
    }

    @Test
    fun `re-entering apps mode after leaving it clears again`() {
        val viewModel = ShellViewModel()
        viewModel.onInputChange(":apps")
        viewModel.onInputChange("zzz")
        viewModel.onSubmit()
        assertTrue(viewModel.transcript.entries.isNotEmpty())

        viewModel.onInputChange(":apps")

        assertTrue(viewModel.transcript.entries.isEmpty())
    }

    // --- submitting -------------------------------------------------------------------------------

    @Test
    fun `submit is a no-op for every reserved command that ShellScreen intercepts`() {
        // :apps, :settings, :alias and :notif all resolve elsewhere — reaching onSubmit means
        // interception broke, and recording the raw command text would be wrong in every case.
        listOf(":apps chrome", ":settings", ":alias", ":notif").forEach { input ->
            val viewModel = ShellViewModel()
            viewModel.onInputChange(input)

            viewModel.onSubmit()

            assertTrue("'$input' recorded something", viewModel.transcript.entries.isEmpty())
        }
    }

    @Test
    fun `submitting accumulates across several commands`() {
        val viewModel = ShellViewModel()

        listOf("zzz1", "zzz2", "zzz3").forEach {
            viewModel.onInputChange(it)
            viewModel.onSubmit()
        }

        assertEquals(6, viewModel.transcript.entries.size)
        assertEquals("zzz1", viewModel.transcript.entries.first().text)
    }

    @Test
    fun `submitting whitespace-only input records nothing and clears the line`() {
        val viewModel = ShellViewModel()
        viewModel.onInputChange("\t  ")

        viewModel.onSubmit()

        assertTrue(viewModel.transcript.entries.isEmpty())
        assertEquals("", viewModel.input)
    }

    @Test
    fun `help output lists every reserved command`() {
        // Ties the ViewModel's :help path back to the registry — a command that's registered but
        // undocumented fails here, not only in HelpTextTest.
        val viewModel = ShellViewModel()
        viewModel.onInputChange(":help")

        viewModel.onSubmit()

        val output = viewModel.transcript.entries[1].text
        reservedCommandHelp.forEach { entry ->
            assertTrue("help omitted '${entry.usage}'", output.contains(entry.usage))
        }
    }

    // --- alias outcomes ------------------------------------------------------------------------------

    @Test
    fun `onAliasDefined describes an overwrite, naming the previous command`() {
        val viewModel = ShellViewModel()

        viewModel.onAliasDefined(
            "alias gm = firefox",
            DefineAliasResult.Overwritten("gm", "firefox", previousCommand = "chrome"),
        )

        assertEquals("alias 'gm' updated (was: chrome)", viewModel.transcript.entries[1].text)
    }

    @Test
    fun `onAliasDefined reports a rejected self-reference rather than staying silent`() {
        val viewModel = ShellViewModel()

        viewModel.onAliasDefined("alias gm = gm", DefineAliasResult.RejectedSelfReference("gm"))

        assertEquals("can't alias 'gm' to itself", viewModel.transcript.entries[1].text)
        assertEquals("", viewModel.input)
    }

    @Test
    fun `onAliasDefined reports a rejected alias-to-alias definition`() {
        val viewModel = ShellViewModel()

        viewModel.onAliasDefined(
            "alias b = a",
            DefineAliasResult.RejectedPointsAtAlias("b", targetAliasName = "a"),
        )

        assertTrue(viewModel.transcript.entries[1].text.contains("can't point at other aliases"))
    }

    @Test
    fun `expanding an alias into apps mode clears an existing transcript`() {
        // The expansion goes through onInputChange, so it inherits the enter-apps-mode clear —
        // an alias to :apps has to behave exactly like typing :apps.
        val viewModel = ShellViewModel()
        viewModel.onInputChange("zzz")
        viewModel.onSubmit()

        viewModel.onAliasExpanded(":apps")

        assertTrue(viewModel.transcript.entries.isEmpty())
        assertEquals(":apps ", viewModel.input)
    }

    @Test
    fun `expanding an alias to unmatched plain text still gets a pipeline response`() {
        // The expansion is dispatched, not just pasted into the field — so it lands on the T3
        // fallback like any other unmatched input rather than silently doing nothing.
        val viewModel = ShellViewModel()
        viewModel.onInputChange("b")

        viewModel.onAliasExpanded("zzzznotanapp")

        assertEquals("zzzznotanapp", viewModel.transcript.entries[0].text)
        assertTrue(viewModel.transcript.entries[1].text.contains(":help"))
    }
}
