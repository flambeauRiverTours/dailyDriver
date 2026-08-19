package com.example.dailydriver.apps

import org.junit.Assert.assertEquals
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
    fun `is symmetric`() {
        assertEquals(
            damerauLevenshtein("calendar", "calculator"),
            damerauLevenshtein("calculator", "calendar"),
        )
    }

    @Test
    fun `completely unrelated strings cost at most the longer length`() {
        assertEquals(6, damerauLevenshtein("aaaaaa", "bbbbbb"))
    }
}
