package com.example.dailydriver.alias

/**
 * Formats stored aliases for `:alias` (design doc §4.7, T144) — one `name = command` line per
 * alias, sorted alphabetically by name for a stable, predictable listing regardless of what
 * backs the map. `=` matches the definition syntax (`alias gm = chrome`) rather than inventing a
 * new notation for the same relationship.
 *
 * **Case-insensitive sort**, matching `AppIndex`'s ordering of app labels. `toSortedMap()` was
 * the original implementation and was wrong for this: natural `String` order is codepoint order,
 * so every capitalised name sorted ahead of every lowercase one (`Zoo` before `apple`). Alias
 * names are user-chosen and casing is incidental, so that grouping is noise. Ties between names
 * differing only in case keep their relative order, which `sortedBy` guarantees by being stable.
 */
fun Map<String, String>.formatAliasListing(): String {
    if (isEmpty()) return "no aliases defined"
    return entries.sortedBy { it.key.lowercase() }
        .joinToString("\n") { (name, command) -> "$name = $command" }
}
