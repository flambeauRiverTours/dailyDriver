package com.example.dailydriver.apps

/**
 * Case-insensitive substring match on the label, preserving whatever order the receiver was in
 * (AppIndex already keeps entries sorted). Deliberately not fuzzy/edit-distance matching — that's
 * T008's job (T1); this is the lightweight filter behind live-typing in the `:apps` browse view.
 */
fun List<AppEntry>.filteredBy(query: String): List<AppEntry> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return this
    return filter { it.label.contains(trimmed, ignoreCase = true) }
}

/**
 * The app to open automatically when a filter narrows to exactly one result — via Enter, or after
 * a brief typing pause. Null means don't auto-launch.
 *
 * The blank-[query] guard matters: bare `:apps` on a device with a single installed app would
 * otherwise launch it the instant you finished typing the command, when what you asked for was to
 * browse. Auto-launch is only ever a response to *filtering down* to one result.
 */
fun List<AppEntry>.soleAutoLaunchTarget(query: String): AppEntry? =
    if (query.isBlank()) null else singleOrNull()
