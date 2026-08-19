package com.example.dailydriver.command

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpTextTest {

    // --- the pure formatter, against synthetic entries ------------------------------------------

    @Test
    fun `usages are padded to a common width so descriptions line up in a monospace column`() {
        val entries = listOf(
            CommandHelp(":a", "short one"),
            CommandHelp(":longest [x]", "the wide one"),
        )

        assertEquals(
            ":a            short one\n" +
                ":longest [x]  the wide one",
            formatCommandHelp(entries),
        )
    }

    @Test
    fun `a single entry needs no padding beyond the separator`() {
        assertEquals(":only  does a thing", formatCommandHelp(listOf(CommandHelp(":only", "does a thing"))))
    }

    @Test
    fun `no entries formats to an empty string rather than a stray separator`() {
        assertEquals("", formatCommandHelp(emptyList()))
    }

    // --- the real help text ----------------------------------------------------------------------

    @Test
    fun `help lists every registered command that opted into being documented`() {
        val help = helpText()

        reservedCommandHelp.forEach { entry ->
            assertTrue("missing '${entry.usage}'", help.contains(entry.usage))
            assertTrue("missing '${entry.description}'", help.contains(entry.description))
        }
    }

    @Test
    fun `help documents both help spellings on one line, not as two rows`() {
        assertTrue(helpText().contains(":help, ?"))
    }

    @Test
    fun `help covers what the launcher does beyond reserved commands`() {
        // Aliases and bare-name fuzzy launching are most of day-to-day use — a command-only
        // listing would document the smaller half of the app.
        val help = helpText()

        assertTrue(help.contains("alias <name> = <command>"))
        assertTrue(help.contains("app name"))
    }
}
