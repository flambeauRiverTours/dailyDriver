package com.example.dailydriver.notes

import java.time.LocalDate

/**
 * One line of a checklist, as displayed and edited (T023).
 *
 * Not a stored entity — these are *derived* from [Note.body] on demand and rendered straight back
 * to it. §6.2 allows exactly one source of truth, and root `CLAUDE.md` pins it: "Checklists are
 * `- [ ]` / `- [x]` lines parsed into structured items." A parallel item table that could disagree
 * with the body is the thing that model exists to prevent.
 *
 * [due] is what makes an item a *task* (T153, §4.8, ADR-0006). It is derived from an `@YYYY-MM-DD`
 * token in the same line, not stored anywhere else, for exactly the reason above — a due date in a
 * column beside a body that also states it would be two copies of one fact. An item with a null
 * [due] is an ordinary checklist item and never appears in `:tasks`.
 */
data class ChecklistItem(
    val text: String,
    val checked: Boolean = false,
    val due: LocalDate? = null,
)

/**
 * Reads a Markdown body as checklist items (T023).
 *
 * **This is a cross-implementation contract**, the same seam `TagParsing` warns about. §7.3 names
 * merge rules, `rev` comparison and tag parsing as the shared rules Android reimplements in
 * Kotlin; checklists aren't on that list, but root `CLAUDE.md` calls `- [ ]`/`- [x]` "the wire and
 * storage format for both Android (Room) and desktop (EF Core/SQLite)", and §4.6 has both UIs
 * rendering "two views of one Markdown body" — which is the same seam by a different name. The
 * rules below are pinned by `fixtures/checklist-parsing.json`, executed by this module's tests and
 * by the C# suite later. Change the fixture and both implementations, never one alone.
 *
 * Three rules, in order:
 * 1. A line carrying a task marker is an item, **even with empty text** — `- [ ]` on its own is
 *    the half-typed item you're about to fill in, and dropping it would make new rows vanish as
 *    you created them. It also keeps the body genuinely authoritative: the editor can derive its
 *    rows from the body on every recomposition rather than keeping a second list beside it.
 * 2. Any other non-blank line is an unchecked item carrying that line's text. This is what makes
 *    note → checklist conversion (T024) a parse rather than a special case, and it means a body
 *    synced from a desktop that wrote plain prose still opens as something you can tick off.
 * 3. Blank lines are dropped. They have no checkbox to render and no text to keep.
 */
fun parseChecklist(body: String): List<ChecklistItem> =
    body.lineSequence()
        .mapNotNull { line ->
            val marker = TASK_MARKER.matchEntire(line)
            when {
                marker != null -> {
                    // **Not trimmed**, and that is load-bearing. The body is the live editing
                    // buffer, so every keystroke round-trips through render → parse; trimming here
                    // strips the space you just typed before you can type the word after it, and
                    // "peanut butter" comes out "peanutbutter". Leading whitespace is already
                    // consumed by the marker pattern, and trailing whitespace is tidied on close
                    // ([cleaned]) — where it costs nothing, because you've stopped typing.
                    //
                    // [splitDueDate] is held to the same rule: it lifts out the `@YYYY-MM-DD` token
                    // and exactly one space beside it, never trimming the rest (T153).
                    val split = splitDueDate(marker.groupValues[2])
                    ChecklistItem(
                        text = split.text,
                        checked = !marker.groupValues[1].equals(" ", ignoreCase = true),
                        due = split.due,
                    )
                }

                line.isBlank() -> null
                else -> {
                    // Prose lines are trimmed (this is conversion, not live editing) but still get
                    // a date lifted out, so note → checklist promotes a dated line to a task.
                    val split = splitDueDate(line.trim())
                    ChecklistItem(text = split.text, checked = false, due = split.due)
                }
            }
        }
        .toList()

