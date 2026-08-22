package com.flambeauriver.dailydriver.alias

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AliasListingTest {

    @Test
    fun `no aliases defined gets a plain message, not an empty string`() {
        assertEquals("no aliases defined", emptyMap<String, String>().formatAliasListing())
    }

    @Test
    fun `a single alias is shown as name equals command, matching definition syntax`() {
        assertEquals("gm = chrome", mapOf("gm" to "chrome").formatAliasListing())
    }

    @Test
    fun `multiple aliases are sorted alphabetically by name, one per line`() {
        // Map iteration order isn't something to depend on — sort explicitly so the listing is
        // stable and testable regardless of what backs the map.
        val aliases = mapOf("maps" to "google maps", "a" to ":apps", "gm" to "chrome")

        assertEquals("a = :apps\ngm = chrome\nmaps = google maps", aliases.formatAliasListing())
    }

    // --- sorting ------------------------------------------------------------------------------

    @Test
    fun `sorting ignores case rather than grouping capitals ahead of lowercase`() {
        // The original toSortedMap() used codepoint order, which put every capitalised name
        // before every lowercase one — "Zoo" ahead of "apple". Alias names are user-chosen and
        // their casing is incidental, so that grouping was noise, and it disagreed with how
        // AppIndex orders app labels.
        val aliases = mapOf("Zoo" to "z", "apple" to "a", "Banana" to "b")

        assertEquals("apple = a\nBanana = b\nZoo = z", aliases.formatAliasListing())
    }

    @Test
    fun `names differing only by case keep a stable relative order`() {
        val aliases = linkedMapOf("GM" to "first", "gm" to "second")

        assertEquals("GM = first\ngm = second", aliases.formatAliasListing())
    }

    @Test
    fun `digits sort ahead of letters`() {
        val aliases = mapOf("b" to "two", "1" to "one")

        assertEquals("1 = one\nb = two", aliases.formatAliasListing())
    }

    @Test
    fun `input map order never leaks into the listing`() {
        // Same content, two insertion orders, one output.
        val forwards = linkedMapOf("a" to "1", "b" to "2", "c" to "3")
        val backwards = linkedMapOf("c" to "3", "b" to "2", "a" to "1")

        assertEquals(forwards.formatAliasListing(), backwards.formatAliasListing())
    }

    // --- content ------------------------------------------------------------------------------

    @Test
    fun `a command containing its own equals sign is shown verbatim`() {
        // The listing uses " = " as the separator but doesn't escape — the first one is the
        // separator and the rest are just text, which is how the definition syntax reads too.
        val aliases = mapOf("q" to "search q=kotlin")

        assertEquals("q = search q=kotlin", aliases.formatAliasListing())
    }

    @Test
    fun `a command that is a reserved command is shown as-is`() {
        assertEquals("a = :apps", mapOf("a" to ":apps").formatAliasListing())
    }

    @Test
    fun `non-ASCII names and commands survive`() {
        assertEquals("café = 日本語", mapOf("café" to "日本語").formatAliasListing())
    }

    @Test
    fun `one line per alias, however many there are`() {
        val aliases = (1..20).associate { "a%02d".format(it) to "command$it" }

        assertEquals(20, aliases.formatAliasListing().lines().size)
    }

    @Test
    fun `the listing has no leading or trailing blank lines`() {
        val listing = mapOf("a" to "1", "b" to "2").formatAliasListing()

        assertEquals(listing.trim(), listing)
    }

    @Test
    fun `the empty message is a single line, not a blank one`() {
        val listing = emptyMap<String, String>().formatAliasListing()

        assertEquals(1, listing.lines().size)
        assertEquals(listing.trim(), listing)
    }

    @Test
    fun `formatting is pure — repeated calls agree`() {
        val aliases = mapOf("b" to "2", "a" to "1")

        assertEquals(aliases.formatAliasListing(), aliases.formatAliasListing())
    }

    @Test
    fun `every line reads as name space equals space command`() {
        val aliases = mapOf("gm" to "chrome", "a" to ":apps")

        aliases.formatAliasListing().lines().forEach { line ->
            assertTrue("'$line' isn't in name = command form", line.contains(" = "))
        }
    }
}
