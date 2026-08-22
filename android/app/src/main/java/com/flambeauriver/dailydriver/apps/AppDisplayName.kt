package com.flambeauriver.dailydriver.apps

/**
 * Disambiguating apps that share a label (T164).
 *
 * Two apps both called `Messages` are indistinguishable in `:apps`, and worse in T1's preview line,
 * where the launcher says it will open "Messages" and you cannot tell which one it means.
 *
 * **Only duplicates get a suffix.** Adding a publisher to every row would be noise on the large
 * majority of names that are already unique, and this list is read at a glance.
 */

/**
 * Display names for [apps], suffixed with a publisher only where the label is ambiguous.
 *
 * Returned as a map keyed by package rather than a modified [AppEntry], so that matching (T1) keeps
 * working on the real label. A fuzzy match against `Messages (Google)` would score worse than
 * against `Messages` for someone typing "mess", which would make disambiguation actively harmful to
 * the thing it is meant to clarify.
 */
fun displayNames(apps: List<AppEntry>): Map<String, String> {
    val duplicated = apps.groupingBy { it.label.trim().lowercase() }.eachCount()

    return apps.associate { app ->
        val ambiguous = (duplicated[app.label.trim().lowercase()] ?: 0) > 1
        app.packageName to if (ambiguous) "${app.label} (${publisherOf(app.packageName)})" else app.label
    }
}

/**
 * The vendor segment of a package name: `com.google.android.apps.messaging` → `Google`.
 *
 * **Derived rather than looked up.** The obvious alternative — asking `PackageManager` who
 * installed it — sounds authoritative and is not: nearly everything reports the Play Store, so both
 * `Messages` apps would still read identically. The package name is the one piece of identity that
 * actually differs, and its second segment is by convention the organisation.
 *
 * Falls back to the whole package when there is no vendor segment to read, as in a two-part name
 * like `com.whatsapp`. Ugly, and honest: better a long unambiguous row than a confident wrong one.
 */
fun publisherOf(packageName: String): String {
    val segments = packageName.split('.').filter { it.isNotBlank() }

    // Fewer than three segments means there is no org segment between the TLD and the app, so
    // there is nothing to extract that is more informative than the package itself.
    if (segments.size < 3) return packageName

    return segments[1].replaceFirstChar { it.uppercase() }
}
