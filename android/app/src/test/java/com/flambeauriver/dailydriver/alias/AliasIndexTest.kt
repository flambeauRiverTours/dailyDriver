package com.flambeauriver.dailydriver.alias

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AliasIndexTest {

    @Test
    fun `empty before any refresh`() {
        assertEquals(emptyMap<String, String>(), AliasIndex().asMap())
    }

    @Test
    fun `replaceAll makes aliases available as a name to command map`() {
        val index = AliasIndex()

        index.replaceAll(listOf(Alias("gm", "chrome")))

        assertEquals(mapOf("gm" to "chrome"), index.asMap())
    }

    @Test
    fun `replaceAll wholesale replaces prior entries, not merges`() {
        val index = AliasIndex()
        index.replaceAll(listOf(Alias("a", "1")))

        index.replaceAll(listOf(Alias("b", "2")))

        assertEquals(mapOf("b" to "2"), index.asMap())
    }

    @Test
    fun `resolve is an exact match on the trigger — T0's tight latency budget, no fuzzy matching here`() {
        val index = AliasIndex()
        index.replaceAll(listOf(Alias("gm", "chrome")))

        assertEquals("chrome", index.resolve("gm"))
        assertNull(index.resolve("g"))
        assertNull(index.resolve("gm "))
    }

    @Test
    fun `resolve of an unknown trigger is null`() {
        assertNull(AliasIndex().resolve("gm"))
    }

    // --- more index behaviour ---------------------------------------------------------------

    @Test
    fun `replacing with nothing empties the index`() {
        // Deleting your last alias has to actually clear it, not leave the previous set live.
        val index = AliasIndex()
        index.replaceAll(listOf(Alias("gm", "chrome")))

        index.replaceAll(emptyList())

        assertEquals(emptyMap<String, String>(), index.asMap())
    }

    @Test
    fun `duplicate names collapse to the last one seen`() {
        // Room's primary key makes this unreachable in practice; asserted so the in-memory index
        // has defined behaviour rather than depending on that guarantee holding forever.
        val index = AliasIndex()

        index.replaceAll(listOf(Alias("gm", "chrome"), Alias("gm", "firefox")))

        assertEquals(mapOf("gm" to "firefox"), index.asMap())
    }

    @Test
    fun `resolution is case-sensitive, matching T0's exact-match contract`() {
        val index = AliasIndex()
        index.replaceAll(listOf(Alias("gm", "chrome")))

        assertNull(index.resolve("GM"))
        assertNull(index.resolve("Gm"))
    }

    @Test
    fun `an alias whose command is a reserved command resolves to that text verbatim`() {
        // The case that proved deferring resolution was wrong at T007 — ":apps" already works,
        // so an alias pointing at it is useful the moment it exists.
        val index = AliasIndex()
        index.replaceAll(listOf(Alias("a", ":apps")))

        assertEquals(":apps", index.resolve("a"))
    }

    @Test
    fun `many aliases are all reachable`() {
        val index = AliasIndex()
        val aliases = (1..50).map { Alias("a$it", "command$it") }

        index.replaceAll(aliases)

        assertEquals(50, index.asMap().size)
        assertEquals("command37", index.resolve("a37"))
    }

    @Test
    fun `the map exposed earlier is not mutated by a later replaceAll`() {
        val index = AliasIndex()
        index.replaceAll(listOf(Alias("gm", "chrome")))
        val snapshot = index.asMap()

        index.replaceAll(listOf(Alias("other", "firefox")))

        assertEquals(mapOf("gm" to "chrome"), snapshot)
    }

    @Test
    fun `resolve rejects a trigger with surrounding whitespace rather than trimming it`() {
        // Trimming here would silently diverge from ShellScreen, which does its own trim before
        // looking anything up — two places deciding what counts as a match is a bug waiting.
        val index = AliasIndex()
        index.replaceAll(listOf(Alias("gm", "chrome")))

        assertNull(index.resolve(" gm"))
        assertNull(index.resolve("gm\t"))
    }

    @Test
    fun `an empty trigger resolves to nothing`() {
        val index = AliasIndex()
        index.replaceAll(listOf(Alias("gm", "chrome")))

        assertNull(index.resolve(""))
    }
}
