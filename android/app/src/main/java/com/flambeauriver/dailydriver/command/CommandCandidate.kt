package com.flambeauriver.dailydriver.command

import com.flambeauriver.dailydriver.apps.AppEntry
import com.flambeauriver.dailydriver.apps.displayNames
import com.flambeauriver.dailydriver.apps.LabelMatch
import com.flambeauriver.dailydriver.apps.MatchStrategy
import com.flambeauriver.dailydriver.apps.scoreLabel

/**
 * One thing the prompt could mean (T182).
 *
 * **Before this, there were three collections matched three different ways** — reserved commands
 * by an exact registry walk, aliases by an exact map lookup, apps by a fuzzy ladder — related only
 * by a hard-coded cascade in [dispatchCommand]. Nothing ever ranked a command against an app, so
 * "what will Enter do?" had a different answer depending on which bucket your text landed in.
 * That is what usability testing called "all over the place".
 *
 * [target] is deliberately the same string [CommandResolution.target] already records: a package
 * name for an app, the token for a reserved command, the expansion for an alias. That makes it the
 * key for the usage index (T183) without inventing a second identity scheme for the same things.
 */
sealed interface Candidate {

    /** Stable identity: the usage-index key, and what the T014 log records. */
    val target: String

    /** What is displayed, and what input is matched against. */
    val label: String

    /**
     * Whether trailing text is an argument rather than part of the name.
     *
     * True for commands (`:task buy milk`) and aliases (`a firefox`, T173), false for apps — an
     * app name is matched whole or not at all, since "Chrome foo" is not a way of saying Chrome.
     */
    val acceptsArgument: Boolean

    /**
     * An installed app.
     *
     * [label] is passed in rather than read from [entry] because T164's disambiguation suffix
     * lives outside `AppEntry` on purpose: a fuzzy match against `Messages (Google)` scores worse
     * for someone typing `mess` than one against `Messages`, so the suffix is a display concern
     * the index must not carry. The caller decides which name this candidate answers to.
     */
    data class App(val entry: AppEntry, override val label: String) : Candidate {
        override val target: String get() = entry.packageName
        override val acceptsArgument: Boolean get() = false
    }

    /** A user-defined alias. Matched on its name; dispatched as its expansion. */
    data class Alias(val name: String, val expansion: String) : Candidate {
        override val target: String get() = expansion
        override val label: String get() = name
        override val acceptsArgument: Boolean get() = true
    }

    /** A reserved command token. Every one takes an argument slot, even those that discard it. */
    data class Command(val token: String) : Candidate {
        override val target: String get() = token
        override val label: String get() = token
        override val acceptsArgument: Boolean get() = true
    }
}

/**
 * A candidate that matched, with everything needed to rank and then run it.
 *
 * [argument] is the remainder when the match was on a leading token — `buy milk` for
 * `:task buy milk`. Empty for a whole-input match.
 */
data class CandidateMatch(
    val candidate: Candidate,
    val strategy: MatchStrategy,
    val score: Double,
    val usage: Int,
    val argument: String,
)

/**
 * Every candidate that matches [input], best first (T182).
 *
 * **The stages are ordered, and that ordering is what preserves the T0-before-T1 rule.** Because
 * the exact pass over the whole list completes before any fuzzy scoring is considered, an alias
 * named `cam` still beats a fuzzy match on Camera — the property `CommandDispatchTest` pins, now
 * falling out of the structure instead of needing the special case the old cascade gave it.
 *
 * **Ranking is band, then usage, then coverage, then label** (decision, 2026-09-15). The strategy
 * ladder decides tiers, so a stronger match never loses to a weaker one you happened to use once;
 * within a tier the thing you actually use wins. Label is a final tiebreak purely so the list is
 * deterministic and never reorders under equal candidates.
 *
 * ⚠️ **This deliberately changes what `ca` does.** It used to prefer Camera, because coverage
 * favours the tighter label. With usage ranked above coverage it goes to whichever of
 * Camera/Calculator you open more. That is the requested behaviour, not a regression — see the
 * note in `AppMatching.kt`'s `rankedMatches`, which still describes the old rule for the app-only
 * path that has no usage data to consult.
 *
 * [usage] maps [Candidate.target] to a use count; absent means zero. Passed in rather than read
 * here so this stays pure and testable without a database (T183 supplies the real numbers).
 */
