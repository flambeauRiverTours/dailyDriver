package com.flambeauriver.dailydriver.shell

import com.flambeauriver.dailydriver.alias.AliasDefinitionAttempt
import com.flambeauriver.dailydriver.alias.parseAliasDefinition
import com.flambeauriver.dailydriver.apps.AppEntry
import com.flambeauriver.dailydriver.apps.filteredBy
import com.flambeauriver.dailydriver.apps.soleAutoLaunchTarget
import com.flambeauriver.dailydriver.command.Candidate
import com.flambeauriver.dailydriver.command.CandidateMatch
import com.flambeauriver.dailydriver.command.ReservedCommand
import com.flambeauriver.dailydriver.command.buildCandidates
import com.flambeauriver.dailydriver.command.parseReservedCommand
import com.flambeauriver.dailydriver.command.rankCandidates

/**
 * Everything the shell derives from one line of typed input (T179, reworked at T184).
 *
 * **Pure, and that is the entire point.** All of this used to live inside `ShellScreen` as a run
 * of `remember` blocks, which made it unreachable from a unit test — including [previewText] and
 * what a pause commits to, which are among the least appropriate things in the app to have had no
 * tests.
 *
 * **T184 replaced three separate answers with one ranked list.** There used to be
 * `plainMatchTarget` (T1's fuzzy app guess), `pendingAliasExpansion` (an exact alias) and
 * [autoLaunchTarget] (`:apps` narrowed to one), each derived by its own matcher and each feeding
 * its own timer in `ShellScreen`. The first two are now [candidates] — one ranking over apps,
 * aliases and reserved commands alike (ADR-0010) — so the preview and the pause read the same
 * answer dispatch will act on, rather than three answers that had to be kept in agreement.
 *
 * Note results and task results are deliberately **not** here. They arrive from Room as flows, so
 * they are asynchronous UI state rather than a function of the input, and they stay collected in
 * the composable.
 */
data class ShellUiState(
    /** Non-null while the input matches a T0 reserved command. */
    val reservedCommand: ReservedCommand?,
    /** `alias x = y` being typed. Disjoint from [reservedCommand] — an alias name can't start with `:`. */
    val aliasDefinition: AliasDefinitionAttempt?,
    /** The `:apps` filter result, empty unless `:apps` is active. */
    val filteredApps: List<AppEntry>,
    /** `:apps` narrowed to exactly one app — what Enter or the pause opens. */
    val autoLaunchTarget: AppEntry?,
    /**
     * Everything the prompt could mean, best first — the same ranking `dispatchCommand` acts on.
     *
     * Empty while a reserved command or a definition is being typed: those are modes, not
     * queries, and ranking `:apps ca` as a whole would give Enter a second answer to the question
     * [autoLaunchTarget] already answers.
     */
    val candidates: List<CandidateMatch>,
    /** The dim line above the prompt showing what Enter would do. */
    val previewText: String?,
) {
    val appsCommand: ReservedCommand.Apps? get() = reservedCommand as? ReservedCommand.Apps
    val notesCommand: ReservedCommand.Notes? get() = reservedCommand as? ReservedCommand.Notes
    val tasksCommand: ReservedCommand.Tasks? get() = reservedCommand as? ReservedCommand.Tasks
    val tasksDoneCommand: ReservedCommand.TasksDone? get() = reservedCommand as? ReservedCommand.TasksDone

    /** `:notif` only counts as a browse mode once access is granted; without it, Enter deep-links. */
    fun notifCommand(hasAccess: Boolean): ReservedCommand.Notif? =
        (reservedCommand as? ReservedCommand.Notif)?.takeIf { hasAccess }

    /** What Enter would act on at a bare prompt. Null in a browse mode, or when nothing matched. */
    val topCandidate: CandidateMatch? get() = candidates.firstOrNull()

    /**
     * What a typing pause commits to without Enter, or null if nothing should fire — the single
     * key behind `ShellScreen`'s one remaining timer (T184).
     *
     * A `String` rather than the candidate itself because it is used as a `LaunchedEffect` key:
     * what matters is whether the thing being waited on *changed identity*, and two equal targets
     * must not restart the clock.
     *
     * **A reserved command is deliberately not in here, and that is a rule worth stating.** Enter
     * commits anything; a pause commits only what is cheap to be wrong about — an app, or an
     * alias the user wrote themselves. Before T184 that fell out of `parseReservedCommand` being
     * exact, so no timer ever saw a half-typed token. Now that tokens are fuzzy-matchable
     * (ADR-0010) it has to be said out loud, or pausing halfway through `:notes` would open the
     * editor on `:note`.
     */
    val pauseTarget: String?
        get() = autoLaunchTarget?.packageName ?: topCandidate?.candidate?.let { candidate ->
            when (candidate) {
                is Candidate.App, is Candidate.Alias -> candidate.target
                is Candidate.Command -> null
            }
        }
}

