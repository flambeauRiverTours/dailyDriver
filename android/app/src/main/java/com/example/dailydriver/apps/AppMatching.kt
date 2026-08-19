package com.example.dailydriver.apps

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
 */
fun List<AppEntry>.rankedMatches(query: String): List<AppMatch> {
    val normalizedQuery = query.trim().lowercase()
    if (normalizedQuery.isEmpty()) return emptyList()
    return mapNotNull { it.matchAgainst(normalizedQuery) }.sortedByDescending { it.score }
}

/**
 * The single match to act on without asking, or null when the field is too close or too weak to
 * call. Mirrors "T1 short-circuits on a single high-confidence match" (design doc §4.2) — the
 * corollary being that it must *not* short-circuit when there isn't one.
 *
 * Thresholds are provisional and deliberately conservative; calibrating them against real usage
 * is T009's job.
 */
fun List<AppEntry>.confidentMatch(query: String): AppMatch? {
    val ranked = rankedMatches(query)
    val best = ranked.firstOrNull() ?: return null
    if (best.score < MIN_CONFIDENT_SCORE) return null
    val runnerUp = ranked.getOrNull(1) ?: return best
    return best.takeIf { it.score - runnerUp.score >= MIN_CONFIDENT_LEAD }
}

/**
 * The hint shown above the prompt as you type, or null when there's nothing worth previewing.
 *
 * Names the runner-up count as well as the best match, because with the ambiguity rule in
 * [confidentMatch] the interesting question isn't only "what matched?" but "will Enter act on
 * it?" — `Camera  +2 more` says plainly that it won't.
 */
fun List<AppEntry>.matchPreview(query: String): String? {
    val ranked = rankedMatches(query)
    val best = ranked.firstOrNull() ?: return null
    val others = ranked.size - 1
    return if (others == 0) best.entry.label else "${best.entry.label}  +$others more"
}

/** How many candidates to list when a query is too ambiguous to act on. */
const val MAX_LISTED_CANDIDATES = 5

/** Below this, the best guess is too speculative to launch without confirmation. */
private const val MIN_CONFIDENT_SCORE = 0.70

/** Two candidates closer together than this are a genuine tie — `ca` has no right answer. */
private const val MIN_CONFIDENT_LEAD = 0.15

private fun AppEntry.matchAgainst(query: String): AppMatch? {
    val label = this.label.lowercase()
    if (label.isEmpty()) return null
    // Split from the ORIGINAL label, not the lowercased one — camelCase boundaries are exactly
    // the casing information `lowercase()` destroys.
    val words = this.label.words()
    val coverage = query.length.toDouble() / label.length

    // Ordered by band, strongest first — the first hit wins, which is what makes this
    // best-of-strategies rather than additive.
    val (strategy, score) = when {
        label == query -> MatchStrategy.EXACT to 1.0

        label.startsWith(query) -> MatchStrategy.PREFIX to band(0.85, 0.95, coverage)

        else -> {
            val initials = words.map { it.first() }.joinToString("")
            val similarity = editSimilarity(query, label)
            when {
                initials.startsWith(query) ->
                    MatchStrategy.ACRONYM to band(0.80, 0.85, query.length.toDouble() / initials.length)

                // Only rescues near-misses; below this it produces noise, not corrections.
                similarity >= MIN_TYPO_SIMILARITY -> MatchStrategy.TYPO to similarity * 0.80

                words.any { it.startsWith(query) } -> MatchStrategy.WORD_PREFIX to band(0.70, 0.78, coverage)

                label.contains(query) -> MatchStrategy.SUBSTRING to band(0.60, 0.68, coverage)

                label.containsSubsequence(query) -> MatchStrategy.SUBSEQUENCE to band(0.50, 0.58, coverage)

                else -> return null
            }
        }
    }
    return AppMatch(this, score, strategy)
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
