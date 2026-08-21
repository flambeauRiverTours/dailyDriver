package com.example.dailydriver.notes

import com.example.dailydriver.shell.shortDate
import java.time.LocalDate
import java.util.Locale

/** How a due date reads on a checklist row, and whether it needs to shout (T154). */
data class DueLabel(val text: String, val overdue: Boolean)

/**
 * Renders a due date for display on a row.
 *
 * **Relative words for the two days that matter**, and the app's ordinary date shape for everything
 * else. "What is due today" is the question this whole feature exists to answer (ADR-0007), and
 * scanning a column of `Fri 21 Aug` for the one matching today's date is exactly the work the label
 * should be doing for you. `today` and `tomorrow` are also shorter than the date they replace, on a
 * row that has little width to spare.
 *
 * Anything further out goes through [shortDate], so a due date, the status row and a stamped note
 * title all write a date the same way.
 *
 * **Overdue is a separate flag rather than a word.** The row colours it instead of spending
 * characters on "overdue", and colour is never the only signal — an overdue task still shows its
 * date, which is the information you actually need to judge how late it is.
 */
fun dueLabel(due: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): DueLabel = DueLabel(
    text = when (due) {
        today -> "today"
        today.plusDays(1) -> "tomorrow"
        else -> shortDate(due, locale)
    },
    overdue = due.isBefore(today),
)

/** The dated items in a list, soonest first. Undated items are not tasks and are dropped. */
fun List<ChecklistItem>.dueSoonestFirst(): List<ChecklistItem> =
    filter { it.due != null }.sortedBy { it.due }
