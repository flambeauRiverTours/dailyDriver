package com.example.dailydriver.command

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReservedCommandParserTest {

    @Test
    fun `bare colon-apps parses to the Apps variant with an empty query`() {
        assertEquals(ReservedCommand.Apps(""), parseReservedCommand(":apps"))
    }

    @Test
    fun `colon-apps followed by a space and text captures the query`() {
        assertEquals(ReservedCommand.Apps("chrome"), parseReservedCommand(":apps chrome"))
    }

    @Test
    fun `leading whitespace in the query is trimmed`() {
        assertEquals(ReservedCommand.Apps("chrome"), parseReservedCommand(":apps   chrome"))
    }

    @Test
    fun `trailing whitespace in the query is preserved mid-type`() {
        // The user is still typing — don't eagerly trim a trailing space, or a query like
        // "chrome browser" would have its word-boundary space stripped while typing it.
        assertEquals(ReservedCommand.Apps("chrome "), parseReservedCommand(":apps chrome "))
    }

    @Test
    fun `command tokens are case-sensitive, matching the rest of T0`() {
        assertNull(parseReservedCommand(":APPS"))
        assertNull(parseReservedCommand(":Apps chrome"))
    }

    @Test
    fun `not a prefix match — the token must be the whole word, not part of a longer one`() {
        assertNull(parseReservedCommand(":appsfoo"))
    }

    @Test
    fun `unregistered or unrelated input matches nothing`() {
        assertNull(parseReservedCommand("apps"))
        assertNull(parseReservedCommand(""))
        assertNull(parseReservedCommand("chrome"))
        // :help is a real T0 command per the design doc, but registering its behavior is T145's
        // job, not T140's — until then it's just unrecognized input, falling through exactly
        // like any other text. (:settings, :note, and :alias were this same kind of stub until
        // T142/T143/T144 registered them — see the dedicated tests below.)
        assertNull(parseReservedCommand(":help"))
    }

    @Test
    fun `every registered token is exposed for callers like the auto-space behavior`() {
        assertTrue(APPS_TOKEN in reservedCommandTokens)
        assertTrue(SETTINGS_TOKEN in reservedCommandTokens)
        assertTrue(NOTE_TOKEN in reservedCommandTokens)
        assertTrue(ALIAS_TOKEN in reservedCommandTokens)
    }

    // --- T142: :settings -----------------------------------------------------------------------

    @Test
    fun `bare colon-settings parses to the Settings variant`() {
        assertEquals(ReservedCommand.Settings, parseReservedCommand(":settings"))
    }

    @Test
    fun `colon-settings followed by text still resolves to Settings — it has no query to capture`() {
        // Unlike :apps, :settings has nothing to filter. Anything typed after it is ignored
        // rather than rejected — matching the shape every T0 token shares (token, or token +
        // space + more) without inventing special-case parsing just for this one command.
        assertEquals(ReservedCommand.Settings, parseReservedCommand(":settings anything"))
    }

    @Test
    fun `colon-settings is case-sensitive and not a prefix match, like every other T0 token`() {
        assertNull(parseReservedCommand(":Settings"))
        assertNull(parseReservedCommand(":SETTINGS"))
        assertNull(parseReservedCommand(":settingsfoo"))
    }

    // --- T143: :note -----------------------------------------------------------------------

    @Test
    fun `bare colon-note parses to the Note variant with an empty query`() {
        assertEquals(ReservedCommand.Note(""), parseReservedCommand(":note"))
    }

    @Test
    fun `colon-note followed by a space and text captures the query, unlike Settings`() {
        // Unused today (T143 just shows a placeholder), but the design doc's own worked example
        // ("note standup") gives it a name argument, so it's captured like :apps' query rather
        // than discarded like :settings' — see ReservedCommand.Note's doc comment.
        assertEquals(ReservedCommand.Note("standup"), parseReservedCommand(":note standup"))
    }

    @Test
    fun `colon-note is case-sensitive and not a prefix match, like every other T0 token`() {
        assertNull(parseReservedCommand(":Note"))
        assertNull(parseReservedCommand(":NOTE"))
        assertNull(parseReservedCommand(":notefoo"))
    }

    // --- T144: :alias ----------------------------------------------------------------------

    @Test
    fun `bare colon-alias parses to the Alias variant`() {
        assertEquals(ReservedCommand.Alias, parseReservedCommand(":alias"))
    }

    @Test
    fun `colon-alias followed by text still resolves to Alias — it has no query, like Settings`() {
        assertEquals(ReservedCommand.Alias, parseReservedCommand(":alias anything"))
    }

    @Test
    fun `colon-alias is case-sensitive and not a prefix match, like every other T0 token`() {
        assertNull(parseReservedCommand(":Alias"))
        assertNull(parseReservedCommand(":ALIAS"))
        assertNull(parseReservedCommand(":aliasfoo"))
    }
}
