package com.flambeauriver.dailydriver.notes

import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Due dates on checklist items (T153).
 *
 * **A task is a checklist item with a due date** — there is no tasks table, no `NoteType.TASK`, and
 * no due-date column (§4.8, ADR-0006). §6.2 already permits exactly one source of truth for a
 * checklist item, and a task that existed both as a row and as a `- [ ]` line in a body would be two
 * copies of one fact, free to disagree. So a due date lives where the item lives: in the text.
 *
 * **This is a cross-implementation contract**, pinned by `fixtures/due-date-parsing.json` and
 * executed by this module's tests and the C# suite later. Change the fixture and both
 * implementations, never one alone.
 *
 * The file has two halves that are deliberately *not* equally binding:
 * - [splitDueDate] and [dueToken] implement the **stored** format. Every client must, because
 *   getting them wrong corrupts bodies.
 * - [resolveDueInput] implements **typing shortcuts**, which never reach storage. A client that
 *   skips it is still correct, just less pleasant to type into.
 */

/**
 * `@YYYY-MM-DD`, preceded by start-of-text or whitespace.
 *
 * The whitespace requirement is what keeps `jack@example.com` an email address rather than a
 * mangled date, and it matches how `TagParsing` recognizes `#hashtags` — one convention for inline
 * metadata in a body, not two.
 *
 * Deliberately strict about shape: `\d{4}-\d{2}-\d{2}` only, so `@2026-8-5` is text. A lenient
 * pattern would have to guess, and guessing wrong moves someone's task by a month.
 */
private val DUE_TOKEN = Regex("""(?:^|\s)@(\d{4}-\d{2}-\d{2})(?=\s|$)""")

/** `t+3`, `w+2`, `m+1`, `y+1`. Non-negative only — a due date in the past is typed, not shorthanded. */
private val OFFSET_SHORTCUT = Regex("""^([twmy])\+(\d{1,4})$""")

/** An item's text with its due date lifted out of it. */
data class DueDateSplit(val text: String, val due: LocalDate?)

/**
 * Separates a due date from the surrounding item text.
 *
 * **Takes exactly one adjacent space with the token, and never trims.** That restraint is
 * load-bearing and the reason this isn't a one-line `replace`: item text is edited *through* the
 * body on every keystroke, so a general trim here would strip the space you just typed and make
 * "peanut butter" impossible to enter — the exact bug T023 shipped and had to fix. Removing only
 * the space that joined the token to the text closes the gap ("call @2026-08-25 about" becomes
 * "call about") while leaving a trailing space you are mid-typing alone.
 *
 * **The last valid date wins**, and any earlier one stays as literal text. First-wins is the more
 * obvious rule and it is wrong here: [renderChecklist] writes the due token at the end of the line,
 * so on a line carrying two dates a first-wins parse picks one date and then renders the *other*
 * one last, and the two swap places on every parse/render cycle. Since the body is the live editing
 * buffer, that is a line that visibly rewrites itself while you type — the same class of bug T023
 * shipped with trailing spaces. Last-wins makes render and parse an identity instead.
 *
 * It is also the better reading. Appending a date to a line that already has one is how you change
 * your mind about when something is due, so the one you just typed should win.
 *
 * A date that matches the shape but is not a real calendar day (30 February) is left as text.
 * Leniency would silently reschedule the task.
 */
fun splitDueDate(text: String): DueDateSplit {
    var result: DueDateSplit? = null

    // Last valid match wins, so the loop keeps overwriting rather than breaking out. See the note
    // above on why this is not first-wins.
    for (match in DUE_TOKEN.findAll(text)) {
        val parsed = match.groupValues[1].toLocalDateOrNull() ?: continue

        // The match already includes the space in front of the token when there is one, so cutting
        // the whole range takes that space with it and the words either side don't weld together.
        val start = match.range.first
        val cut = text.removeRange(start, match.range.last + 1)

        // A token at the very start of the text had no space in front to absorb, so it leaves the
        // one that followed it. Take that instead, and only that.
        val tidied = if (!text[start].isWhitespace() && cut.startsWith(" ")) cut.drop(1) else cut

        result = DueDateSplit(text = tidied, due = parsed)
    }

    return result ?: DueDateSplit(text = text, due = null)
}

/** Renders a due date back to its stored token. Inverse of the extraction in [splitDueDate]. */
fun dueToken(due: LocalDate): String = "@$due"

