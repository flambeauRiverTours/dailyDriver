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
        // Every reserved command the design doc names is registered now (T141-T145, T147), so
        // what's left here is genuinely unregistered input: a plausible-looking colon token that
        // nothing claims still falls through to T1 like any other text.
        assertNull(parseReservedCommand(":nosuchcommand"))
    }

    @Test
    fun `every registered token is exposed for callers like the auto-space behavior`() {
        assertTrue(APPS_TOKEN in reservedCommandTokens)
        assertTrue(SETTINGS_TOKEN in reservedCommandTokens)
        assertTrue(NOTE_TOKEN in reservedCommandTokens)
        assertTrue(ALIAS_TOKEN in reservedCommandTokens)
        assertTrue(NOTIF_TOKEN in reservedCommandTokens)
        assertTrue(HELP_TOKEN in reservedCommandTokens)
        assertTrue(HELP_SHORTHAND in reservedCommandTokens)
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

    // --- T147: :notif ----------------------------------------------------------------------

    @Test
    fun `bare colon-notif parses to the Notif variant`() {
        assertEquals(ReservedCommand.Notif, parseReservedCommand(":notif"))
    }

    @Test
    fun `colon-notif followed by text still resolves to Notif — no live filter, per design check-in`() {
        assertEquals(ReservedCommand.Notif, parseReservedCommand(":notif anything"))
    }

    @Test
    fun `colon-notif is case-sensitive and not a prefix match, like every other T0 token`() {
        assertNull(parseReservedCommand(":Notif"))
        assertNull(parseReservedCommand(":NOTIF"))
        assertNull(parseReservedCommand(":notiffoo"))
    }

    // --- T145: :help and its bare-? shorthand -------------------------------------------------

    @Test
    fun `both help spellings resolve to the same variant`() {
        assertEquals(ReservedCommand.Help, parseReservedCommand(HELP_TOKEN))
        assertEquals(ReservedCommand.Help, parseReservedCommand(HELP_SHORTHAND))
    }

    @Test
    fun `the bare question mark is a whole token, not a prefix of longer input`() {
        // "?" is the one registered token without a ":" prefix, so it's worth proving it obeys
        // the same whole-token rule — "?x" must not resolve to help.
        assertNull(parseReservedCommand("?x"))
        assertNull(parseReservedCommand("what?"))
    }

    @Test
    fun `help takes no query, so trailing text is discarded rather than rejected`() {
        assertEquals(ReservedCommand.Help, parseReservedCommand(":help me"))
        assertEquals(ReservedCommand.Help, parseReservedCommand("? me"))
    }

    @Test
    fun `every documented command's usage starts with a token the parser actually accepts`() {
        // The point of generating help from the registry is that it can't document something
        // undispatchable — this asserts that property rather than trusting it.
        reservedCommandHelp.forEach { entry ->
            val token = entry.usage.substringBefore(' ').substringBefore(',')
            assertTrue("'$token' is documented but not parseable", parseReservedCommand(token) != null)
        }
    }
}
