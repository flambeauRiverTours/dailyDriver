package com.example.dailydriver.apps

import kotlin.math.min

/**
 * Damerau-Levenshtein distance (optimal string alignment variant) — the minimum number of
 * insertions, deletions, substitutions, or *adjacent transpositions* turning [a] into [b].
 *
 * Damerau rather than plain Levenshtein specifically for the transposition case: swapping two
 * adjacent characters ("chorme" for "chrome") is the single most common typing error, and plain
 * Levenshtein charges it two edits instead of one. On a six-character word that's 0.83 vs 0.67
 * similarity — the difference between rescuing the typo and missing it entirely.
 *
 * Optimal-string-alignment, not unrestricted Damerau-Levenshtein: OSA never edits the same
 * substring twice, so a non-adjacent swap costs 2 rather than being optimised further. That
 * distinction is irrelevant at app-label lengths and OSA is meaningfully simpler.
 */
fun damerauLevenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length

    // Only three rows are ever live (current, previous, the one before it for transpositions),
    // but at app-label lengths the full matrix is a few hundred ints — clarity wins over reuse.
    val distance = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) distance[i][0] = i
    for (j in 0..b.length) distance[0][j] = j

    for (i in 1..a.length) {
        for (j in 1..b.length) {
            val substitutionCost = if (a[i - 1] == b[j - 1]) 0 else 1
            distance[i][j] = min(
                min(
                    distance[i - 1][j] + 1,      // deletion
                    distance[i][j - 1] + 1,      // insertion
                ),
                distance[i - 1][j - 1] + substitutionCost,
            )
            val isAdjacentTransposition = i > 1 && j > 1 &&
                a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]
            if (isAdjacentTransposition) {
                distance[i][j] = min(distance[i][j], distance[i - 2][j - 2] + 1)
            }
        }
    }
    return distance[a.length][b.length]
}

/** Edit distance expressed as similarity in `[0.0, 1.0]`, where 1.0 is an exact match. */
fun editSimilarity(a: String, b: String): Double {
    val longest = maxOf(a.length, b.length)
    if (longest == 0) return 1.0
    return 1.0 - (damerauLevenshtein(a, b).toDouble() / longest)
}
