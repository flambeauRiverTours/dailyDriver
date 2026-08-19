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

    // --- alignment as a property, not a golden string -------------------------------------------

    @Test
    fun `every description starts at the same column, whatever the usages are`() {
        // The reason for padding at all. Asserted as an invariant so it keeps holding when a
        // command with a longer usage is registered, rather than only for today's set.
        val entries = listOf(
            CommandHelp(":a", "one"),
            CommandHelp(":bbbbbbbb", "two"),
            CommandHelp(":ccc [x]", "three"),
        )

        val lines = formatCommandHelp(entries).lines()
        val descriptionStarts = entries.mapIndexed { i, entry -> lines[i].indexOf(entry.description) }

        assertEquals(1, descriptionStarts.distinct().size)
    }

    @Test
    fun `the real registry listing is aligned too`() {
        val lines = formatCommandHelp(reservedCommandHelp).lines()
        val descriptionStarts = reservedCommandHelp.mapIndexed { i, entry ->
            lines[i].indexOf(entry.description)
        }

        assertEquals(1, descriptionStarts.distinct().size)
    }

    @Test
    fun `equal-length usages need no padding beyond the separator`() {
        val entries = listOf(CommandHelp(":aa", "one"), CommandHelp(":bb", "two"))

        assertEquals(":aa  one\n:bb  two", formatCommandHelp(entries))
    }

    @Test
    fun `one line out per entry, in the order given`() {
        val entries = listOf(CommandHelp(":z", "last"), CommandHelp(":a", "first"))

        // Not sorted — the registry's own order is the documented order, so :apps stays first
        // rather than being alphabetised away from its position in the cascade.
        assertEquals(listOf(":z  last", ":a  first"), formatCommandHelp(entries).lines())
    }

    @Test
    fun `an empty description still produces a well-formed row`() {
        assertEquals(":a  ", formatCommandHelp(listOf(CommandHelp(":a", ""))))
    }

    // --- registry-backed invariants ---------------------------------------------------------------

    @Test
    fun `something is actually documented`() {
        // Guards the degenerate case where every entry gets help = null and helpText silently
        // becomes just its footer.
        assertTrue(reservedCommandHelp.isNotEmpty())
    }

    @Test
    fun `no command is documented twice`() {
        val usages = reservedCommandHelp.map { it.usage }

        assertEquals(usages.distinct(), usages)
    }

    @Test
    fun `every documented command says what it does`() {
        reservedCommandHelp.forEach { entry ->
            assertTrue("'${entry.usage}' has no description", entry.description.isNotBlank())
            assertTrue("'${entry.usage}' has a blank usage", entry.usage.isNotBlank())
        }
    }

    @Test
    fun `no help row is padded on the right — trailing spaces are invisible clutter`() {
        formatCommandHelp(reservedCommandHelp).lines().forEach { line ->
            assertEquals(line.trimEnd(), line)
        }
    }

    @Test
    fun `help neither starts nor ends with a blank line`() {
        val help = helpText()

        assertEquals(help.trim(), help)
    }

    @Test
    fun `the footer is separated from the command list by exactly one blank line`() {
        val lines = helpText().lines()
        val blankIndices = lines.indices.filter { lines[it].isBlank() }

        assertEquals(listOf(reservedCommandHelp.size), blankIndices)
    }

    @Test
    fun `help is pure — repeated calls agree`() {
        assertEquals(helpText(), helpText())
    }
}
