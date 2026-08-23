package com.flambeauriver.dailydriver.search

import java.net.URLEncoder

/**
 * Building a Google search URL for `:google` (T172).
 *
 * Pure and separate from [WebSearchLauncher] for the usual reason: the encoding is the part that
 * can be wrong in a way you would not notice, and it needs no device to test.
 */

/** Where a bare `:google` with no query should land — nowhere. */
fun googleSearchUrl(query: String): String? {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return null
    return "https://www.google.com/search?q=" + URLEncoder.encode(trimmed, "UTF-8")
}

/**
 * What the transcript says once the browser has been handed the search.
 *
 * Echoes the query rather than the URL. The URL is percent-encoded and long, and the thing worth
 * confirming is *what was searched for*, not how it was escaped.
 */
fun searchedText(query: String): String = "searched: ${query.trim()}"
