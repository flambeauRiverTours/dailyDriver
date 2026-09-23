package com.flambeauriver.dailydriver.command

import com.flambeauriver.dailydriver.alias.describeDefineAliasResult
import com.flambeauriver.dailydriver.alias.formatAliasListing
import com.flambeauriver.dailydriver.alias.parseAliasDefinition
import com.flambeauriver.dailydriver.apps.MatchStrategy
import com.flambeauriver.dailydriver.apps.displayNames
import com.flambeauriver.dailydriver.apps.filteredBy
import com.flambeauriver.dailydriver.apps.soleAutoLaunchTarget
import com.flambeauriver.dailydriver.notes.parseChecklist
import com.flambeauriver.dailydriver.notes.parseNoteQuery
import com.flambeauriver.dailydriver.notes.resolveDueInText
import com.flambeauriver.dailydriver.notes.soleAutoOpenTarget
import com.flambeauriver.dailydriver.search.googleSearchUrl
import com.flambeauriver.dailydriver.search.searchedText
import com.flambeauriver.dailydriver.shell.shortDate
import com.flambeauriver.dailydriver.slm.commandLineFor
import com.flambeauriver.dailydriver.slm.resolveIntent
import java.time.LocalDate

/**
 * What running a submitted line produces (T178).
 *
 * [transcript] is the line to record, or **null when the UI itself is the response** — `:apps`
 * opening a browse view, `:note` opening the editor. That one rule replaced fifteen ViewModel
 * methods whose entire body was `transcript.record(…)` followed by `input = ""`.
 */
data class CommandOutcome(val transcript: String?, val clearsInput: Boolean = true) {
    companion object {
        /** The UI is the answer and the prompt is done with — an app launched, the editor opened. */
        val Silent = CommandOutcome(null)

        /**
         * Nothing happened and **the prompt keeps what was typed**.
         *
         * This is a browse view with nothing to commit to: `:apps ca` matching several apps, or
         * `:tasks`, where rows are tapped. Clearing there would close the list you are still
         * reading and throw away the filter you typed to get it.
         */
        val Stay = CommandOutcome(null, clearsInput = false)

        fun say(text: String) = CommandOutcome(text)
    }
}

/**
 * Runs one submitted line against [env] and says what to put in the transcript (T178).
 *
 * **This is the single dispatch point the task exists to create.** It replaces a twelve-branch
 * `when` of no-ops in `ShellViewModel` plus a seven-branch `when` inside `ShellScreen`'s Enter
 * handler, which between them meant adding a command touched four files and no part of the
 * decision was reachable from a test.
 *
 * **The order is the T0 → T1 → T3 cascade and must stay that way** (design doc §4.2). A reserved
 * command is exact and wins outright. An alias is also T0 and is checked before any fuzzy match,
 * because an alias that happens to fuzzy-match an app must expand rather than launch the app.
 * Only then does T1 run, and T3 is what is left.
 *
 * Suspending because half the commands await a Room write or a file write. The caller captures the
 * submitted line first, so what gets recorded is what was submitted rather than whatever has been
 * typed since.
 */
suspend fun dispatchCommand(
    input: String,
    env: CommandEnvironment,
    today: LocalDate = LocalDate.now(),
    includeAliases: Boolean = true,
    allowSlm: Boolean = true,
): CommandOutcome {
    if (input.isBlank()) return CommandOutcome.Silent

    // A definition is configuration rather than a command, and is checked before anything can read
    // it as a *use* of the alias it defines.
    parseAliasDefinition(input)?.let { attempt ->
        return CommandOutcome.say(describeDefineAliasResult(env.defineAlias(attempt.name, attempt.command)))
    }

    val top = rankCandidates(input, buildCandidates(env.apps, env.aliases, includeAliases), env.usageCounts())
        .firstOrNull()
    // **T2, then T3.** Reaching here means T0 and T1 both missed, which is exactly and only where
    // the SLM is allowed to run — the root hard constraint is that it never sits on the fast path,
    // and structurally it cannot, because the deterministic tiers have already answered or
    // declined by this line.
        ?: return runSlm(input, env, today, allowSlm)
            ?: CommandOutcome.say(unmatchedResponse(input, escalationTier(input)))

    return when (val candidate = top.candidate) {
        // Built from the registry rather than by reassembling and re-parsing a string: the matcher
        // already decided which token won and what the remainder is.
        is Candidate.Command ->
            buildReservedCommand(candidate.token, top.argument)
                ?.let { runReserved(it, env, today) }
                ?: CommandOutcome.Silent

        // Dispatched as if the expansion had been typed — one Enter, not two, since aliases exist
        // for muscle memory.
        //
        // ⚠️ `includeAliases = false` on the recursion, and it is load-bearing in a way the old
        // exact-only lookup never needed. `evaluateAliasDefinition` stops an alias *pointing at*
        // another, but nothing stops an expansion from fuzzy-matching some unrelated alias's name
        // — and that pair would ping-pong forever. Dropping aliases for the second pass makes "one
        // substitution" structural rather than a property of the data.
        is Candidate.Alias ->
            dispatchCommand(joinArgument(candidate.expansion, top.argument), env, today, includeAliases = false)

        is Candidate.App -> {
            env.launchApp(candidate.target)
            CommandOutcome.Silent
        }
    }
}

