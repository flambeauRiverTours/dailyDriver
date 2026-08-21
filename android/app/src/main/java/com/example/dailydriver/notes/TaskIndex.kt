package com.example.dailydriver.notes

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * One dated checklist item, indexed for querying (T157).
 *
 * **An index, not a store** (ADR-0007). A task is authored in the note body and nowhere else;
 * these rows exist so "what is due today" can be an indexed lookup instead of a scan of every
 * body. Nothing may insert one directly — [NoteDao] derives them inside the same transaction as
 * the note write, exactly as it already does for [NoteTag], and a wrong index is repaired by
 * rebuilding it rather than by patching a row.
 *
 * The precedent matters: hashtags already work this way, parsed out of the body on save and
 * indexed for exact matching while the body stays authoritative. This is that pattern with a date
 * instead of a tag, which is most of the reason to be comfortable with it.
 *
 * [position] is the item's index in the note's *full* checklist, not among the dated ones. That's
 * what lets a tap in `:tasks` find the right line to tick in a body that also holds undated items.
 *
 * [due] is the stored ISO date as text. Text rather than an epoch number because `YYYY-MM-DD`
 * already sorts and range-compares correctly as a string, and staying in the format the body uses
 * means there is no second representation of a date to get wrong.
 *
 * `CASCADE` fires only on a *hard* delete. A soft-deleted note keeps its rows, the same as tags —
 * queries exclude them by joining on `notes.deleted`, so undoing a delete needs no rebuild.
 */
@Entity(
    tableName = "tasks",
    primaryKeys = ["noteId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = Note::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    // The entire point of the table.
    indices = [Index(value = ["due"])],
)
data class TaskRow(
    val noteId: String,
    val position: Int,
    val text: String,
    val checked: Boolean,
    val due: String,
)

/**
 * A task as `:tasks` shows it — the row plus the title of the note it came from.
 *
 * The title is carried because a task list that can't say *where* a task lives is hard to act on,
 * and because tapping the text opens that note.
 */
data class TaskListItem(
    val noteId: String,
    val noteTitle: String,
    val position: Int,
    val text: String,
    val checked: Boolean,
    val due: String,
)

/**
 * Derives a note's task rows from its body (T157).
 *
 * **Only checklists produce tasks.** A prose note is parsed by [parseChecklist] as one item per
 * line — that's what makes note-to-checklist conversion a parse rather than a special case — so
 * deriving from every note would turn any prose line mentioning a date into a task the user never
 * created. "A task is a dated checklist item" (ADR-0007) is taken literally here.
 *
 * **Only dated items produce tasks.** An undated checklist item is an ordinary checklist item, and
 * `:tasks` should not show it.
 *
 * Soft-deleted notes still derive rows. They're excluded at query time instead, so undoing a
 * delete restores the tasks without a rebuild.
 */
fun tasksOf(note: Note): List<TaskRow> {
    if (note.type != NoteType.CHECKLIST) return emptyList()

    return parseChecklist(note.body).mapIndexedNotNull { position, item ->
        item.due?.let { due ->
            TaskRow(
                noteId = note.id,
                position = position,
                text = item.text.trim(),
                checked = item.checked,
                due = due.toString(),
            )
        }
    }
}
