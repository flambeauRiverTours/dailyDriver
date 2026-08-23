package com.flambeauriver.dailydriver.notes

/**
 * Separating a note's tag lines from its content (T169).
 *
 * **The bug this fixes:** `withTag` appends a tag on its own line at the end of the body (T139),
 * and [parseChecklist]'s second rule makes *any* non-blank line an unchecked item. So adding a tag
 * chip to a checklist produced a checkbox reading `#shopping` — a piece of note metadata you could
 * tick off.
 *
 * **Two obvious fixes are both wrong, which is why this file exists instead.**
 *
 * Teaching [parseChecklist] to skip tag lines breaks a cross-language contract: it is pinned by
 * `fixtures/checklist-parsing.json` and owes a matching C# implementation (§7.3), and rule 2 is
 * also what makes note→checklist conversion a parse rather than a special case.
 *
 * Worse, it would *delete* the tags. The editor round-trips through
 * `render(transform(parse(body)))` on every keystroke, so a line the parser drops is a line the
 * render never writes back. The first tag you added would vanish the moment you typed.
 *
 * So the split happens outside the parser: lift the tag lines off, parse and render what's left,
 * put them back. The contract file and the C# obligation stay untouched, and nothing can be lost
 * in between.
 */
data class BodyParts(val content: String, val tagLines: List<String>) {

    /**
     * Puts [replacement] back where [content] was, with the tag lines below it.
     *
     * Tags end up at the bottom regardless of where they were, which is where [withTag] puts them
     * anyway. Preserving their original line positions would mean tracking indices through a
     * transform that inserts and deletes rows, to preserve something no one can see.
     */
    fun rejoin(replacement: String): String = when {
        tagLines.isEmpty() -> replacement
        replacement.isBlank() -> tagLines.joinToString("\n")
        else -> replacement.trimEnd('\n') + "\n" + tagLines.joinToString("\n")
    }
}

/**
 * Splits [body] into content and the lines that are nothing but tags.
 *
 * **A line qualifies only if *every* token on it is a hashtag**, which is deliberately
 * conservative. `#include <stdio.h>` has a non-tag token and stays content, so pasted code is not
 * quietly reclassified as metadata — only the `#work #home` shape a chip row produces is lifted.
 *
 * (A lone `#include` on its own line inside a fenced code block would be lifted, since this works
 * line by line and [parseTags] fence-awareness isn't reachable per-line. That body would already
 * be flattened into checklist items by [parseChecklist], so the fence has no meaning by this point
 * either way.)
 */
fun splitTagLines(body: String): BodyParts {
    val (tags, content) = body.lines().partition(::isTagLine)
    return BodyParts(content = content.joinToString("\n"), tagLines = tags)
}

/** True if the line is non-blank and made only of hashtags. */
private fun isTagLine(line: String): Boolean {
    val tokens = line.trim().split(WHITESPACE).filter { it.isNotEmpty() }
    return tokens.isNotEmpty() && tokens.all(::isTag)
}

/** Matches [parseTags]'s notion of a tag, including its at-least-one-letter rule. */
private fun isTag(token: String): Boolean {
    if (!token.startsWith("#")) return false
    val name = token.removePrefix("#")
    return name.isNotEmpty() && name.all { it.isLetterOrDigit() || it == '_' || it == '-' } &&
        name.any(Char::isLetter)
}

private val WHITESPACE = Regex("""\s+""")
