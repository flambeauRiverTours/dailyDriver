package com.flambeauriver.dailydriver.apps

/**
 * Distinct first letters present in the receiver, uppercased, in list order (already A→Z since
 * AppIndex sorts). Entries with a blank label are skipped rather than crashed on — PackageManager
 * doesn't strictly guarantee a non-empty loadLabel() result, and a real device is where that
 * theoretical edge case actually shows up, not a test.
 */
fun List<AppEntry>.fastScrollLetters(): List<Char> =
    mapNotNull { it.label.firstOrNull()?.uppercaseChar() }.distinct()

/** Index of the first entry whose label starts with [letter] (case-insensitive), or null if none does. */
fun List<AppEntry>.indexOfFirstLabelStartingWith(letter: Char): Int? {
    val index = indexOfFirst { it.label.firstOrNull()?.uppercaseChar() == letter.uppercaseChar() }
    return index.takeIf { it >= 0 }
}
