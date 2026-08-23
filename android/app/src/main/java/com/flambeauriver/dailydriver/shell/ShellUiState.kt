package com.flambeauriver.dailydriver.shell

import com.flambeauriver.dailydriver.alias.AliasDefinitionAttempt
import com.flambeauriver.dailydriver.alias.expandAlias
import com.flambeauriver.dailydriver.alias.parseAliasDefinition
import com.flambeauriver.dailydriver.apps.AppEntry
import com.flambeauriver.dailydriver.apps.filteredBy
import com.flambeauriver.dailydriver.apps.matchPreview
import com.flambeauriver.dailydriver.apps.soleAutoLaunchTarget
import com.flambeauriver.dailydriver.apps.topMatch
import com.flambeauriver.dailydriver.command.ReservedCommand
import com.flambeauriver.dailydriver.command.parseReservedCommand

/**
 * Everything the shell derives from one line of typed input (T179).
 *
 * **Pure, and that is the entire point.** All of this used to live inside `ShellScreen` as a run
 * of `remember` blocks, which made it unreachable from a unit test — including [previewText] and
 * [plainMatchTarget], which decide what T1 shows you and what Enter commits to. Those are among
 * the least appropriate things in the app to have had no tests.
 *
 * It is also what lets dispatch move to the ViewModel (T178): Enter's behaviour for `:apps` and
 * `:notes` is "act on the sole match", which needs the filtered list, which needed to stop being
 * composable-local before anything outside a composable could answer that question.
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
    /** True only for bare prompt input: no reserved command, no alias, no definition. */
    val isPlainQuery: Boolean,
    /** T1's best guess for bare input. No ambiguity gate — best match wins, even among near-ties. */
    val plainMatchTarget: AppEntry?,
    /** The dim line above the prompt showing what Enter would do. */
    val previewText: String?,
    /** An alias matching the whole input, with any argument already appended (T173). */
    val pendingAliasExpansion: String?,
) {
    val appsCommand: ReservedCommand.Apps? get() = reservedCommand as? ReservedCommand.Apps
    val notesCommand: ReservedCommand.Notes? get() = reservedCommand as? ReservedCommand.Notes
    val tasksCommand: ReservedCommand.Tasks? get() = reservedCommand as? ReservedCommand.Tasks
    val tasksDoneCommand: ReservedCommand.TasksDone? get() = reservedCommand as? ReservedCommand.TasksDone

    /** `:notif` only counts as a browse mode once access is granted; without it, Enter deep-links. */
    fun notifCommand(hasAccess: Boolean): ReservedCommand.Notif? =
        (reservedCommand as? ReservedCommand.Notif)?.takeIf { hasAccess }

    /**
     * What a pause in typing should act on: `:apps`' sole match, or T1's best guess at the bare
     * prompt. The two are never both set — `isPlainQuery` is false whenever a reserved command is.
     */
    val currentAutoLaunchTarget: AppEntry? get() = autoLaunchTarget ?: plainMatchTarget
}

/**
 * Derives the whole of [ShellUiState] from the current input.
 *
 * [names] is passed in rather than derived here because it is keyed on the **app index**, not on
 * the input (T164: whether "Messages" is ambiguous is a property of what is installed). Computing
 * it inside would rebuild a map over every installed app on every keystroke.
 */
fun shellUiState(
    input: String,
    apps: List<AppEntry>,
    aliases: Map<String, String>,
    names: Map<String, String>,
): ShellUiState {
    val reservedCommand = parseReservedCommand(input)
    val aliasDefinition = parseAliasDefinition(input)

    val appsCommand = reservedCommand as? ReservedCommand.Apps
    val filteredApps = appsCommand?.let { apps.filteredBy(it.query, names) }.orEmpty()

    // T1 applies to bare prompt input only — not while a reserved command is active, not to a
    // definition, and not to an alias name, which expands rather than fuzzy-matching.
    val isPlainQuery = reservedCommand == null &&
        aliasDefinition == null &&
        !aliases.containsKey(input.trim())

    return ShellUiState(
        reservedCommand = reservedCommand,
        aliasDefinition = aliasDefinition,
        filteredApps = filteredApps,
        // Sole-match only, which is stricter than the bare-prompt rule below and deliberately so:
        // `:apps` is a browse view, and committing to a best-of-several while you are still
        // reading the list would take the list away mid-scan.
        autoLaunchTarget = appsCommand?.let { filteredApps.soleAutoLaunchTarget(it.query) },
        isPlainQuery = isPlainQuery,
        plainMatchTarget = if (isPlainQuery) apps.topMatch(input) else null,
        previewText = when {
            reservedCommand != null || aliasDefinition != null -> null
            // An alias previews its expansion for the same reason a fuzzy match previews its
            // target: so you can see what Enter will do before committing to it.
            aliases.containsKey(input.trim()) -> aliases[input.trim()]
            else -> apps.matchPreview(input)
        },
        // Guarded against a definition so the two paths can never race: `alias x = y` contains an
        // "=" and is never itself an alias key, but the guard makes that structural.
        pendingAliasExpansion = expandAlias(input, aliases)?.takeIf { aliasDefinition == null },
    )
}
