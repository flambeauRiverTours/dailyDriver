package com.flambeauriver.dailydriver.alias

/** A successfully parsed `alias <name> = <command>` attempt, before conflict checking. */
data class AliasDefinitionAttempt(val name: String, val command: String)

private val DEFINITION_PATTERN = Regex("""^alias\s+(\S+)\s*=\s*(.+)$""")

/**
 * Parses the `alias <name> = <command>` shape from design doc §4.7. Deliberately not part of
 * T140's `:token` registry — bare, no colon prefix, matching the doc's own example — which is
 * also why alias names are rejected if they start with `:`: that keeps the two namespaces
 * disjoint, so there's no precedence question between reserved commands and aliases at T0.
 */
fun parseAliasDefinition(input: String): AliasDefinitionAttempt? {
    val match = DEFINITION_PATTERN.matchEntire(input) ?: return null
    val (name, rawCommand) = match.destructured
    if (name.startsWith(":")) return null
    // The regex's `.+` for the command is satisfied by a single trailing space (`.` matches any
    // character, including whitespace) — "alias gm = " would otherwise parse to an empty command
    // instead of failing to match at all.
    val command = rawCommand.trim()
    if (command.isEmpty()) return null
    return AliasDefinitionAttempt(name, command)
}

/** The outcome of checking a parsed [AliasDefinitionAttempt] against the currently stored aliases. */
sealed interface DefineAliasResult {
    data class Created(val name: String, val command: String) : DefineAliasResult
    data class Overwritten(val name: String, val command: String, val previousCommand: String) : DefineAliasResult
    data class RejectedSelfReference(val name: String) : DefineAliasResult
    data class RejectedPointsAtAlias(val name: String, val targetAliasName: String) : DefineAliasResult

    /** Nothing on the device matches the target (T162). */
    data class RejectedUnknownTarget(val name: String, val command: String) : DefineAliasResult

    /** Several apps match, so the choice is the user's to make (T162). */
    data class RejectedAmbiguousTarget(
        val name: String,
        val command: String,
        val candidates: List<String>,
    ) : DefineAliasResult
}

/**
 * Checks a definition against [existingAliases] (name -> command) for self-reference and
 * pointing-at-another-alias, otherwise reports whether it's a new alias or an overwrite.
 *
 * The points-at-alias check alone — reject if [command] exactly matches an *existing* alias's
 * name — is sufficient to prevent cycles of any length, not just direct pairs: completing a
 * cycle of any size requires its last link to target something already in the store (you can't
 * create every link in a cycle simultaneously), so whichever definition would close the loop is
 * always caught right here. No graph traversal needed.
 *
 * Overwriting an existing name is allowed, not rejected — real alias systems do this silently —
 * but reported distinctly from a fresh Created so the caller can say which happened.
 */
fun evaluateAliasDefinition(
    name: String,
    command: String,
    existingAliases: Map<String, String>,
    target: AliasTarget = AliasTarget.Command,
): DefineAliasResult {
    val trimmedCommand = command.trim()

    // Structural checks first, then whether the target exists. Order matters: `alias a = a` is
    // self-referential regardless of whether anything called "a" is installed, and reporting it as
    // an unknown target would send you looking for a missing app instead of a typo.
    return when {
        trimmedCommand == name -> DefineAliasResult.RejectedSelfReference(name)
        existingAliases.containsKey(trimmedCommand) ->
            DefineAliasResult.RejectedPointsAtAlias(name, targetAliasName = trimmedCommand)

        target is AliasTarget.Unknown -> DefineAliasResult.RejectedUnknownTarget(name, trimmedCommand)
        target is AliasTarget.Ambiguous ->
            DefineAliasResult.RejectedAmbiguousTarget(name, trimmedCommand, target.candidates)

        else -> {
            // An app target is stored as the app's full label rather than what was typed, so the
            // alias cannot silently repoint when the index changes. See [resolveAliasTarget].
            val stored = (target as? AliasTarget.App)?.resolvedCommand ?: trimmedCommand
            if (existingAliases.containsKey(name)) {
                DefineAliasResult.Overwritten(name, stored, previousCommand = existingAliases.getValue(name))
            } else {
                DefineAliasResult.Created(name, stored)
            }
        }
    }
}

/** User-facing transcript text for the outcome of a definition attempt. */
fun describeDefineAliasResult(result: DefineAliasResult): String = when (result) {
    is DefineAliasResult.Created -> "alias '${result.name}' created"
    is DefineAliasResult.Overwritten -> "alias '${result.name}' updated (was: ${result.previousCommand})"
    is DefineAliasResult.RejectedSelfReference -> "can't alias '${result.name}' to itself"
    is DefineAliasResult.RejectedPointsAtAlias ->
        "can't alias '${result.name}' to '${result.targetAliasName}' — aliases can't point at other aliases"
    is DefineAliasResult.RejectedUnknownTarget ->
        "nothing matches '${result.command}' — alias not created"
    // Names the candidates rather than just reporting ambiguity, so the fix is to retype one of
    // them rather than to guess what the launcher saw.
    is DefineAliasResult.RejectedAmbiguousTarget ->
        "'${result.command}' matches ${result.candidates.joinToString(", ")} — be more specific"
}