/**
 * T2: ask the model, and act on the answer by re-entering dispatch with the line it implies.
 *
 * **Null means "T2 had nothing", and every path that produces it is ordinary.** Single-word input
 * never reaches the model at all (§4.2: prose is multi-word, and T1 already had its shot at a
 * one-word app name). No model, a failed inference, output that will not validate, a label outside
 * §4.3, or an intent with no command line — all of them fall through to T3 rather than guessing.
 *
 * **`allowSlm = false` on the recursion, and it is load-bearing.** The synthesised line is
 * dispatched exactly as if it had been typed, and for `open_app` that line is a bare app name
 * which may itself miss. Without the guard that miss would call the model again on text the model
 * just produced, and keep doing so. Same shape as `includeAliases` above, and for the same reason:
 * making "one substitution" structural rather than a property of the data.
 *
 * Aliases are left enabled on the recursion, deliberately — if the model says to open `gm` and
 * `gm` is an alias, expanding it is what the user means.
 */
private suspend fun runSlm(
    input: String,
    env: CommandEnvironment,
    today: LocalDate,
    allowSlm: Boolean,
): CommandOutcome? {
    if (!allowSlm) return null
    if (escalationTier(input) != EscalationTier.T2) return null

    val line = resolveIntent(input, env.inferenceEngine)?.let(::commandLineFor) ?: return null
    return dispatchCommand(line, env, today, allowSlm = false)
}

/** Re-attaches an argument to an expansion, without inventing a trailing space when there is none. */
private fun joinArgument(head: String, argument: String): String =
    if (argument.isEmpty()) head else "$head $argument"