/**
 * Resolves what someone typed into a real date (T153).
 *
 * **Shortcuts are input sugar and never a storage format.** The result of this function is written
 * to the body as a literal ISO date, so a note means the same thing next week as it does today, and
 * the C# side only ever has to parse [DUE_TOKEN]. That separation is why the shortcut vocabulary can
 * grow later without touching the wire format or the desktop client.
 *
 * Accepts an ISO date unchanged, the words `today` / `tomorrow` / `next week|month|year`, and the
 * offset forms `t+N` days, `w+N` weeks, `m+N` months, `y+N` years. Anything else returns null and is
 * left as literal text by the caller — silently discarding what someone typed is worse than leaving
 * it visible and wrong.
 *
 * Spaced forms each have a hyphenated equivalent, because `@next week` is two tokens and only
 * unambiguous at the end of a line (recorded as a known limit in the corpus).
 */
fun resolveDueInput(input: String, today: LocalDate): LocalDate? {
    val raw = input.trim()

    // An ISO date is checked *first*, before any normalization. The hyphen folding below is what
    // lets `next-week` and `next week` agree, and it would happily turn `2026-12-25` into
    // `2026 12 25` and drop it on the floor.
    raw.toLocalDateOrNull()?.let { return it }

    val normalized = raw.lowercase().replace('-', ' ').replace(Regex("""\s+"""), " ")

    return when (normalized) {
        "today" -> today
        "tomorrow" -> today.plusDays(1)
        "next week" -> today.plusWeeks(1)
        "next month" -> today.plusMonths(1)
        "next year" -> today.plusYears(1)
        else -> normalized.asOffset(today)
    }
}

/**
 * `t+3` and friends.
 *
 * Month and year arithmetic clamps to the end of the target month rather than overflowing, which is
 * `java.time`'s own behaviour and the right one: 31 January plus a month is February, and rolling
 * into 3 March would move the task past the month the user asked for. The cost is that the
 * operation isn't reversible, which the corpus records as a known limit rather than leaving to be
 * discovered.
 */
private fun String.asOffset(today: LocalDate): LocalDate? {
    // The hyphen normalization above turns "t-3" into "t 3", so a negative offset can no longer
    // reach OFFSET_SHORTCUT and correctly fails to resolve.
    val match = OFFSET_SHORTCUT.matchEntire(this) ?: return null
    val count = match.groupValues[2].toLongOrNull() ?: return null

    return when (match.groupValues[1]) {
        "t" -> today.plusDays(count)
        "w" -> today.plusWeeks(count)
        "m" -> today.plusMonths(count)
        "y" -> today.plusYears(count)
        else -> null
    }
}

/**
 * Rewrites any shortcut someone typed into the item's text as a stored ISO token (T154).
 *
 * **Called on commit, never on keystroke**, and that timing is the whole design. The row is already
 * a text field, so typing `@tomorrow` needs no date-picker UI at all — but rewriting text while a
 * cursor is in it is what T023 got wrong, and the field here is a `BasicTextField(String)` whose
 * cursor snaps to the end on any external change. Resolving when the row loses focus means the text
 * only ever changes while nobody is typing into it.
 *
 * Two-word shortcuts (`@next week`) are only resolved at the very end of the text, which is the
 * limit the corpus records: mid-line there is no way to tell the second word of a shortcut from the
 * next word of the sentence. Every spaced form has a hyphenated equivalent that works anywhere.
 *
 * Anything that doesn't resolve is left exactly as typed, so `@bob` and an email address survive.
 */
fun resolveDueInText(text: String, today: LocalDate): String {
    // The two-word tail first: "@next week" only means anything here, and trying it before the
    // single-word pass stops "@next" being resolved on its own and orphaning "week".
    TWO_WORD_TAIL.find(text)?.let { match ->
        resolveDueInput(match.groupValues[1], today)?.let { resolved ->
            // The match swallowed the space in front of the shortcut, so put one back rather than
            // welding the token onto the last word ("move house@2026-08-28").
            val head = text.removeRange(match.range)
            return if (head.isEmpty()) dueToken(resolved) else "$head ${dueToken(resolved)}"
        }
    }

    return SINGLE_TOKEN.replace(text) { match ->
        val resolved = resolveDueInput(match.groupValues[1], today)
        if (resolved == null) match.value else match.value.replace("@" + match.groupValues[1], dueToken(resolved))
    }
}

/** `@next week` and friends, anchored to the end of the text. */
private val TWO_WORD_TAIL = Regex("""(?:^|\s)@(\S+[ \t]+\S+)$""")

/** A single-word `@token` at a word boundary. */
private val SINGLE_TOKEN = Regex("""(?:^|\s)@(\S+)""")

/** Strict ISO parse. Returns null rather than throwing, since bad input here is ordinary text. */
private fun String.toLocalDateOrNull(): LocalDate? = try {
    LocalDate.parse(this)
} catch (_: DateTimeParseException) {
    null
}
