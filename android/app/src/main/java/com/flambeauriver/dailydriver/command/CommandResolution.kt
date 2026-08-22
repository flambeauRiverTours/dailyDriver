package com.flambeauriver.dailydriver.command

/**
 * What the deterministic tiers actually did with one line of input (T014).
 *
 * **Deliberately not a §4.3 intent label** (design check-in, 2026-08-21). §4.3's label space is
 * already stale — it predates `:task`, `:tasks`, `:tasksdone` and `:notif`, so four of the ten
 * reserved commands have no label to record. A log that bakes in today's labels goes stale the
 * same way, and unlike code it cannot be refactored: the rows are a record of the past.
 *
 * So this records *what happened*, and §8's hand-labelling step maps it to whatever the label
 * space is at the time the dataset is frozen. Mapping is a decision best made once, when the
 * labels stop moving.
 */
data class CommandResolution(val tier: Tier, val target: String) {

    /** Which deterministic tier answered, matching the T0–T3 names used throughout §4.2. */
    enum class Tier { T0, T1, T3 }

    companion object {
        /** [target] for input nothing resolved. Empty rather than null so it can sit in a key. */
        const val UNRESOLVED = ""
    }
}

/**
 * Classifies one submitted line against what the tiers found.
 *
 * Takes the already-computed results rather than re-deriving them, so the log records the same
 * answer the launcher acted on. Re-running the matchers here would be a second opinion that could
 * disagree with the first, which would make the training data quietly wrong rather than obviously
 * wrong.
 *
 * Order matters and mirrors the cascade: an exact command wins, then an alias, then a fuzzy app
 * match, then nothing.
 */
fun classifyResolution(
    reservedToken: String?,
    aliasExpansion: String?,
    appPackage: String?,
): CommandResolution = when {
    reservedToken != null -> CommandResolution(CommandResolution.Tier.T0, reservedToken)
    aliasExpansion != null -> CommandResolution(CommandResolution.Tier.T0, aliasExpansion)
    appPackage != null -> CommandResolution(CommandResolution.Tier.T1, appPackage)
    // T2 is skipped entirely: the SLM does not exist, and when it does its output is what this
    // data trains, so logging its guesses as ground truth would be circular.
    else -> CommandResolution(CommandResolution.Tier.T3, CommandResolution.UNRESOLVED)
}