/**
 * `- [ ] text`, allowing the bullet variants and the capital `X` that Markdown editors emit.
 *
 * Leading indentation is tolerated and then discarded — nested checklists aren't modelled (§4.6
 * describes a flat list), so preserving the indent would render items that look nested but
 * behave flat, which is worse than flattening them honestly.
 */
private val TASK_MARKER = Regex("""^\s*[-*+]\s+\[([ xX])]\s*(.*)$""")

/** Renders items back to Markdown. Inverse of [parseChecklist] for anything it produced. */
fun renderChecklist(items: List<ChecklistItem>): String =
    items.joinToString("\n") { item ->
        val box = if (item.checked) "x" else " "
        // Text and date are joined only when both are present, so an empty dated item renders as
        // `- [ ] @2026-08-25` rather than carrying a stray leading space, and an empty undated one
        // stays `- [ ]`. No trailing space on an empty item either — it would be invisible
        // whitespace in the stored Markdown, and `- [ ]` parses back to the same item anyway.
        val content = listOfNotNull(
            item.text.ifEmpty { null },
            item.due?.let(::dueToken),
        ).joinToString(" ")

        if (content.isEmpty()) "- [$box]" else "- [$box] $content"
    }

/**
 * Rewrites a prose body as a checklist body (T024). Every non-blank line becomes an unchecked
 * item; lines that already carry markers keep their state, so converting an already-checklist body
 * is a no-op rather than double-marking it (`- [ ] - [ ] milk`).
 */
fun toChecklistBody(body: String): String = renderChecklist(parseChecklist(body))

/**
 * Rewrites a checklist body as prose (T024) — the markers come off and the text stays.
 *
 * **Round-trips the words, not the ticks** (design check-in, 2026-08-20). `note → checklist → note`
 * is exactly lossless. `checklist → note → checklist` returns every item's text but comes back
 * unchecked, because prose has nowhere to record a checkbox. That's inherent to the destination
 * format rather than a shortcut here: the alternative considered was leaving the markers in the
 * body, which round-trips perfectly but hands you back a "note" full of the `- [ ]` litter you
 * converted away from, and contradicts §4.6's "conversion reformats the body".
 */
fun toNoteBody(body: String): String = parseChecklist(body).cleaned().joinToString("\n") { it.text }

/**
 * Unchecked items first, checked below, each group keeping its existing relative order (T023).
 *
 * §4.6: "Checking an item moves it below the unchecked ones (Keep's behaviour) rather than leaving
 * it in place." Enforced as an invariant re-applied after every mutation rather than as a one-off
 * move on check, so drag-reorder can't leave the list in a state check-and-uncheck couldn't.
 *
 * Stability is what makes the move feel small: a newly checked item lands at the *top* of the
 * checked group, immediately below where the unchecked ones end, rather than jumping to the very
 * bottom past items checked long ago.
 */
fun List<ChecklistItem>.normalized(): List<ChecklistItem> = filterNot { it.checked } + filter { it.checked }

/**
 * Moves the item at [from] so that it sits at [to], shifting the rest along — the list operation
 * behind a finished drag (T023).
 *
 * Out-of-range indices return the list untouched rather than throwing: [to] is computed from a
 * drag offset, so a fling past either end is an ordinary thing for the gesture layer to hand over,
 * not a programming error worth crashing the editor over.
 */
fun List<ChecklistItem>.moved(from: Int, to: Int): List<ChecklistItem> {
    if (from !in indices || to !in indices || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

/**
 * Tidies items for storage — applied on close (T023).
 *
 * Trims each one and drops whatever's left blank, which covers both the half-typed row you never
 * filled in and the trailing space [parseChecklist] deliberately preserves while you're typing.
 * Doing it here rather than at parse time is what lets a space be typed at all.
 */
fun List<ChecklistItem>.cleaned(): List<ChecklistItem> =
    map { it.copy(text = it.text.trim()) }.filter { it.text.isNotEmpty() || it.due != null }
