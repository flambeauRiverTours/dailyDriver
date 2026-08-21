package com.example.dailydriver.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** T157 — deriving the task index from a note body. Pure; the DAO side is instrumented. */
class TaskIndexTest {

    private fun note(body: String, type: NoteType = NoteType.CHECKLIST) = Note(
        id = "n1",
        type = type,
        title = "tasks",
        body = body,
        createdAt = 0,
        updatedAt = 0,
        sourceDevice = "test",
    )

    @Test
    fun `a dated checklist item becomes a task`() {
        val rows = tasksOf(note("- [ ] renew passport @2026-08-25"))

        assertEquals(1, rows.size)
        assertEquals("renew passport", rows.single().text)
        assertEquals("2026-08-25", rows.single().due)
        assertEquals(0, rows.single().position)
    }

    @Test
    fun `undated items are not tasks`() {
        assertEquals(emptyList<TaskRow>(), tasksOf(note("- [ ] milk\n- [x] eggs")))
    }

    @Test
    fun `position indexes the full checklist, not just the dated rows`() {
        // The whole point: a tap in `:tasks` has to find the right line in a body that also holds
        // undated items. Indexing among dated items only would tick the wrong row.
        val rows = tasksOf(note("- [ ] milk\n- [ ] renew passport @2026-08-25\n- [ ] eggs"))

        assertEquals(1, rows.single().position)
    }

    @Test
    fun `a prose note produces no tasks even when it mentions a date`() {
        // parseChecklist turns every prose line into an item, which is what makes conversion a
        // parse. Deriving from prose would invent tasks the user never created.
        assertEquals(emptyList<TaskRow>(), tasksOf(note("meeting @2026-08-25", type = NoteType.NOTE)))
    }

    @Test
    fun `checked state carries across`() {
        val rows = tasksOf(note("- [x] renew passport @2026-08-25"))

        assertTrue(rows.single().checked)
    }

    @Test
    fun `several dated items keep their own positions`() {
        val rows = tasksOf(
            note("- [ ] a @2026-08-25\n- [ ] undated\n- [x] b @2026-08-01\n- [ ] c @2026-09-01"),
        )

        assertEquals(listOf(0, 2, 3), rows.map { it.position })
        assertEquals(listOf("2026-08-25", "2026-08-01", "2026-09-01"), rows.map { it.due })
    }

    @Test
    fun `derivation is a pure function of the body`() {
        // The property the whole index rests on: rebuilding always lands in the same place, so a
        // wrong index is repairable rather than something to patch.
        val body = "- [ ] a @2026-08-25\n- [x] b @2026-08-01"

        assertEquals(tasksOf(note(body)), tasksOf(note(body)))
    }

    @Test
    fun `item text is trimmed for the index`() {
        // The index is display and query data, not the editing buffer, so the trailing space
        // parsing deliberately preserves while typing has no business here.
        assertEquals("milk", tasksOf(note("- [ ] milk  @2026-08-25")).single().text)
    }

    @Test
    fun `an empty body has no tasks`() {
        assertEquals(emptyList<TaskRow>(), tasksOf(note("")))
    }

    @Test
    fun `the stored due is the ISO text, matching the body`() {
        // No second representation of a date to get wrong.
        val due = LocalDate.of(2026, 12, 25)
        val rows = tasksOf(note(renderChecklist(listOf(ChecklistItem("wrap presents", due = due)))))

        assertEquals(due.toString(), rows.single().due)
    }
}
