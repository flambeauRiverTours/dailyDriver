package com.example.dailydriver.apps

/**
 * In-memory index of launchable apps, always exposed sorted by label. Rebuilt wholesale on
 * every refresh rather than diffed — the PackageManager query is cheap enough that incremental
 * update logic isn't worth the complexity.
 */
class AppIndex {

    private var entries: List<AppEntry> = emptyList()

    val apps: List<AppEntry> get() = entries

    fun replaceAll(newEntries: Collection<AppEntry>) {
        entries = newEntries.sortedBy { it.label.lowercase() }
    }
}
