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
        // This is also what keeps the registry's ordering from mattering. No two registered
        // tokens are currently prefixes of each other, but requiring an exact match or
        // token-then-space means a future ":notes" alongside ":note" still wouldn't shadow it,
        // and first-match-wins would stay an implementation detail rather than a rule to obey.
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

    // --- registry-wide invariants -------------------------------------------------------------
    //
    // These don't test any one command — they're guardrails for the NEXT command someone
    // registers. T141-T145 and T147 each added an entry by copying the one above it, which is
    // exactly the workflow that quietly introduces a duplicate or a token that doesn't dispatch.

    @Test
    fun `no token is registered twice`() {
        assertEquals(reservedCommandTokens.distinct(), reservedCommandTokens)
    }

    @Test
    fun `no token is blank or carries stray whitespace`() {
        reservedCommandTokens.forEach { token ->
            assertTrue("'$token' is blank", token.isNotBlank())
            assertEquals("'$token' has stray whitespace", token.trim(), token)
        }
    }

    @Test
    fun `every registered token actually dispatches`() {
        // A token in the list that parseReservedCommand rejects would be invisible everywhere
        // except ShellViewModel's auto-space, which would then insert a space after a
        // meaningless prefix.
        reservedCommandTokens.forEach { token ->
            assertTrue("'$token' is registered but doesn't parse", parseReservedCommand(token) != null)
        }
    }

    @Test
    fun `only the help shorthand is allowed to skip the colon prefix`() {
        // The `:` prefix is what keeps reserved commands and user aliases in disjoint namespaces
        // (see AliasDefinition's rejection of names starting with `:`). `?` is the documented
        // exception; a second bare token would reopen that precedence question.
        val bare = reservedCommandTokens.filterNot { it.startsWith(":") }

        assertEquals(listOf(HELP_SHORTHAND), bare)
    }

    @Test
    fun `each token resolves to its own command, except the two help spellings`() {
        // The registry is first-match-wins and ordered, so two tokens collapsing to one variant
        // is only correct when it's deliberate. Grouping catches an accidental copy-paste that
        // left the wrong builder behind.
        val byVariant = reservedCommandTokens.groupBy { parseReservedCommand(it) }
        val sharedVariants = byVariant.filterValues { it.size > 1 }

        assertEquals(mapOf(ReservedCommand.Help to listOf(HELP_TOKEN, HELP_SHORTHAND)), sharedVariants)
    }

    @Test
    fun `parsing is pure — the same input gives the same result every time`() {
        repeat(3) {
            assertEquals(ReservedCommand.Apps("chrome"), parseReservedCommand(":apps chrome"))
            assertEquals(ReservedCommand.Help, parseReservedCommand("?"))
        }
    }

    // --- token/query boundary cases -------------------------------------------------------------

    @Test
    fun `a bare colon matches nothing`() {
        assertNull(parseReservedCommand(":"))
    }

    @Test
    fun `leading whitespace defeats the match — the token has to start the input`() {
        // ShellViewModel never trims before parsing, so this is the real input shape, and a
        // leading space genuinely means the user hasn't typed a command.
        assertNull(parseReservedCommand(" :apps"))
        assertNull(parseReservedCommand("\t:apps"))
    }

    @Test
    fun `a tab after the token is not a separator — only a space is`() {
        assertNull(parseReservedCommand(":apps\tchrome"))
    }

    @Test
    fun `internal spacing inside a query is preserved, not collapsed`() {
        // The query is a live filter string; collapsing runs of spaces would silently change
        // what the user is searching for.
        assertEquals(ReservedCommand.Apps("google  maps"), parseReservedCommand(":apps google  maps"))
    }

    @Test
    fun `a query can contain punctuation and symbols without breaking the parse`() {
        assertEquals(ReservedCommand.Apps("a+b (c) [d]"), parseReservedCommand(":apps a+b (c) [d]"))
    }

    @Test
    fun `a query can contain a colon, which would otherwise look like another command`() {
        assertEquals(ReservedCommand.Apps(":settings"), parseReservedCommand(":apps :settings"))
    }

    @Test
    fun `a query can contain non-ASCII text`() {
        assertEquals(ReservedCommand.Apps("café 日本"), parseReservedCommand(":apps café 日本"))
    }

    @Test
    fun `a long query is carried through intact rather than truncated`() {
        val long = "x".repeat(500)

        assertEquals(ReservedCommand.Apps(long), parseReservedCommand(":apps $long"))
    }

    @Test
    fun `a question mark inside a query is not the help shorthand`() {
        // "?" only means help as the whole input — otherwise searching for "what?" would be
        // impossible.
        assertEquals(ReservedCommand.Apps("what?"), parseReservedCommand(":apps what?"))
    }
}
