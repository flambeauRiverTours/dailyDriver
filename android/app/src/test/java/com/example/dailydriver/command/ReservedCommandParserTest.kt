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
        // :settings/:note/:alias/:help are real T0 commands per the design doc, but registering
        // their behavior is T142-T145's job, not T140's — until then they're just unrecognized
        // input, falling through exactly like any other text.
        assertNull(parseReservedCommand(":settings"))
    }

    @Test
    fun `every registered token is exposed for callers like the auto-space behavior`() {
        assertTrue(APPS_TOKEN in reservedCommandTokens)
    }
}
