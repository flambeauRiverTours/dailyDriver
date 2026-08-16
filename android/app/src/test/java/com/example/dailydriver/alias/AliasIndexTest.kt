package com.example.dailydriver.alias

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
}