private suspend fun runReserved(
    command: ReservedCommand,
    env: CommandEnvironment,
    today: LocalDate,
): CommandOutcome = when (command) {

    // Browse views: Enter acts only once the filter has narrowed to exactly one row. Otherwise
    // the list *is* the response and selection is by tapping.
    is ReservedCommand.Apps -> {
        // Display names are passed through so a disambiguated name stays typeable (T167) —
        // dropping them here would silently undo that fix for Enter while leaving the list fine.
        val filtered = env.apps.filteredBy(command.query, displayNames(env.apps))
        val sole = filtered.soleAutoLaunchTarget(command.query)
        if (sole == null) CommandOutcome.Stay else {
            env.launchApp(sole.packageName)
            CommandOutcome.Silent
        }
    }

    is ReservedCommand.Notes -> {
        val sole = env.notesMatching(parseNoteQuery(command.query)).soleAutoOpenTarget(command.query)
        if (sole == null) CommandOutcome.Stay else {
            env.openNote(sole)
            CommandOutcome.Silent
        }
    }

    // The editor appearing is the response; a `:note` line in the scrollback would be noise you
    // scroll past forever.
    is ReservedCommand.Note -> {
        env.openEditor(command.query)
        CommandOutcome.Silent
    }

    is ReservedCommand.Task -> {
        val text = command.text.trim()
        if (text.isEmpty()) {
            CommandOutcome.say("nothing to add — try :task buy milk @fri")
        } else {
            // Shortcuts resolve here, at the moment of entry, so what reaches the body is always a
            // literal ISO date (T153). An undated task is due today.
            val item = parseChecklist("- [ ] " + resolveDueInText(text, today)).single()
            val due = item.due ?: today
            env.addTask(item.text, due)
            // Echoes the resolved date rather than what was typed — the only feedback that `@fri`
            // was understood at all.
            CommandOutcome.say("added: ${item.text} (due ${shortDate(due)})")
        }
    }

    // Browse-only: the list is the response and rows are tapped.
    ReservedCommand.Tasks, ReservedCommand.TasksDone -> CommandOutcome.Stay

    ReservedCommand.Alias -> CommandOutcome.say(env.aliases.formatAliasListing())

    ReservedCommand.Settings -> {
        env.openSettings()
        CommandOutcome.Silent
    }

    // With access the browse view is already showing and Enter has nothing to commit to. Without
    // it, Enter deep-links straight to the permission screen, no explanation page first.
    ReservedCommand.Notif -> if (env.hasNotificationAccess) {
        CommandOutcome.Stay
    } else {
        env.openNotificationAccess()
        CommandOutcome.Silent
    }

    is ReservedCommand.Google -> {
        val url = googleSearchUrl(command.query)
        when {
            url == null -> CommandOutcome.say("nothing to search — try :google flat white recipe")
            env.openUrl(url) -> CommandOutcome.say(searchedText(command.query))
            else -> CommandOutcome.say("no browser available to open the search")
        }
    }

    ReservedCommand.Export -> when (val result = env.exportCommandLog()) {
        is CommandLogExportResult.Written -> CommandOutcome.say(exportedText(result.stats, result.location))
        // Reported rather than swallowed, deliberately inverting `CommandLogRepository.record`'s
        // rule: writing the log must never cost an app launch, but exporting *is* the command.
        is CommandLogExportResult.Failed -> CommandOutcome.say("export failed: ${result.reason}")
    }

    ReservedCommand.Help -> CommandOutcome.say(helpText())
}

/**
 * Whether a submitted line belongs in the T014 training log at all.
 *
 * **An alias definition is excluded.** `alias sp = spotify` is configuration, not a command anyone
 * will later phrase in natural language, so it is not an example of anything the model needs to
 * learn — and logging it would file it as a T3 *miss*, teaching the set that a line which worked
 * perfectly was unresolvable.
 */
fun isTrainingExample(input: String): Boolean =
    input.isNotBlank() && parseAliasDefinition(input) == null

/**
 * Classifies a submitted line for the T014 log, without running it.
 *
 * **Rewritten at T184 to read the same ranking dispatch acts on**, which it had stopped doing.
 * It used to re-derive the answer from three separate matchers — an exact token walk, an exact
 * alias lookup, a fuzzy app match — mirroring the cascade [dispatchCommand] no longer runs. Once
 * reserved tokens became fuzzy-matchable (ADR-0010) the two disagreed: `:aps` launched the app
 * list and was logged as a T3 *miss*, which is training data that says a line which worked was
 * unresolvable. That is the failure `classifyResolution`'s own comment warned about — "a second
 * opinion that could disagree with the first" — arriving by a route it did not anticipate.
 *
 * **Tier is now the pipeline stage that answered, which is a slight shift in meaning.** An exact
 * match is T0 and anything the fuzzy ladder rescued is T1, regardless of *what* matched — so
 * typing an app's full name logs T0 where it used to log T1. The tiers survive this redesign as
 * stage labels rather than as separate lookups (ADR-0010), and stage is what the label now says.
 *
 * Suspending because the ranking consults the usage index. It is already called from inside
 * dispatch's coroutine, so this costs no extra hop.
 */
suspend fun resolutionOf(input: String, env: CommandEnvironment): CommandResolution {
    val top = rankCandidates(input, buildCandidates(env.apps, env.aliases), env.usageCounts())
        .firstOrNull()
        // T2 is skipped entirely: the SLM does not exist, and when it does its output is what this
        // data trains, so logging its guesses as ground truth would be circular.
        ?: return CommandResolution(CommandResolution.Tier.T3, CommandResolution.UNRESOLVED)

    val tier =
        if (top.strategy == MatchStrategy.EXACT) CommandResolution.Tier.T0 else CommandResolution.Tier.T1
    return CommandResolution(tier, top.candidate.target)
}
