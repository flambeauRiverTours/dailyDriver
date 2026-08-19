package com.example.dailydriver.alias

import org.junit.Assert.assertEquals
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
}
