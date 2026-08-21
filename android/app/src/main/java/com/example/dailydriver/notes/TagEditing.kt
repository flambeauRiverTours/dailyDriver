package com.example.dailydriver.notes

/**
 * Adding and removing tags by editing the body's hashtags (T139).
 *
 * **Chips are a view over the body, not a second place tags live** (design check-in, 2026-08-21).
 * `NoteDao.upsertLocal` derives tags from the body and deliberately ignores any set passed in, so
 * a chip that wrote to `note_tags` directly would need that guarantee removed and would allow a
 * tag to exist with nothing in the body backing it. The task list originally suggested unioning
 * the two, which is the same drift §6.2 and ADR-0004 exist to prevent: two writers, one fact.
 *
 * So the chip row adds and removes text. Everything downstream — parsing, indexing, sync — carries
 * on unchanged, because from its point of view nothing new happened.
 */

/**
 * Appends a tag to the body, if it isn't already there.
 *
 * Placed at the end on its own line rather than inline, since a tag added from a chip has no
 * position in the prose to belong to. Idempotent: adding a tag the body already carries returns
 * the body untouched, so the chip row can't produce `#work #work`.
 */
fun withTag(body: String, tag: String): String {
    val clean = tag.trim().removePrefix("#").lowercase()
    if (clean.isEmpty() || !clean.any(Char::isLetter)) return body
    if (clean in parseTags(body)) return body

    return when {
        body.isEmpty() -> "#$clean"
        body.endsWith("\n") -> "$body#$clean"
        else -> "$body\n#$clean"
    }
}

/**
 * Removes every occurrence of a tag from the body.
 *
 * **Every occurrence, not the first.** A tag is a property of the note rather than of a position
 * in it, so removing the chip has to mean the note no longer carries the tag; leaving a second
 * `#work` further down would put the chip straight back on the next parse and read as the tap
 * having failed.
 *
 * Takes the whitespace in front of the tag with it, so removing an inline tag doesn't leave a
 * double space mid-sentence. Lines left empty by the removal are dropped only when the tag was
 * the whole line, which is how [withTag] writes them.
 */
fun withoutTag(body: String, tag: String): String {
    val clean = tag.trim().removePrefix("#").lowercase()
    if (clean.isEmpty()) return body

    val pattern = Regex("""(?<=^|\s)#${Regex.escape(clean)}\b""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

    return body.lineSequence()
        .map { line ->
            val stripped = pattern.replace(line) { "" }
            // Collapse the gap the tag left behind, without touching indentation.
            if (stripped == line) line else stripped.replace(Regex(" {2,}"), " ").trimEnd()
        }
        // A line that existed only to carry the tag goes with it. A line that was already blank
        // before this is left alone, since blank lines are meaningful spacing in prose.
        .filterIndexed { index, line ->
            line.isNotBlank() || body.lines().getOrNull(index)?.isBlank() == true
        }
        .joinToString("\n")
}
