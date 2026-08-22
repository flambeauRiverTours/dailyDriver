package com.flambeauriver.dailydriver.editor

import com.flambeauriver.dailydriver.notes.Note
import com.flambeauriver.dailydriver.notes.NoteType
import com.flambeauriver.dailydriver.shell.statusLineText
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * The editor's in-memory working copy of a note (T020/T021).
 *
 * **Deliberately not a [Note].** A draft can exist before anything has been written, and §4.6's
 * "autosave is the real save" means the decision of *whether* this is worth a database row is
 * made here rather than by whoever opened the editor. Nothing is persisted until [hasContent] is
 * true, so an accidental `:note` leaves no trace and T025's list never fills with blanks.
 *
 * [persisted] tracks whether a row exists yet, which is what tells discard the difference between
 * "forget this" and "soft-delete a real note".
 */
data class NoteDraft(
    val id: String,
    val title: String = "",
    val body: String = "",
    val createdAt: Long,
    val rev: Long? = null,
    val persisted: Boolean = false,
    /**
     * Prose or checkbox list (T023/T024). Decides which surface the editor renders, and is the
     * only thing note↔checklist conversion changes besides reformatting [body].
     *
     * **[body] stays the single source of truth either way** — a checklist's items are parsed out
     * of it on demand ([com.flambeauriver.dailydriver.notes.parseChecklist]) rather than held beside it,
     * so this field selects a *view*, never a second copy of the data.
     */
    val type: NoteType = NoteType.NOTE,
)

/** Whether there's anything worth keeping. An untouched draft is discarded silently on close. */
fun NoteDraft.hasContent(): Boolean = title.isNotBlank() || body.isNotBlank()

/**
 * The title actually written to the database: what the user typed, or a `Wed 19 Aug - 14:32`
 * stamp when they left it blank (design check-in, 2026-08-19).
 *
 * **Derived from [NoteDraft.createdAt], never the current time.** A journal entry begun at 23:58
 * and finished at 00:05 should carry the date you started writing, and a stable answer also means
 * the title doesn't quietly change every time an autosave fires with a blank title field.
 *
 * Formatting goes through [statusLineText] rather than a second `DateTimeFormatter` so a note
 * title and the status line can never disagree about how this app writes a date — and so this
 * inherits that function's existing coverage of zones, locales and 12/24-hour handling.
 */
fun NoteDraft.resolvedTitle(
    zone: ZoneId,
    is24Hour: Boolean,
    locale: Locale = Locale.getDefault(),
): String = title.ifBlank { defaultNoteTitle(createdAt, zone, is24Hour, locale) }

/** `Wed 19 Aug - 14:32` — see [resolvedTitle] for why this is the fallback. */
fun defaultNoteTitle(
    createdAt: Long,
    zone: ZoneId,
    is24Hour: Boolean,
    locale: Locale = Locale.getDefault(),
): String {
    // colonVisible = true: the blink is a status-line affordance, meaningless in a stored title.
    val stamp = statusLineText(Instant.ofEpochMilli(createdAt), zone, is24Hour, true, locale)
    return "${stamp.date} - ${stamp.time}"
}

/**
 * Converts a draft into the row to store. Tags aren't passed — `NoteDao.upsertLocal` derives them
 * from the body (T019), which is what keeps the body the single source of truth.
 */
fun NoteDraft.toNote(
    sourceDevice: String,
    updatedAt: Long,
    zone: ZoneId,
    is24Hour: Boolean,
    locale: Locale = Locale.getDefault(),
): Note = Note(
    id = id,
    type = type,
    title = resolvedTitle(zone, is24Hour, locale),
    body = body,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deleted = false,
    rev = rev,
    sourceDevice = sourceDevice,
)
