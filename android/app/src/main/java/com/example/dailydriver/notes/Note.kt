package com.example.dailydriver.notes

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Whether a note renders as prose or as a checkbox list — design doc §6.2's `type` field. */
enum class NoteType { NOTE, CHECKLIST }

/**
 * One note or checklist, mirroring the Cosmos document model in design doc §6.2 so the local
 * store and the wire format don't drift.
 *
 * **[body] is the single source of truth.** It's Markdown (root CLAUDE.md hard constraint), and
 * checklist items are `- [ ]` / `- [x]` lines *derived* from it rather than stored as rows —
 * §6.2 is explicit that there's one source of truth, not a body plus a parallel item table that
 * can disagree with it. [tags] likewise live in `note_tags`, but are *derived* from hashtags in
 * the body on save (T019); the body still wins.
 *
 * **No `userId` column**, unlike the document model. It's the Cosmos partition key and is
 * constant for a single user, so locally it would be the same value on every row — the sync
 * layer attaches it at push time instead.
 *
 * **[rev] is null until the server has seen this note.** Design doc §7.1: the server assigns
 * `rev`, clients never invent one. Null therefore means "never synced", which is what lets the
 * outbox tell a create from an update without storing an operation type.
 */
@Entity(
    tableName = "notes",
    indices = [
        // The note list sorts by recency (T025), and every query filters tombstones out.
        Index(value = ["updatedAt"]),
        Index(value = ["deleted"]),
    ],
)
data class Note(
    @PrimaryKey val id: String,
    val type: NoteType,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
    /**
     * Soft delete only (§7.1) — the row stays as a tombstone so the deletion can propagate to
     * the other replica. Server purges tombstones past 90 days (T049).
     */
    val deleted: Boolean = false,
    val rev: Long? = null,
    val sourceDevice: String,
)
