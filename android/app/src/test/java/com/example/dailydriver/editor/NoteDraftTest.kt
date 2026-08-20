package com.example.dailydriver.editor

import com.example.dailydriver.notes.NoteType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class NoteDraftTest {

    /** 2026-08-19 14:32 UTC — a Wednesday. */
    private val createdAt =
        LocalDateTime.of(2026, 8, 19, 14, 32).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun draft(title: String = "", body: String = "") =
        NoteDraft(id = "n1", title = title, body = body, createdAt = createdAt)

    private fun NoteDraft.title24h() = resolvedTitle(ZoneOffset.UTC, is24Hour = true, Locale.US)

    // --- what counts as worth keeping ------------------------------------------------------------

    @Test
    fun `an untouched draft has no content`() {
        // The accidental-`:note` case: opened, nothing typed, closed. Must leave no row behind,
        // or T025's list fills with blanks.
        assertFalse(draft().hasContent())
    }

    @Test
    fun `whitespace alone is not content`() {
        assertFalse(draft(body = "   \n\t").hasContent())
        assertFalse(draft(title = "  ").hasContent())
    }

    @Test
    fun `either field alone counts as content`() {
        // A title-only note is legitimate (a placeholder to fill in later), and so is the far
        // more common body-only quick note.
        assertTrue(draft(body = "milk, eggs").hasContent())
        assertTrue(draft(title = "Standup").hasContent())
    }

    // --- the blank-title fallback -----------------------------------------------------------------

    @Test
    fun `a blank title falls back to a date-time stamp`() {
        assertEquals("Wed 19 Aug - 14:32", draft(body = "something").title24h())
    }

    @Test
    fun `a whitespace-only title also falls back`() {
        assertEquals("Wed 19 Aug - 14:32", draft(title = "   ", body = "x").title24h())
    }

    @Test
    fun `a real title is kept verbatim, including its spacing`() {
        assertEquals("Morning pages", draft(title = "Morning pages").title24h())
        assertEquals("  padded  ", draft(title = "  padded  ").title24h())
    }

    @Test
    fun `the stamp follows the 12-hour setting like the status line does`() {
        assertEquals(
            "Wed 19 Aug - 2:32 PM",
            draft(body = "x").resolvedTitle(ZoneOffset.UTC, is24Hour = false, Locale.US),
        )
    }

    @Test
    fun `the stamp is rendered in the requested zone`() {
        // 14:32 UTC is 10:32 in New York, and the date could roll for other times — the stamp has
        // to be local, not UTC.
        assertEquals(
            "Wed 19 Aug - 10:32",
            draft(body = "x").resolvedTitle(ZoneId.of("America/New_York"), is24Hour = true, Locale.US),
        )
    }

    @Test
    fun `the stamp comes from createdAt, so it does not drift as the note is edited`() {
        // Every autosave with a blank title re-resolves this. If it used "now", a journal entry
        // would silently retitle itself on every keystroke.
        val later = draft(body = "first").copy(body = "much later, after more writing")

        assertEquals(draft(body = "first").title24h(), later.title24h())
    }

    @Test
    fun `an entry written past midnight keeps the date it was started on`() {
        val startedBeforeMidnight = LocalDateTime.of(2026, 8, 19, 23, 58)
            .toInstant(ZoneOffset.UTC).toEpochMilli()
        val draft = NoteDraft(id = "n1", body = "journal", createdAt = startedBeforeMidnight)

        assertEquals("Wed 19 Aug - 23:58", draft.resolvedTitle(ZoneOffset.UTC, true, Locale.US))
    }

    @Test
    fun `the standalone stamp helper agrees with the resolved title`() {
        assertEquals(
            defaultNoteTitle(createdAt, ZoneOffset.UTC, is24Hour = true, Locale.US),
            draft(body = "x").title24h(),
        )
    }

    // --- conversion to a storable row ----------------------------------------------------------------

    @Test
    fun `converting carries the identity and content across`() {
        val note = draft(title = "Standup", body = "#work notes")
            .toNote(sourceDevice = "test-device", updatedAt = 9_000L, zone = ZoneOffset.UTC, is24Hour = true, locale = Locale.US)

        assertEquals("n1", note.id)
        assertEquals("Standup", note.title)
        assertEquals("#work notes", note.body)
        assertEquals(createdAt, note.createdAt)
        assertEquals(9_000L, note.updatedAt)
        assertEquals("test-device", note.sourceDevice)
    }

    @Test
    fun `converting applies the blank-title fallback`() {
        val note = draft(body = "x")
            .toNote("d", updatedAt = 1L, zone = ZoneOffset.UTC, is24Hour = true, locale = Locale.US)

        assertEquals("Wed 19 Aug - 14:32", note.title)
    }

    @Test
    fun `a converted note is never born deleted, and carries its rev`() {
        val note = draft(body = "x").copy(rev = 7L)
            .toNote("d", updatedAt = 1L, zone = ZoneOffset.UTC, is24Hour = true, locale = Locale.US)

        assertFalse(note.deleted)
        assertEquals(7L, note.rev)
        assertEquals(NoteType.NOTE, note.type)
    }

    @Test
    fun `a never-synced draft converts with a null rev`() {
        // null means "the server has never seen this", which is what lets the outbox tell a
        // create from an update without storing an operation type (T018).
        val note = draft(body = "x")
            .toNote("d", updatedAt = 1L, zone = ZoneOffset.UTC, is24Hour = true, locale = Locale.US)

        assertEquals(null, note.rev)
    }

    @Test
    fun `converting is pure — repeated calls agree`() {
        val subject = draft(body = "x")
        val once = subject.toNote("d", 1L, ZoneOffset.UTC, true, Locale.US)
        val twice = subject.toNote("d", 1L, ZoneOffset.UTC, true, Locale.US)

        assertEquals(once, twice)
    }
}
