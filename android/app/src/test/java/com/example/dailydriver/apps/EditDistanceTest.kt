package com.example.dailydriver.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditDistanceTest {

    @Test
    fun `identical strings have no distance`() {
        assertEquals(0, damerauLevenshtein("chrome", "chrome"))
    }

    @Test
    fun `empty against non-empty costs one per character`() {
        assertEquals(6, damerauLevenshtein("", "chrome"))
        assertEquals(6, damerauLevenshtein("chrome", ""))
        assertEquals(0, damerauLevenshtein("", ""))
    }

    @Test
    fun `single substitution`() {
        assertEquals(1, damerauLevenshtein("chrome", "chrame"))
    }

    @Test
    fun `single deletion`() {
        assertEquals(1, damerauLevenshtein("google", "gogle"))
    }

    @Test
    fun `single insertion`() {
        assertEquals(1, damerauLevenshtein("gogle", "google"))
    }

    @Test
    fun `adjacent transposition costs one, not two — the whole reason for Damerau over Levenshtein`() {
        // Plain Levenshtein scores this 2 (delete + insert), which for a 6-character word is the
        // difference between 0.83 and 0.67 similarity — i.e. between rescuing a typo and missing it.
        assertEquals(1, damerauLevenshtein("chrome", "chorme"))
    }

    @Test
    fun `non-adjacent transposition is not a single edit`() {
        // Only *adjacent* swaps are one edit under the optimal-string-alignment variant.
        assertEquals(2, damerauLevenshtein("abcd", "cbad"))
    }

    @Test
    fun `completely unrelated strings cost at most the longer length`() {
        assertEquals(6, damerauLevenshtein("aaaaaa", "bbbbbb"))
    }

    // --- more distance cases --------------------------------------------------------------------

    @Test
    fun `transpositions count as one edit wherever they occur in the word`() {
        assertEquals(1, damerauLevenshtein("chrome", "hcrome")) // first two swapped
        assertEquals(1, damerauLevenshtein("chrome", "chroem")) // last two swapped
    }

    @Test
    fun `two separate transpositions cost two edits`() {
        assertEquals(2, damerauLevenshtein("abcd", "badc"))
    }

    @Test
    fun `mixed edit kinds accumulate`() {
        // "calendar" -> "celandar": substitution (a->e) plus a transposition.
        assertEquals(2, damerauLevenshtein("calendar", "celandar"))
    }

    @Test
    fun `matching is case-sensitive — callers lowercase before comparing`() {
        // AppMatching normalises to lowercase before ever reaching here. If this function
        // silently folded case, that normalisation would look redundant and get removed.
        assertEquals(1, damerauLevenshtein("chrome", "Chrome"))
    }

    @Test
    fun `single-character strings behave`() {
        assertEquals(0, damerauLevenshtein("a", "a"))
        assertEquals(1, damerauLevenshtein("a", "b"))
        assertEquals(1, damerauLevenshtein("a", ""))
    }

    @Test
    fun `non-ASCII characters are handled per character, not per byte`() {
        assertEquals(1, damerauLevenshtein("café", "cafe"))
        assertEquals(0, damerauLevenshtein("日本語", "日本語"))
    }

    @Test
    fun `distance never exceeds the longer string's length`() {
        pairs().forEach { (a, b) ->
            val distance = damerauLevenshtein(a, b)
            assertTrue("$a/$b exceeded max length", distance <= maxOf(a.length, b.length))
        }
    }

    @Test
    fun `distance is at least the difference in lengths`() {
        // You can't reconcile a length gap with fewer edits than the gap itself.
        pairs().forEach { (a, b) ->
            val distance = damerauLevenshtein(a, b)
            assertTrue("$a/$b undershot the length gap", distance >= kotlin.math.abs(a.length - b.length))
        }
    }

    @Test
    fun `distance is zero only for identical strings`() {
        pairs().forEach { (a, b) ->
            assertEquals("$a/$b", a == b, damerauLevenshtein(a, b) == 0)
        }
    }

    @Test
    fun `symmetry holds across the whole sample, not just one pair`() {
        pairs().forEach { (a, b) ->
            assertEquals("$a/$b", damerauLevenshtein(a, b), damerauLevenshtein(b, a))
        }
    }

    // --- editSimilarity -------------------------------------------------------------------------

    @Test
    fun `identical strings are perfectly similar`() {
        assertEquals(1.0, editSimilarity("chrome", "chrome"), TOLERANCE)
    }

    @Test
    fun `two empty strings are perfectly similar rather than dividing by zero`() {
        // The explicit guard in editSimilarity — without it this is 0/0.
        assertEquals(1.0, editSimilarity("", ""), TOLERANCE)
    }

    @Test
    fun `anything against empty is completely dissimilar`() {
        assertEquals(0.0, editSimilarity("chrome", ""), TOLERANCE)
        assertEquals(0.0, editSimilarity("", "chrome"), TOLERANCE)
    }

    @Test
    fun `strings sharing nothing at equal length are completely dissimilar`() {
        assertEquals(0.0, editSimilarity("aaaaaa", "bbbbbb"), TOLERANCE)
    }

    @Test
    fun `one edit in six characters is five-sixths similar`() {
        assertEquals(5.0 / 6.0, editSimilarity("chrome", "chrame"), TOLERANCE)
    }

    @Test
    fun `the transposition advantage shows up in the similarity, not just the distance`() {
        // 0.83 rather than the 0.67 plain Levenshtein would give — this is the number that
        // decides whether AppMatching's TYPO rung fires at all (its floor is 0.70).
        assertEquals(5.0 / 6.0, editSimilarity("chrome", "chorme"), TOLERANCE)
    }

    @Test
    fun `an abbreviation scores poorly — the reason AppMatching does not rely on this alone`() {
        // The design doc's own worked example: "chr" against "chrome" is 3 edits over 6
        // characters, barely above noise, despite being an obviously intended match. This is
        // precisely why T008 built a strategy ladder instead of using edit distance for
        // everything.
        assertEquals(0.5, editSimilarity("chr", "chrome"), TOLERANCE)
    }

    @Test
    fun `the longer string is the denominator, so length gaps drag similarity down`() {
        // "cal" vs "calculator": 7 edits over 10.
        assertEquals(1.0 - 7.0 / 10.0, editSimilarity("cal", "calculator"), TOLERANCE)
    }

    @Test
    fun `similarity always lands inside zero to one`() {
        pairs().forEach { (a, b) ->
            val similarity = editSimilarity(a, b)
            assertTrue("$a/$b -> $similarity", similarity in 0.0..1.0)
        }
    }

    @Test
    fun `similarity is symmetric`() {
        pairs().forEach { (a, b) ->
            assertEquals("$a/$b", editSimilarity(a, b), editSimilarity(b, a), TOLERANCE)
        }
    }

    @Test
    fun `similarity is one only for identical strings`() {
        pairs().forEach { (a, b) ->
            assertEquals("$a/$b", a == b, editSimilarity(a, b) == 1.0)
        }
    }

    private companion object {
        const val TOLERANCE = 0.0001

        /** A spread of shapes — equal, empty, typo'd, abbreviated, unrelated, different lengths. */
        fun pairs(): List<Pair<String, String>> {
            val samples = listOf("", "a", "chrome", "chorme", "chr", "calculator", "calendar", "日本語")
            return samples.flatMap { a -> samples.map { b -> a to b } }
        }
    }
}
