package com.flambeauriver.dailydriver.apps

/**
 * In-memory index of launchable apps, always exposed sorted by label and unique per package.
 * Rebuilt wholesale on every refresh rather than diffed — the PackageManager query is cheap
 * enough that incremental update logic isn't worth the complexity.
 */
class AppIndex {

    private var entries: List<AppEntry> = emptyList()

    val apps: List<AppEntry> get() = entries

    /**
     * A single package can expose more than one CATEGORY_LAUNCHER activity — PackageManager
     * returns one entry per activity, not per package (confirmed on-device: Google's app does
     * this). IntentLauncher can only ever launch a package's one default activity regardless of
     * which duplicate is tapped, so distinctBy here isn't just satisfying LazyColumn's unique-key
     * requirement — showing both rows would be misleading, since they'd do the same thing.
     * Applied after sorting so the kept duplicate is deterministic (alphabetically-first label),
     * not whatever order PackageManager happened to return.
     */
    fun replaceAll(newEntries: Collection<AppEntry>) {
        entries = newEntries.sortedBy { it.label.lowercase() }.distinctBy { it.packageName }
    }
}
