package com.example.dailydriver.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppMatchingTest {

    private val chrome = AppEntry("com.android.chrome", "Chrome")
    private val calculator = AppEntry("com.android.calculator2", "Calculator")
    private val calendar = AppEntry("com.android.calendar", "Calendar")
    private val camera = AppEntry("com.android.camera", "Camera")
    private val googleMaps = AppEntry("com.google.maps", "Google Maps")
    private val payPal = AppEntry("com.paypal", "PayPal")
    private val settings = AppEntry("com.android.settings", "Settings")

    private val apps = listOf(chrome, calculator, calendar, camera, googleMaps, payPal, settings)

    private fun topMatch(query: String) = apps.rankedMatches(query).firstOrNull()

    // --- the design doc's own worked example ------------------------------------------------

    @Test
    fun `chr resolves to Chrome — design doc section 4_2's example`() {
        val top = topMatch("chr")

        assertEquals(chrome, top?.entry)
        assertEquals(MatchStrategy.PREFIX, top?.strategy)
    }

    // --- each strategy on the ladder ---------------------------------------------------------

    @Test
    fun `an exact label match scores 1_0`() {
        val top = topMatch("chrome")

        assertEquals(chrome, top?.entry)
        assertEquals(MatchStrategy.EXACT, top?.strategy)
        assertEquals(1.0, top!!.score, 0.0001)
    }

    @Test
    fun `acronym matches initials across words`() {
        val top = topMatch("gm")

        assertEquals(googleMaps, top?.entry)
        assertEquals(MatchStrategy.ACRONYM, top?.strategy)
    }

    @Test
    fun `word prefix matches a later word, not just the first`() {
        val top = topMatch("maps")

        assertEquals(googleMaps, top?.entry)
        assertEquals(MatchStrategy.WORD_PREFIX, top?.strategy)
    }

    @Test
    fun `substring matches mid-label`() {
        val top = topMatch("oogle")

        assertEquals(googleMaps, top?.entry)
        assertEquals(MatchStrategy.SUBSTRING, top?.strategy)
    }

    @Test
    fun `subsequence matches scattered characters in order`() {
        val top = topMatch("chrm")

        assertEquals(chrome, top?.entry)
        assertEquals(MatchStrategy.SUBSEQUENCE, top?.strategy)
    }

    @Test
    fun `a transposed typo is rescued by edit distance`() {
        val top = topMatch("chorme")

        assertEquals(chrome, top?.entry)
        assertEquals(MatchStrategy.TYPO, top?.strategy)
    }

    @Test
    fun `camelCase splits into words, so initials of a single-token label still match`() {
        // "PayPal" -> [pay, pal] -> initials "pp". Deliberately a label whose initials differ
        // from its own prefix: "wa" for WhatsApp would just be a PREFIX hit and prove nothing
        // about camelCase splitting.
        val top = topMatch("pp")

        assertEquals(payPal, top?.entry)
        assertEquals(MatchStrategy.ACRONYM, top?.strategy)
    }

    // --- ordering between strategies ---------------------------------------------------------

    @Test
    fun `a prefix match outranks a subsequence match on the same query`() {
        // "ca" is a prefix of Calculator/Calendar/Camera, and also a subsequence of others.
        val ranked = apps.rankedMatches("ca")
        val prefixes = ranked.takeWhile { it.strategy == MatchStrategy.PREFIX }

        assertTrue(prefixes.size >= 3)
        assertTrue(ranked.drop(prefixes.size).none { it.strategy == MatchStrategy.PREFIX })
    }

    @Test
    fun `scores are returned in descending order`() {
        val scores = apps.rankedMatches("ca").map { it.score }

        assertEquals(scores.sortedDescending(), scores)
    }

    // --- within-band ordering by coverage -----------------------------------------------------

    @Test
    fun `among equal-strategy matches, a shorter label ranks higher`() {
        // All three are prefix matches for "ca"; Camera (6) is a tighter fit than Calculator (10).
        val ranked = apps.rankedMatches("ca").filter { it.strategy == MatchStrategy.PREFIX }

        assertEquals(listOf(camera, calendar, calculator), ranked.map { it.entry })
    }

    // --- non-matches ---------------------------------------------------------------------------

    @Test
    fun `a query matching nothing returns no matches`() {
        assertTrue(apps.rankedMatches("zzzzzz").isEmpty())
    }

    @Test
    fun `a blank query returns no matches rather than everything`() {
        assertTrue(apps.rankedMatches("").isEmpty())
        assertTrue(apps.rankedMatches("   ").isEmpty())
    }

    @Test
    fun `matching is case-insensitive in both directions`() {
        assertEquals(chrome, topMatch("CHROME")?.entry)
        assertEquals(chrome, topMatch("ChRoMe")?.entry)
    }

    @Test
    fun `surrounding whitespace in the query is ignored`() {
        assertEquals(chrome, topMatch("  chr  ")?.entry)
    }

    // --- topMatch (what Enter / a pause acts on) ------------------------------------------------

    @Test
    fun `topMatch is the best-ranked entry, unambiguous case`() {
        assertEquals(chrome, apps.topMatch("chr"))
    }

    @Test
    fun `topMatch wins even among near-tied candidates — best guess, not a refusal to guess`() {
        // Calculator / Calendar / Camera all score within a hair of each other. Reversed from the
        // original design (which listed candidates instead) per explicit request: the preview
        // line's "+N more" is the disambiguation signal now, not a gate on launching at all.
        assertEquals(camera, apps.topMatch("ca"))
    }

    @Test
    fun `topMatch is null only when nothing matched at all`() {
        assertNull(apps.topMatch("zzzzzz"))
        assertNull(apps.topMatch(""))
    }

    // --- preview line ---------------------------------------------------------------------------

    @Test
    fun `preview names the single match on its own`() {
        assertEquals("Chrome", apps.matchPreview("chrome"))
    }

    @Test
    fun `preview reports the runner-up count, so you can tell Enter won't act`() {
        val preview = apps.matchPreview("ca")

        assertTrue(preview!!.startsWith("Camera"))
        assertTrue(preview.contains("more"))
    }

    @Test
    fun `nothing to preview for a blank query or a query that matches nothing`() {
        assertNull(apps.matchPreview(""))
        assertNull(apps.matchPreview("zzzzzz"))
    }

    @Test
    fun `even a weak subsequence-only match is actionable via topMatch when it's the only one`() {
        // No score floor anymore — a lone SUBSEQUENCE hit still wins, since the user asked for
        // the best match to always be what Enter/a pause acts on.
        val weak = listOf(AppEntry("com.x", "Xylophone Tuner Deluxe"))

        assertEquals(MatchStrategy.SUBSEQUENCE, weak.rankedMatches("xye").first().strategy)
        assertEquals(weak.first(), weak.topMatch("xye"))
    }
}
