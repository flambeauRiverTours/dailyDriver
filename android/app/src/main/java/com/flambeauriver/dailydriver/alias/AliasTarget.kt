package com.flambeauriver.dailydriver.alias

import com.flambeauriver.dailydriver.apps.AppEntry
import com.flambeauriver.dailydriver.apps.displayNames
import com.flambeauriver.dailydriver.apps.rankedMatches
import com.flambeauriver.dailydriver.command.parseReservedCommand

/**
 * Checking that an alias points at something real, before it is stored (T162).
 *
 * **This deliberately inverts T1's rule, and the inversion is the whole point.** Fuzzy matching at
 * the prompt lets the best match win even among near-ties (T008), because a wrong guess costs one
 * keystroke to correct. An alias is a *stored decision*: a wrong guess there is wrong every time
 * you use it afterwards, and you are unlikely to notice which of two candidates it picked. So the
 * same ambiguity that T1 resolves silently is refused here.
 */
sealed interface AliasTarget {

    /** A reserved command, with or without an argument: `:apps`, `:note standup`. */
    data object Command : AliasTarget

    /**
     * Exactly one app matched.
     *
     * [resolvedCommand] is the app's **full label**, not what was typed. See [resolveAliasTarget].
     */
    data class App(val entry: AppEntry, val resolvedCommand: String) : AliasTarget

    /** Several apps matched. [candidates] are display names, disambiguated where they collide. */
    data class Ambiguous(val candidates: List<String>) : AliasTarget

    /** Nothing matched. */
    data object Unknown : AliasTarget
}

/** How many candidates an ambiguous result lists before it stops. */
private const val MAX_CANDIDATES = 5

/**
 * Works out what an alias's target refers to.
 *
 * A reserved command wins outright: it is exact by definition, and `:note standup` must not be
 * fuzzy-matched against app names.
 *
 * **An app target is rewritten to the app's full label**, which matters more than it looks.
 * Aliases store a command string that is re-resolved through T1 every time they are used, so
 * storing what was typed (`alias s = spot`) leaves the alias meaning whatever `spot` happens to
 * match *later*. Installing an app that scores higher on `spot` would silently repoint an alias
 * the user never touched. Storing `Spotify` pins it to what they actually chose.
 *
 * An exact label match short-circuits the ranking: if the typed target *is* an app's name, that is
 * the app, even if the fuzzy ranker would also surface others. Duplicate labels are still
 * ambiguous, which is exactly the `Messages` case T164 exists for.
 */
fun resolveAliasTarget(command: String, apps: List<AppEntry>): AliasTarget {
    val trimmed = command.trim()
    if (trimmed.isEmpty()) return AliasTarget.Unknown
    if (parseReservedCommand(trimmed) != null) return AliasTarget.Command

    val names = displayNames(apps)

    val exact = apps.filter { it.label.trim().equals(trimmed, ignoreCase = true) }
    if (exact.size == 1) return AliasTarget.App(exact.single(), exact.single().label)
    if (exact.size > 1) return AliasTarget.Ambiguous(exact.mapNotNull { names[it.packageName] })

    val ranked = apps.rankedMatches(trimmed)
    return when (ranked.size) {
        0 -> AliasTarget.Unknown
        1 -> AliasTarget.App(ranked.single().entry, ranked.single().entry.label)
        else -> AliasTarget.Ambiguous(
            ranked.take(MAX_CANDIDATES).mapNotNull { names[it.entry.packageName] },
        )
    }
}
