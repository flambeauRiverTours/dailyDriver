package com.flambeauriver.dailydriver.command

import com.flambeauriver.dailydriver.alias.describeDefineAliasResult
import com.flambeauriver.dailydriver.alias.expandAlias
import com.flambeauriver.dailydriver.alias.formatAliasListing
import com.flambeauriver.dailydriver.alias.parseAliasDefinition
import com.flambeauriver.dailydriver.apps.displayNames
import com.flambeauriver.dailydriver.apps.filteredBy
import com.flambeauriver.dailydriver.apps.soleAutoLaunchTarget
import com.flambeauriver.dailydriver.apps.topMatch
import com.flambeauriver.dailydriver.notes.parseChecklist
import com.flambeauriver.dailydriver.notes.parseNoteQuery
import com.flambeauriver.dailydriver.notes.resolveDueInText
import com.flambeauriver.dailydriver.notes.soleAutoOpenTarget
import com.flambeauriver.dailydriver.search.googleSearchUrl
import com.flambeauriver.dailydriver.search.searchedText
import com.flambeauriver.dailydriver.shell.shortDate
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
): CommandOutcome {
    if (input.isBlank()) return CommandOutcome.Silent

    parseReservedCommand(input)?.let { return runReserved(it, env, today) }

    // A definition is configuration rather than a command, and is checked before expansion so
    // that `alias a = x` can never be read as a *use* of the alias `a`.
    parseAliasDefinition(input)?.let { attempt ->
        return CommandOutcome.say(describeDefineAliasResult(env.defineAlias(attempt.name, attempt.command)))
    }

    // T0. An alias is dispatched as if its expansion had been typed — one Enter, not two, since
    // aliases exist for muscle memory. This cannot recurse: an alias may never point at another,
    // which `evaluateAliasDefinition` rejects at definition time.
    expandAlias(input, env.aliases)?.let { return dispatchCommand(it, env, today) }

    // T1. The best fuzzy match wins, even among near-ties (T008).
    env.apps.topMatch(input)?.let {
        env.launchApp(it.packageName)
        return CommandOutcome.Silent
    }

    // T3. T2 is skipped while the SLM is absent — see [unmatchedResponse] for why prose degrades
    // here rather than dead-ending.
    return CommandOutcome.say(unmatchedResponse(input, escalationTier(input)))
}

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
 * Classifies a submitted line for the T014 log, without running it.
 *
 * **Kept beside dispatch so the two can never disagree about which tier answered.** The log's
 * whole value is that its `tier` reflects what actually happened, and before T178 the
 * classification was duplicated across three call sites in `ShellScreen` — with a fourth path,
 * the note auto-open, that logged nothing at all.
 */
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

fun resolutionOf(input: String, env: CommandEnvironment): CommandResolution = classifyResolution(
    reservedToken = reservedCommandToken(input),
    aliasExpansion = expandAlias(input, env.aliases),
    appPackage = env.apps.topMatch(input)?.packageName,
)
