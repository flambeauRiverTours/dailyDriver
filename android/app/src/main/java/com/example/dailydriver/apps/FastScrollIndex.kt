package com.example.dailydriver.apps

/** Distinct first letters present in the receiver, uppercased, in list order (already A→Z since AppIndex sorts). */
fun List<AppEntry>.fastScrollLetters(): List<Char> =
    map { it.label.first().uppercaseChar() }.distinct()

/** Index of the first entry whose label starts with [letter] (case-insensitive), or null if none does. */
fun List<AppEntry>.indexOfFirstLabelStartingWith(letter: Char): Int? {
    val index = indexOfFirst { it.label.first().uppercaseChar() == letter.uppercaseChar() }
    return index.takeIf { it >= 0 }
}
