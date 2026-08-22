package com.flambeauriver.dailydriver.command

import com.flambeauriver.dailydriver.command.CommandResolution.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** T014 — classifying what the deterministic tiers did, for the training log. */
class CommandResolutionTest {

    @Test
    fun `a reserved command is a T0 hit on its token`() {
        assertEquals(
            CommandResolution(Tier.T0, ":apps"),
            classifyResolution(reservedToken = ":apps", aliasExpansion = null, appPackage = null),
        )
    }

    @Test
    fun `an alias is a T0 hit on what it expands to`() {
        assertEquals(
            CommandResolution(Tier.T0, "spotify"),
            classifyResolution(reservedToken = null, aliasExpansion = "spotify", appPackage = null),
        )
    }

    @Test
    fun `a fuzzy app match is a T1 hit on the package`() {
        assertEquals(
            CommandResolution(Tier.T1, "com.spotify.music"),
            classifyResolution(reservedToken = null, aliasExpansion = null, appPackage = "com.spotify.music"),
        )
    }

    @Test
    fun `nothing matching is T3 with an empty target`() {
        val resolution = classifyResolution(reservedToken = null, aliasExpansion = null, appPackage = null)

        assertEquals(Tier.T3, resolution.tier)
        assertEquals(CommandResolution.UNRESOLVED, resolution.target)
    }

    @Test
    fun `the cascade order is respected when several could apply`() {
        // Mirrors §4.2: exact command, then alias, then fuzzy. If the log disagreed with the
        // cascade, it would record an intent the launcher never acted on.
        assertEquals(
            Tier.T0,
            classifyResolution(":apps", "spotify", "com.spotify.music").tier,
        )
        assertEquals(
            "spotify",
            classifyResolution(null, "spotify", "com.spotify.music").target,
        )
    }

    // --- which token the log records ---------------------------------------------------------------

    @Test
    fun `the token is recorded without its argument`() {
        // ":note standup" and ":note groceries" are one command with different slots. Collapsing
        // them is what makes the log a frequency table rather than a list of every note title.
        assertEquals(":note", reservedCommandToken(":note standup"))
        assertEquals(":note", reservedCommandToken(":note groceries"))
        assertEquals(":note", reservedCommandToken(":note"))
    }

    @Test
    fun `every registered command yields its own token`() {
        // Property test, in the registry style this module already uses: a newly registered
        // command is automatically checked for being loggable, rather than silently collecting
        // no training data.
        reservedCommandTokens.forEach { token ->
            assertEquals("'$token' did not resolve to itself", token, reservedCommandToken(token))
        }
    }

    @Test
    fun `input that is not a command has no token`() {
        assertNull(reservedCommandToken("spotify"))
        assertNull(reservedCommandToken("remind me to call the dentist"))
        assertNull(reservedCommandToken(""))
    }
}
