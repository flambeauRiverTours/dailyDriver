package com.flambeauriver.dailydriver.apps

/**
 * Case-insensitive substring match on the label **or the display name**, preserving whatever order
 * the receiver was in (AppIndex already keeps entries sorted). Deliberately not fuzzy/edit-distance
 * matching — that's T008's job (T1); this is the lightweight filter behind live-typing in the
 * `:apps` browse view.
 *
 * **[names] is why `:apps Messages (Google)` works** (T167). T164 added publisher suffixes for
 * ambiguous labels and passed them only to the view, so the list showed a name the filter could not
 * find: two rows both reading `Messages`, and typing what distinguished them matched nothing. A
 * name on screen you cannot type is worse than no suffix at all.
 *
 * **⚠️ This does not contradict T164's rule that *matching* sees the real label.** That rule is
 * about [rankedMatches], where fuzzy scoring against `Messages (Google)` is genuinely worse for
 * someone typing `mess` than scoring against `Messages`. A substring filter has no score to
 * degrade — `mess` is contained in both — so checking the longer string can only add matches.
 *
 * [names] defaults to empty so callers that have no display map behave exactly as before.
 */
fun List<AppEntry>.filteredBy(query: String, names: Map<String, String> = emptyMap()): List<AppEntry> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return this
    return filter { app ->
        app.label.contains(trimmed, ignoreCase = true) ||
            names[app.packageName]?.contains(trimmed, ignoreCase = true) == true
    }
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
