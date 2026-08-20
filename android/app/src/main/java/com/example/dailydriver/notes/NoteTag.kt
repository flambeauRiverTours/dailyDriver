package com.example.dailydriver.notes

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * A tag on a note, one row per (note, tag) pair.
 *
 * **A join table rather than a delimited column on [Note]** (design check-in, 2026-08-19). The
 * document model in §6.2 carries `tags` as an array, and the obvious local mirror is a single
 * delimited column — but filtering it then means `LIKE '%work%'`, which also matches `#workout`.
 * That's a real wrong-results bug for T026's search, not a tidiness argument. An indexed join
 * table gives exact matching for free.
 *
 * Derived from hashtags in the note's Markdown body on save (T019). The body stays the source of
 * truth; these rows are a query index over it, rewritten wholesale whenever the body changes.
 *
 * `CASCADE` fires only on a *hard* delete of the note row. Soft-deleting a note leaves its
 * tombstone and therefore its tags in place, which is correct — the tombstone is still a note.
 */
@Entity(
    tableName = "note_tags",
    primaryKeys = ["noteId", "tag"],
    foreignKeys = [
        ForeignKey(
            entity = Note::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    // The point of the table: "which notes carry this tag" has to be an indexed lookup.
    indices = [Index(value = ["tag"])],
)
data class NoteTag(
    val noteId: String,
    val tag: String,
)
