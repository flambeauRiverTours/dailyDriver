package com.example.dailydriver.notes

/**
 * What the text after `:notes` means (T026).
 *
 * Deliberately a parsed type rather than a raw string handed to the DAO: the `#tag` form and the
 * free-text form hit *different* queries — one an indexed lookup on `note_tags`, the other a
 * `LIKE` scan over title and body — and deciding which in the UI layer would put a storage
 * concern in a composable.
 */
sealed interface NoteQuery {

    /** No filter — the whole list, newest first. */
    data object All : NoteQuery

    /** Substring match over title and body. [term] is raw; escape it with [escapeLike] for SQL. */
    data class Text(val term: String) : NoteQuery

    /** Exact match against the `note_tags` index. [tag] is already normalised (lowercase, no `#`). */
    data class Tag(val tag: String) : NoteQuery
}

/**
 * Reads a `:notes` query.
 *
 * A lone `#something` filters by tag; anything else is free text. **The tag grammar isn't
 * re-implemented here** — it defers to [parseTags], so searching for a tag and writing one can't
 * drift apart. That also means `#123` isn't a tag (T019: a tag needs at least one letter), so it
 * falls through to text search and finds notes literally containing "#123", which is what someone
 * typing an issue number actually wants.
 *
 * Requiring a single whitespace-free token keeps `#work notes` as a text search rather than
 * silently dropping "notes" and filtering by tag alone.
 */
fun parseNoteQuery(raw: String): NoteQuery {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return NoteQuery.All

    if (trimmed.startsWith("#") && trimmed.none { it.isWhitespace() }) {
        parseTags(trimmed).singleOrNull()?.let { return NoteQuery.Tag(it) }
    }
    return NoteQuery.Text(trimmed)
}

/** The character `escapeLike` marks literals with; paired with `ESCAPE '\'` in the DAO's SQL. */
const val LIKE_ESCAPE = '\\'

/**
 * Makes a search term safe to drop inside a SQL `LIKE` pattern.
 *
 * `%` and `_` are wildcards there, so an unescaped `%` matches everything and an unescaped `_`
 * matches any single character — searching for "50%" or "snake_case" would otherwise return
 * results that don't contain what you typed. The escape character itself has to be escaped first,
 * or escaping would corrupt terms that legitimately contain a backslash.
 */
fun escapeLike(term: String): String = buildString {
    for (character in term) {
        if (character == LIKE_ESCAPE || character == '%' || character == '_') append(LIKE_ESCAPE)
        append(character)
    }
}

/**
 * The note to open on its own when the `:notes` list has narrowed to exactly one — via Enter, or
 * after a second's pause in typing.
 *
 * Deliberately identical in shape to `:apps`' `soleAutoLaunchTarget`, **including the blank-query
 * guard**: auto-open is a response to *filtering down* to one result, never to simply having one
 * note. Bare `:notes` means "show me my notes", and someone with a single note should still see
 * it listed rather than be thrown straight into the editor with no way to have just looked.
 */
fun List<Note>.soleAutoOpenTarget(query: String): Note? =
    if (query.isBlank()) null else singleOrNull()

/** How much of the body a list row shows before trailing off. */
private const val SNIPPET_MAX = 80

/**
 * The preview line under a note's title in the list (T025).
 *
 * Exists because blank titles default to a `Thu 20 Aug - 12:24 PM` stamp (T021), which is the
 * common case for journal entries — a list of those is a list of timestamps, indistinguishable
 * without a look at the content.
 *
 * Takes the first line with something on it, not simply the first line: a body that opens with a
 * blank line or leading newlines would otherwise preview as empty.
 */
fun noteSnippet(body: String): String {
    val firstMeaningfulLine = body.lineSequence()
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        ?: return ""

    return if (firstMeaningfulLine.length <= SNIPPET_MAX) {
        firstMeaningfulLine
    } else {
        firstMeaningfulLine.take(SNIPPET_MAX).trimEnd() + "…"
    }
}
