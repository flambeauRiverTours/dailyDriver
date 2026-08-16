package com.example.dailydriver.alias

/**
 * In-memory index of stored aliases, read synchronously by T0 resolution (< 5ms budget, design
 * doc §4.2 — too tight to hit Room on every keystroke). Mirrors AppIndex's shape: rebuilt
 * wholesale from Room by AliasRepository, not diffed.
 */
class AliasIndex {

    private var entries: Map<String, String> = emptyMap()

    fun asMap(): Map<String, String> = entries

    fun replaceAll(aliases: Collection<Alias>) {
        entries = aliases.associate { it.name to it.command }
    }

    /** Exact match only — T0, not T1's fuzzy matching. */
    fun resolve(trigger: String): String? = entries[trigger]
}
