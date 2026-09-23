package com.flambeauriver.dailydriver.slm

/**
 * Turns a validated [IntentResult] back into a line of text the existing dispatcher understands
 * (T036).
 *
 * **T2 does not get its own set of actions, and that is the whole design.** Every intent maps to a
 * command line that T0 already handles, so `add_task` runs the same code `:task` runs and inherits
 * its due-date shortcuts, its Room write and its outbox row for free. The alternative was a second
 * `when` duplicating twelve commands' behaviour, which is the shape `android/CLAUDE.md` warns
 * about under "command dispatch has exactly one home".
 *
 * **The `when` is exhaustive over [Intent] on purpose.** Adding a label to §4.3 now fails to
 * compile until someone says what it does, which is the durable fix for the hand-maintained list
 * `StructuredOutputTest` was caught getting wrong on 2026-09-21. A `null` here is a deliberate
 * "no command line expresses this", not a gap.
 */
fun commandLineFor(result: IntentResult): String? = when (result.intent) {

    // Bare text, not a token: this re-enters the cascade at T0/T1 and gets the same exact-then-
    // fuzzy treatment a typed app name gets, including the usage ranking. Launching by package
    // name here would quietly bypass all of it.
    Intent.OPEN_APP -> result.slots["app"]?.takeIf { it.isNotBlank() }

    Intent.LIST_APPS -> ":apps"
    Intent.OPEN_SETTINGS -> ":settings"

    // `:note <name>` opens-or-creates (T025), so one token serves both intents. They stay separate
    // labels because §4.3 describes what the user meant, and the model should not have to know
    // that this app happens to collapse them.
    Intent.CREATE_NOTE, Intent.OPEN_NOTE ->
        ":note".withArgument(result.slots["title"] ?: result.slots["name"])

    Intent.SEARCH_NOTES -> ":notes".withArgument(result.slots["query"])

    Intent.ADD_TASK -> {
        val text = result.slots["text"]?.takeIf { it.isNotBlank() }
        // The due date goes back as the `@` token the body syntax already defines, so T153's
        // shortcut parser resolves `@tomorrow` exactly as it does when typed by hand. Emitting an
        // ISO date here instead would bypass the one place that conversion is tested.
        val due = result.slots["due"]?.takeIf { it.isNotBlank() }
        text?.let { if (due == null) ":task $it" else ":task $it @$due" }
    }

    Intent.LIST_TASKS -> ":tasks"
    Intent.LIST_COMPLETED_TASKS -> ":tasksdone"

    // Not a reserved token. `alias x = y` is configuration and is parsed before dispatch even
    // looks at the candidate list, so the line has to be in that form rather than `:alias`.
    Intent.DEFINE_ALIAS -> {
        val name = result.slots["name"]?.takeIf { it.isNotBlank() }
        val command = result.slots["command"]?.takeIf { it.isNotBlank() }
        if (name != null && command != null) "alias $name = $command" else null
    }

    Intent.LIST_ALIASES -> ":alias"
    Intent.LIST_NOTIFICATIONS -> ":notif"
    Intent.WEB_SEARCH -> ":google".withArgument(result.slots["query"])
    Intent.EXPORT_LOG -> ":export"
    Intent.HELP -> ":help"

    // **Both of these are genuinely unreachable as commands, not oversights.**
    //
    // `check_item` describes tapping a checkbox in the checklist editor (§4.3); there is no line
    // you could type to do it, because the item it refers to is on screen rather than named.
    // `create_checklist` is a *conversion* applied to an open note (T138's undoable action), not
    // something the prompt can start. Mapping either to `:note` would be worse than refusing: it
    // would open an editor and look like the command worked.
    Intent.CHECK_ITEM, Intent.CREATE_CHECKLIST -> null

    // `parseIntent` already abstains on an explicit `unknown` (T035), so this branch should be
    // unreachable. It exists because the `when` is exhaustive, and it agrees with that decision
    // rather than inventing a second answer for the same case.
    Intent.UNKNOWN -> null
}

/** Appends an argument only when there is one, so `:notes` and `:notes tax` are both well formed. */
private fun String.withArgument(argument: String?): String =
    argument?.trim()?.takeIf { it.isNotEmpty() }?.let { "$this $it" } ?: this
