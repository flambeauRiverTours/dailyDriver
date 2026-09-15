package com.flambeauriver.dailydriver.apps

/**
 * Why a query matched a label. Carried on every [AppMatch] because *why* is worth more than
 * *whether* — for calibrating the score bands, and for T014's training-data log.
 *
 * Declaration order is the confidence ladder, strongest first.
 */
enum class MatchStrategy { EXACT, PREFIX, ACRONYM, TYPO, WORD_PREFIX, SUBSTRING, SUBSEQUENCE }

/** A scored match. [score] is in `[0.0, 1.0]`; 1.0 is an exact label match. */
data class AppMatch(val entry: AppEntry, val score: Double, val strategy: MatchStrategy)

/**
 * The T1 tier (design doc §4.2). Scores every app against [query] and returns the matches,
 * best first.
 *
 * **Deliberate departure from the design doc's one-line spec**, which reads "normalized edit
 * distance + prefix/acronym boost". Edit distance measures typo-similarity between strings of
 * comparable length, which is structurally the wrong instrument for the doc's own example: `chr`
 * against "Chrome" is 3 edits out of 6 characters — 0.5 similarity, barely distinguishable from
 * noise — because `chr` isn't a typo of Chrome, it's an *abbreviation* of it.
 *
 * So the strategies below form a ladder and the **best** one wins, rather than one base score
 * accumulating boosts. Each strategy is a competing explanation for why the query matched, not
 * independent evidence to sum, and a max is far easier to tune and debug than interacting
 * weights. Edit distance keeps its place — as the rung that rescues genuine typos ([TYPO]),
 * which is what it's actually good at.
 *
 * Within a band, score scales with coverage (`query.length / label.length`), so a short query
 * prefers the tighter label: `ca` ranks Camera above Calculator.
 *
 * ⚠️ **That last rule is no longer what the prompt does** (T184). The shell ranks through
 * `command/CommandCandidate.kt`, which orders by band, then *use count*, then coverage — so `ca`
 * goes to whichever of Camera/Calculator you actually open, and only falls back to coverage with
 * no usage data. This function is the app-only path that has no usage to consult: `AliasTarget`
 * resolves an alias definition's app name with it, where "which did you mean by this name" is a
 * question about the name alone.
 */
fun List<AppEntry>.rankedMatches(query: String): List<AppMatch> {
    val normalizedQuery = query.trim().lowercase()
    if (normalizedQuery.isEmpty()) return emptyList()
    return mapNotNull { it.matchAgainst(normalizedQuery) }.sortedByDescending { it.score }
}

private fun AppEntry.matchAgainst(query: String): AppMatch? =
    scoreLabel(this.label, query)?.let { AppMatch(this, it.score, it.strategy) }

/** Why one label matched, and how strongly. The label-only half of [AppMatch]. */
data class LabelMatch(val strategy: MatchStrategy, val score: Double)

/**
 * Scores one label against an already-normalized [query], or null if nothing matched.
 *
 * **Extracted from [AppEntry.matchAgainst] at T182 so the ladder works on any label**, not just
 * an app's. The unified candidate matcher (`command/CommandCandidate.kt`) scores alias names and
 * reserved-command tokens through this same function, which is the point: one definition of "why
 * did this match and how well", so an alias and an app can be ranked against each other at all.
 *
 * [query] must already be trimmed and lowercased — callers normalize once for a whole pass rather
 * than per candidate.
 */
fun scoreLabel(label: String, query: String): LabelMatch? {
    val lowered = label.lowercase()
    if (lowered.isEmpty()) return null
    // Split from the ORIGINAL label, not the lowercased one — camelCase boundaries are exactly
    // the casing information `lowercase()` destroys.
    val words = label.words()
    val coverage = query.length.toDouble() / lowered.length

    // Ordered by band, strongest first — the first hit wins, which is what makes this
    // best-of-strategies rather than additive.
    val (strategy, score) = when {
        lowered == query -> MatchStrategy.EXACT to 1.0

        lowered.startsWith(query) -> MatchStrategy.PREFIX to band(0.85, 0.95, coverage)

        else -> {
            val initials = words.map { it.first() }.joinToString("")
            val similarity = editSimilarity(query, lowered)
            when {
                initials.startsWith(query) ->
                    MatchStrategy.ACRONYM to band(0.80, 0.85, query.length.toDouble() / initials.length)

                // Only rescues near-misses; below this it produces noise, not corrections.
                similarity >= MIN_TYPO_SIMILARITY -> MatchStrategy.TYPO to similarity * 0.80

                words.any { it.startsWith(query) } -> MatchStrategy.WORD_PREFIX to band(0.70, 0.78, coverage)

                lowered.contains(query) -> MatchStrategy.SUBSTRING to band(0.60, 0.68, coverage)

                lowered.containsSubsequence(query) -> MatchStrategy.SUBSEQUENCE to band(0.50, 0.58, coverage)

                else -> return null
            }
        }
    }
    return LabelMatch(strategy, score)
}

/** Contiguity is a stronger signal than scattered characters, so [TYPO] sits above these. */
private const val MIN_TYPO_SIMILARITY = 0.70

/** Positions [coverage] within a strategy's band, so bands never overlap. */
private fun band(low: Double, high: Double, coverage: Double) =
    low + (high - low) * coverage.coerceIn(0.0, 1.0)

/**
 * Splits a label into words on whitespace, punctuation, *and* camelCase boundaries — so
 * "PayPal" yields [pay, pal] and its initials are "pp". Without the camelCase rule, single-token
 * product names would have no initials worth matching.
 *
 * Must be called on the original label: lowercasing first would erase the very case boundaries
 * this splits on.
 */
private fun String.words(): List<String> =
    CAMEL_BOUNDARY.replace(this, "$1 $2")
        .split(NON_WORD)
        .filter { it.isNotEmpty() }
        .map { it.lowercase() }

private val CAMEL_BOUNDARY = Regex("([a-z])([A-Z])")
private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

/** True if every character of [query] appears in order, not necessarily contiguously. */
private fun String.containsSubsequence(query: String): Boolean {
    var index = 0
    for (character in this) {
        if (index < query.length && character == query[index]) index++
    }
    return index == query.length
}
