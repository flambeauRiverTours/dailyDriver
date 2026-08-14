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
