package com.flambeauriver.dailydriver.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** T172 — what `:google` sends to the browser. */
class WebSearchTest {

    @Test
    fun `a query becomes a google search url`() {
        assertEquals(
            "https://www.google.com/search?q=flat+white",
            googleSearchUrl("flat white"),
        )
    }

    @Test
    fun `characters that would break the url are encoded`() {
        // The whole reason this function is separate and tested: "c++ &" pasted straight into a
        // query string silently searches for something else.
        val url = googleSearchUrl("c++ & ruby?")

        assertTrue(url, url!!.startsWith("https://www.google.com/search?q="))
        assertTrue(url, url.contains("%2B%2B"))
        assertTrue(url, url.contains("%26"))
        assertTrue(url, url.contains("%3F"))
    }

    @Test
    fun `non-ascii is encoded rather than dropped`() {
        val url = googleSearchUrl("Gemüse")

        assertTrue(url, url!!.contains("Gem%C3%BCse"))
    }

    @Test
    fun `an empty query has nowhere to go`() {
        assertNull(googleSearchUrl(""))
        assertNull(googleSearchUrl("   "))
    }

    @Test
    fun `surrounding whitespace is trimmed rather than encoded`() {
        assertEquals("https://www.google.com/search?q=coffee", googleSearchUrl("  coffee  "))
    }

    @Test
    fun `the transcript echoes the query, not the url`() {
        // Confirming what was searched for is useful; confirming how it was percent-escaped is not.
        assertEquals("searched: flat white", searchedText("  flat white  "))
    }
}