/**
 * Derives the whole of [ShellUiState] from the current input.
 *
 * [names] is passed in rather than derived here because it is keyed on the **app index**, not on
 * the input (T164: whether "Messages" is ambiguous is a property of what is installed). Computing
 * it inside would rebuild a map over every installed app on every keystroke.
 *
 * [usage] is likewise supplied rather than read: this stays a pure function of its arguments, and
 * the counts come from Room via `ShellViewModel`. Passing them is what makes the preview agree
 * with dispatch — both rank with the same numbers, so the name you see is the thing Enter opens.
 */
fun shellUiState(
    input: String,
    apps: List<AppEntry>,
    aliases: Map<String, String>,
    names: Map<String, String>,
    usage: Map<String, Int> = emptyMap(),
): ShellUiState {
    val reservedCommand = parseReservedCommand(input)
    val aliasDefinition = parseAliasDefinition(input)

    val appsCommand = reservedCommand as? ReservedCommand.Apps
    // ⚠️ Still the substring filter, not the fuzzy ladder, and deliberately so — see the note in
    // `AppFiltering.kt`. `:apps` is a browse view whose auto-launch fires when the filter narrows
    // to exactly one row; fuzzy matching is permissive enough (SUBSEQUENCE alone matches dozens of
    // labels on a real device) that it would rarely narrow to one, quietly removing the
    // affordance. A filter and a matcher answer different questions.
    val filteredApps = appsCommand?.let { apps.filteredBy(it.query, names) }.orEmpty()

    // A mode, not a query. Ranking the whole of `:apps ca` would hand Enter a second answer.
    val isPrompt = reservedCommand == null && aliasDefinition == null
    val candidates = if (isPrompt) rankCandidates(input, buildCandidates(apps, aliases), usage) else emptyList()

    return ShellUiState(
        reservedCommand = reservedCommand,
        aliasDefinition = aliasDefinition,
        filteredApps = filteredApps,
        // Sole-match only, which is stricter than the bare-prompt rule and deliberately so:
        // `:apps` is a browse view, and committing to a best-of-several while you are still
        // reading the list would take the list away mid-scan.
        autoLaunchTarget = appsCommand?.let { filteredApps.soleAutoLaunchTarget(it.query) },
        candidates = candidates,
        previewText = previewOf(candidates, names),
    )
}

/**
 * The dim line above the prompt: what the top candidate is, and how many others matched.
 *
 * **The runner-up count is the only remaining ambiguity signal** (T008, reversed 2026-08-16): the
 * matcher no longer refuses to guess between near-ties, so `Camera  +2 more` is what tells you to
 * keep typing before the pause commits.
 *
 * ⚠️ **The count now spans every candidate type**, where before T184 it counted only apps. `s`
 * previewing `Spotify  +2 more` is honest — an alias, the app and `:settings` all answer to it —
 * where the old alias branch printed a bare expansion and hid the fact that anything else matched.
 */
private fun previewOf(candidates: List<CandidateMatch>, names: Map<String, String>): String? {
    val best = candidates.firstOrNull() ?: return null
    val others = candidates.size - 1
    val name = best.candidate.previewName(names)
    return if (others == 0) name else "$name  +$others more"
}

/**
 * What a candidate is called in the preview.
 *
 * An app is named the way the browse list names it (T164) — a preview reading `Messages` while
 * the list shows `Messages (Google)` and `Messages (Samsung)` would be announcing a choice it is
 * not making. An alias shows its *expansion* rather than its name, for the same reason a fuzzy
 * match shows its target: the preview answers "what will Enter do", not "what did I type".
 */
private fun Candidate.previewName(names: Map<String, String>): String = when (this) {
    is Candidate.App -> names[entry.packageName] ?: label
    is Candidate.Alias -> expansion
    is Candidate.Command -> token
}
