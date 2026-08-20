package com.example.dailydriver.notes

/**
 * Extracts hashtags from a note's Markdown body (T019, design doc §4.6 — "inline hashtags parsed
 * out of the body on save and denormalized to a `tags` array for query").
 *
 * **The body stays the single source of truth.** These results are a query index over it, which
 * is why `NoteDao.replaceTags` rewrites them wholesale on every save rather than diffing: a tag
 * deleted from the body has to disappear from `note_tags` too.
 *
 * **This is a cross-implementation contract, not a local detail.** §7.3 lists tag parsing among
 * the rules that live in `Notes.Shared` and that "Android reimplements in Kotlin", and calls that
 * reimplementation "the seam where bugs will live". The rules below are pinned by
 * `fixtures/tag-parsing.json`, which both this module's tests and the C# suite execute (T042) —
 * so change the fixture and both implementations, never one alone.
 *
 * Tags are also expected to be assignable server-side eventually. That doesn't make this
 * redundant: §7.1 is local-first, so a note written offline needs its tags immediately or T026's
 * local search silently misses it. Same shape as `rev` — compute locally, let the server be
 * authoritative once it has seen the note.
 */
fun parseTags(body: String): List<String> =
    HASHTAG.findAll(withoutFencedCode(body))
        .map { it.groupValues[1] }
        // At least one letter, so "#123" and "#1" in prose stay prose. Without this, every issue
        // reference in a note body would become a tag.
        .filter { candidate -> candidate.any(Char::isLetter) }
        .map { it.lowercase() }
        .distinct()
        .toList()

/**
 * `#` plus letters/digits/underscore/hyphen, and only where the `#` follows start-of-input, a
 * line start, or whitespace.
 *
 * That preceding-character rule is what keeps `example.com#anchor` and `C#` out. It costs
 * `(#work)`, which doesn't match either — recorded as a known limit in the fixture rather than
 * special-cased, because every bracket added here is another rule the C# side has to match.
 *
 * Unicode classes rather than `[A-Za-z0-9_-]`: `#café` and `#日本語` are legitimate tags, and both
 * Kotlin and .NET support `\p{L}`/`\p{N}` with the same meaning.
 */
private val HASHTAG = Regex("""(?:^|\s)#([\p{L}\p{N}_-]+)""", RegexOption.MULTILINE)

/** A fence opener or closer: ``` or ~~~ (three or more), indented no more than 3 spaces. */
private val FENCE = Regex("""^ {0,3}(?:`{3,}|~{3,})""")

/**
 * Blanks out fenced code blocks so their contents can't produce tags — `#include`, `#define` and
 * CSS `#fff` are the realistic false positives, and all three arrive by pasting a snippet.
 *
 * Lines are replaced rather than removed so that line structure, and therefore the whitespace
 * preceding any surviving tag, is unchanged. An unclosed fence swallows the rest of the input,
 * which is CommonMark's own behaviour.
 *
 * Only *fenced* blocks. Indented (4-space) code blocks are deliberately not handled — see the
 * fixture's `knownLimits`.
 */
private fun withoutFencedCode(body: String): String = buildString {
    var insideFence = false
    for (line in body.lineSequence()) {
        if (FENCE.containsMatchIn(line)) {
            insideFence = !insideFence
        } else if (!insideFence) {
            append(line)
        }
        append('\n')
    }
}
