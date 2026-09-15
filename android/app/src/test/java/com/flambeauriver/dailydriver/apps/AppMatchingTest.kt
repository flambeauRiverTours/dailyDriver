package com.flambeauriver.dailydriver.apps

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

    private fun topMatch(query: String, over: List<AppEntry> = apps) = over.rankedMatches(query).firstOrNull()

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

    @Test
    fun `even a weak subsequence-only match is actionable when it's the only one`() {
        // No score floor anymore — a lone SUBSEQUENCE hit still wins, since the user asked for
        // the best match to always be what Enter/a pause acts on.
        val weak = listOf(AppEntry("com.x", "Xylophone Tuner Deluxe"))

        assertEquals(MatchStrategy.SUBSEQUENCE, weak.rankedMatches("xye").first().strategy)
        assertEquals(weak.first(), topMatch("xye", weak)?.entry)
    }

    // --- labels the real PackageManager can hand us ---------------------------------------------

    @Test
    fun `an app with a blank label never matches anything`() {
        // matchAgainst bails on an empty label before any strategy runs. PackageManager doesn't
        // guarantee loadLabel() is non-empty, and a blank row that silently matched every query
        // would outrank real apps at the prompt.
        val withBlank = apps + AppEntry("com.mystery", "")

        listOf("c", "chrome", "", "zzz", "a").forEach { query ->
            assertTrue(
                "blank label matched '$query'",
                withBlank.rankedMatches(query).none { it.entry.packageName == "com.mystery" },
            )
        }
    }

    @Test
    fun `an empty app list matches nothing without complaining`() {
        val none = emptyList<AppEntry>()

        assertTrue(none.rankedMatches("chrome").isEmpty())
        assertNull(topMatch("chrome", none))
    }

    @Test
    fun `labels containing digits and symbols still match`() {
        val odd = listOf(AppEntry("com.pw", "1Password"), AppEntry("com.msg", "+Message"))

        assertEquals(odd[0], topMatch("1pass", odd)?.entry)
        assertEquals(odd[1], topMatch("+mes", odd)?.entry)
    }

    @Test
    fun `non-ASCII labels match on their own characters`() {
        val intl = listOf(AppEntry("com.a", "日本語辞書"), AppEntry("com.b", "Café"))

        assertEquals(intl[0], topMatch("日本", intl)?.entry)
        assertEquals(intl[1], topMatch("caf", intl)?.entry)
    }

    // --- score bands ------------------------------------------------------------------------------

    @Test
    fun `every match's score falls inside its own strategy's band`() {
        // The bands are what make the ladder tunable. A strategy scoring outside its range would
        // reorder results in ways none of the per-strategy tests above would notice.
        val ranges = mapOf(
            MatchStrategy.EXACT to 1.0..1.0,
            MatchStrategy.PREFIX to 0.85..0.95,
            MatchStrategy.ACRONYM to 0.80..0.85,
            // TYPO is similarity * 0.80 with a 0.70 similarity floor, so it spans 0.56..0.80 and
            // deliberately overlaps the bands below it — contiguity isn't a property here.
            MatchStrategy.TYPO to 0.56..0.80,
            MatchStrategy.WORD_PREFIX to 0.70..0.78,
            MatchStrategy.SUBSTRING to 0.60..0.68,
            MatchStrategy.SUBSEQUENCE to 0.50..0.58,
        )

        listOf("c", "ca", "chr", "chrome", "gm", "pp", "maps", "oogle", "chrm", "chorme", "set")
            .flatMap { apps.rankedMatches(it) }
            .forEach { match ->
                assertTrue(
                    "${match.strategy} scored ${match.score}, outside ${ranges.getValue(match.strategy)}",
                    match.score in ranges.getValue(match.strategy),
                )
            }
    }

    @Test
    fun `every strategy in the enum is reachable — none is dead code`() {
        val seen = listOf("chrome", "chr", "gm", "chorme", "maps", "oogle", "chrm")
            .flatMap { apps.rankedMatches(it) }
            .map { it.strategy }
            .toSet()

        assertEquals(MatchStrategy.entries.toSet(), seen)
    }

    @Test
    fun `scores never leave the zero-to-one range for any query`() {
        listOf("", " ", "c", "chrome", "zzzz", "日本", "a b c", "x".repeat(50)).forEach { query ->
            apps.rankedMatches(query).forEach { match ->
                assertTrue("'$query' -> ${match.score}", match.score in 0.0..1.0)
            }
        }
    }

    @Test
    fun `an exact match is worth exactly one, not merely near it`() {
        assertEquals(1.0, apps.rankedMatches("settings").first().score, 0.0)
    }

    // --- strategy selection edges -----------------------------------------------------------------

    @Test
    fun `a near-miss below the typo floor drops to a weaker rung rather than scoring as a typo`() {
        // "chrm" is 2 edits from "chrome" — 0.67 similarity, under the 0.70 floor. It has to land
        // on SUBSEQUENCE instead, which is the boundary that keeps TYPO from producing noise.
        assertEquals(MatchStrategy.SUBSEQUENCE, apps.rankedMatches("chrm").first().strategy)
    }

    @Test
    fun `a query longer than the label cannot match it`() {
        val short = listOf(AppEntry("com.x", "Ox"))

        assertTrue(short.rankedMatches("oxygen").isEmpty())
    }

    @Test
    fun `case differences alone still count as an exact match`() {
        val top = apps.rankedMatches("ChRoMe").first()

        assertEquals(MatchStrategy.EXACT, top.strategy)
        assertEquals(1.0, top.score, 0.0001)
    }

    @Test
    fun `a subsequence must appear in order, not merely be present`() {
        // "emo" are all in "Chrome" but not in that order — matching it would make the weakest
        // rung an anagram check.
        val chromeOnly = listOf(chrome)

        assertTrue(chromeOnly.rankedMatches("emo").isEmpty())
        assertEquals(MatchStrategy.SUBSEQUENCE, chromeOnly.rankedMatches("cme").first().strategy)
    }

    @Test
    fun `acronyms work across three or more words`() {
        val long = listOf(AppEntry("com.x", "Very Long App Name"))

        assertEquals(MatchStrategy.ACRONYM, long.rankedMatches("vlan").first().strategy)
    }

    @Test
    fun `a partial acronym prefix still matches`() {
        val long = listOf(AppEntry("com.x", "Very Long App Name"))

        assertEquals(MatchStrategy.ACRONYM, long.rankedMatches("vl").first().strategy)
    }

    @Test
    fun `a single character query still resolves`() {
        assertEquals(MatchStrategy.PREFIX, apps.rankedMatches("s").first().strategy)
    }

    @Test
    fun `within the substring band, tighter coverage ranks higher`() {
        // Both labels have to be far enough from the query that TYPO doesn't claim them first —
        // a shorter padding like "xoogley" is only 2 edits from "oogle" (0.71 similarity) and
        // lands on TYPO instead, which is what makes this comparison meaningless. The strategy
        // assertion below is here so that premise fails loudly rather than the ordering quietly
        // testing something else.
        val substrings = listOf(
            AppEntry("com.a", "xxoogleyy"),
            AppEntry("com.b", "xxxoogleyyy"),
        )
        val ranked = substrings.rankedMatches("oogle")

        assertTrue(ranked.all { it.strategy == MatchStrategy.SUBSTRING })
        assertEquals(listOf("xxoogleyy", "xxxoogleyyy"), ranked.map { it.entry.label })
    }

    @Test
    fun `rankedMatches returns every match, not just the best few`() {
        // "ca" reaches Calculator/Calendar/Camera by prefix and others more weakly; the caller
        // (the preview line's "+N more") depends on the count being complete.
        val ranked = apps.rankedMatches("ca")

        assertTrue(ranked.size >= 3)
        assertEquals(ranked.map { it.entry }.distinct().size, ranked.size)
    }

    @Test
    fun `matching is pure — the same query gives the same ranking every time`() {
        assertEquals(apps.rankedMatches("ca"), apps.rankedMatches("ca"))
    }

}
