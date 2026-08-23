package com.flambeauriver.dailydriver.alias

/**
 * Expanding an alias, with whatever was typed after it (T173).
 *
 * `alias a = :apps` then `a firefox` runs `:apps firefox`. Before this, the lookup was
 * `aliases[input.trim()]` — a whole-string match — so anything after the alias name meant no match
 * at all and the line fell through to T3.
 *
 * **The rule is uniform: the remainder is always appended, whatever the alias points at**
 * (decided 2026-08-22). The alternative was to append only when the expansion is a reserved
 * command, on the grounds that `alias s = Spotify` makes `s foo` expand to `Spotify foo`, which
 * matches no app and dead-ends at T3. That was rejected: passing an argument to an alias that
 * takes none is the user's own doing, and a rule that silently declines to expand in some cases is
 * harder to hold in your head than one that always expands.
 *
 * The consequence is worth knowing rather than fixing — an app alias given an argument logs a T3
 * miss on a string the user never typed (T014). **Don't add a target-type special case here** to
 * avoid it.
 */
fun expandAlias(input: String, aliases: Map<String, String>): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null

    aliases[trimmed]?.let { return it }

    // Only the first token can be an alias. Splitting on the first run of whitespace rather than
    // tokenising keeps the rest of the line exactly as typed, including any interior spacing that
    // a `:note` title or a `:task` description is entitled to.
    val name = trimmed.substringBefore(' ').substringBefore('\t')
    if (name == trimmed) return null

    val expansion = aliases[name] ?: return null
    val rest = trimmed.removePrefix(name).trim()
    return if (rest.isEmpty()) expansion else "$expansion $rest"
}
