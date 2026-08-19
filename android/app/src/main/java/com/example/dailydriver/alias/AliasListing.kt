package com.example.dailydriver.alias

/**
 * Formats stored aliases for `:alias` (design doc §4.7, T144) — one `name = command` line per
 * alias, sorted alphabetically by name for a stable, predictable listing regardless of what
 * backs the map. `=` matches the definition syntax (`alias gm = chrome`) rather than inventing a
 * new notation for the same relationship.
 */
fun Map<String, String>.formatAliasListing(): String {
    if (isEmpty()) return "no aliases defined"
    return toSortedMap().entries.joinToString("\n") { (name, command) -> "$name = $command" }
}
