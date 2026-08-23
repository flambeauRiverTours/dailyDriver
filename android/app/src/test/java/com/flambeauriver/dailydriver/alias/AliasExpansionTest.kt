package com.flambeauriver.dailydriver.alias

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** T173 — an alias carries whatever was typed after it. */
class AliasExpansionTest {

    private val aliases = mapOf(
        "a" to ":apps",
        "s" to "Spotify",
        "n" to ":note",
        "long name" to ":tasks",
    )

    @Test
    fun `a bare alias still expands`() {
        assertEquals(":apps", expandAlias("a", aliases))
    }

    @Test
    fun `an argument is appended`() {
        assertEquals(":apps firefox", expandAlias("a firefox", aliases))
    }

    @Test
    fun `several words are all appended`() {
        assertEquals(":note standup notes for monday", expandAlias("n standup notes for monday", aliases))
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertEquals(":apps firefox", expandAlias("  a firefox  ", aliases))
        assertEquals(":apps", expandAlias("  a  ", aliases))
    }

    @Test
    fun `an alias followed only by spaces is the bare alias`() {
        // "a " is mid-typing, not an empty argument. Appending nothing keeps auto-space in charge
        // of what comes next.
        assertEquals(":apps", expandAlias("a ", aliases))
    }

    @Test
    fun `an app alias appends too`() {
        // Decided 2026-08-22: the rule is uniform. "Spotify foo" matches no app and lands at T3,
        // which is the user's own doing for passing an argument to an alias that takes none. A
        // rule that declines to expand in some cases is harder to hold in your head.
        assertEquals("Spotify foo", expandAlias("s foo", aliases))
    }

    @Test
    fun `an unknown first token expands to nothing`() {
        assertNull(expandAlias("zzz firefox", aliases))
        assertNull(expandAlias("zzz", aliases))
    }

    @Test
    fun `a longer word starting with an alias name is not an alias`() {
        // "alarm" begins with "a" and must reach T1's fuzzy matching untouched. Matching on the
        // token rather than the prefix is what keeps every longer input reachable.
        assertNull(expandAlias("alarm", aliases))
    }

    @Test
    fun `empty input expands to nothing`() {
        assertNull(expandAlias("", aliases))
        assertNull(expandAlias("   ", aliases))
    }

    @Test
    fun `no aliases means no expansion`() {
        assertNull(expandAlias("a firefox", emptyMap()))
    }

    @Test
    fun `a multi-word alias key still matches as a whole`() {
        // The whole-string lookup runs first, so a key containing a space is reachable even though
        // the token split could never find it.
        assertEquals(":tasks", expandAlias("long name", aliases))
    }

    @Test
    fun `interior spacing in the argument is preserved`() {
        // A :note title or :task description is entitled to its own spacing; only the alias name
        // is consumed.
        assertEquals(":note a  b", expandAlias("n a  b", aliases))
    }

    @Test
    fun `a reserved command is never treated as an alias`() {
        // Alias names cannot start with ":", so this can only miss — pinned so a future change to
        // alias naming has to think about it.
        assertNull(expandAlias(":apps firefox", aliases))
    }
}