fun rankCandidates(
    input: String,
    candidates: List<Candidate>,
    usage: Map<String, Int> = emptyMap(),
): List<CandidateMatch> {
    val whole = input.trim().lowercase()
    if (whole.isEmpty()) return emptyList()
    val firstToken = whole.substringBefore(' ')

    return candidates
        .mapNotNull { candidate ->
            val (match, argument) = candidate.bestMatch(whole, firstToken, input) ?: return@mapNotNull null
            CandidateMatch(
                candidate = candidate,
                strategy = match.strategy,
                score = match.score,
                usage = usage[candidate.target] ?: 0,
                argument = argument,
            )
        }
        .sortedWith(
            // Ordinal ascending: MatchStrategy declares strongest first.
            compareBy<CandidateMatch> { it.strategy.ordinal }
                .thenByDescending { it.usage }
                .thenByDescending { it.score }
                .thenBy { it.candidate.label.lowercase() },
        )
}

/**
 * The best reading of this candidate against the input, and the argument it leaves over.
 *
 * A candidate that takes an argument gets two chances — the whole input, and just the leading
 * token — and keeps whichever scores better. Trying whole-input *first* is what keeps a
 * multi-word alias name reachable: `long name` matches as a whole where its leading token `long`
 * would not. That mirrors what `expandAlias` already does, rather than inventing a new rule.
 */
private fun Candidate.bestMatch(
    whole: String,
    firstToken: String,
    rawInput: String,
): Pair<LabelMatch, String>? {
    val wholeMatch = scoreLabel(label, whole)?.let { it to "" }
    if (!acceptsArgument || firstToken == whole) return wholeMatch

    val tokenMatch = scoreLabel(label, firstToken)?.let {
        it to rawInput.trim().substringAfter(' ').trim()
    }

    // Score comparison is safe as a proxy for strategy strength: `band()` keeps the strategies'
    // score ranges disjoint, so a higher score is always the stronger rung.
    return listOfNotNull(wholeMatch, tokenMatch).maxByOrNull { it.first.score }
}

/**
 * Everything the prompt could currently mean, from the three sources that used to be matched
 * separately (T184).
 *
 * App labels come from [displayNames] so a disambiguated name stays typeable (T167) — the same
 * name the browse list shows is the name you can type, which is the bug that fix existed for.
 *
 * Reserved tokens come from the registry rather than a second list, so registering a command still
 * means touching exactly one place (T140's property, pinned by `ReservedCommandParserTest`).
 *
 * ⚠️ **Deduplicated by case, because the registry holds casing variants of one command.**
 * `:tasksDone` is registered beside `:tasksdone` purely so the camelCase spelling still enters
 * browse mode, where `parseReservedCommand` matches exactly. Matching here is case-insensitive, so
 * both entries match the same input equally well and the pair would be two candidates for one
 * command — a phantom "+1 more" in the preview, and worse, two rows in the usage index splitting
 * the count for a command you only ever ran one of. The canonical entry is registered first and is
 * the one kept; the variant needs no candidate of its own, since the spelling it exists to rescue
 * already matches the canonical one exactly.
 */
fun buildCandidates(
    apps: List<AppEntry>,
    aliases: Map<String, String>,
    includeAliases: Boolean = true,
): List<Candidate> {
    val names = displayNames(apps)
    return buildList {
        apps.forEach { add(Candidate.App(it, names[it.packageName] ?: it.label)) }
        if (includeAliases) aliases.forEach { (name, expansion) -> add(Candidate.Alias(name, expansion)) }
        reservedCommandTokens.distinctBy { it.lowercase() }.forEach { add(Candidate.Command(it)) }
    }
}
