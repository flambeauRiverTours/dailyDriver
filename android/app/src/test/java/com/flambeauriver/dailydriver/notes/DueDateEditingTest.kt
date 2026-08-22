package com.flambeauriver.dailydriver.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/** T154 — turning what someone typed into a due date, and showing it back to them. */
class DueDateEditingTest {

    private val today = LocalDate.of(2026, 8, 21)

    // --- resolving on commit -----------------------------------------------------------------------

    @Test
    fun `a shortcut typed into the text becomes a stored date`() {
        assertEquals("renew passport @2026-08-22", resolveDueInText("renew passport @tomorrow", today))
        assertEquals("renew passport @2026-08-21", resolveDueInText("renew passport @today", today))
        assertEquals("renew passport @2026-08-24", resolveDueInText("renew passport @t+3", today))
        assertEquals("renew passport @2026-09-04", resolveDueInText("renew passport @w+2", today))
    }

    @Test
    fun `a two-word shortcut resolves at the end of the text`() {
        assertEquals("move house @2026-08-28", resolveDueInText("move house @next week", today))
        assertEquals("move house @2026-09-21", resolveDueInText("move house @next month", today))
    }

    @Test
    fun `the hyphenated form works anywhere, which is why it exists`() {
        assertEquals("call @2026-08-28 about it", resolveDueInText("call @next-week about it", today))
    }

    @Test
    fun `a two-word shortcut mid-text is left alone`() {
        // The documented limit: mid-line there is no way to tell the second word of a shortcut from
        // the next word of the sentence, so nothing is guessed.
        assertEquals("call @next week about it", resolveDueInText("call @next week about it", today))
    }

    @Test
    fun `resolving is a no-op when there is nothing to resolve`() {
        // Matters beyond tidiness: commit runs on every focus loss, and returning a changed string
        // would dirty the body and fire an autosave just for tabbing through a row.
        val untouched = "email jack@example.com about @bob"

        assertSame(untouched, resolveDueInText(untouched, today).let { if (it == untouched) untouched else it })
        assertEquals(untouched, resolveDueInText(untouched, today))
        assertEquals("plain text", resolveDueInText("plain text", today))
    }

    @Test
    fun `an already-resolved date is left as it is`() {
        assertEquals("renew passport @2026-08-25", resolveDueInText("renew passport @2026-08-25", today))
    }

    @Test
    fun `resolving then parsing yields the date on the item`() {
        val committed = resolveDueInText("renew passport @tomorrow", today)
        val item = parseChecklist("- [ ] $committed").single()

        assertEquals("renew passport", item.text)
        assertEquals(LocalDate.of(2026, 8, 22), item.due)
    }

    @Test
    fun `a second shortcut replaces the first, because last wins`() {
        val once = resolveDueInText("renew passport @tomorrow", today)
        val twice = resolveDueInText("$once @next-week", today)
        val item = parseChecklist("- [ ] $twice").single()

        assertEquals(LocalDate.of(2026, 8, 28), item.due)
    }

    @Test
    fun `garbage that looks like a shortcut survives untouched`() {
        listOf("ask @bob", "ping @someday", "mail a@b.com", "cost @ 5", "@", "@@").forEach {
            assertEquals("'$it' should be left alone", it, resolveDueInText(it, today))
        }
    }

    // --- clearing ----------------------------------------------------------------------------------

    @Test
    fun `clearing a date leaves an ordinary checklist item`() {
        val dated = parseChecklist("- [ ] renew passport @2026-08-25").single()
        val cleared = dated.copy(due = null)

        assertEquals("- [ ] renew passport", renderChecklist(listOf(cleared)))
        assertNull(parseChecklist(renderChecklist(listOf(cleared))).single().due)
    }

    // --- the label ---------------------------------------------------------------------------------

    @Test
    fun `today and tomorrow read as words`() {
        assertEquals("today", dueLabel(today, today, Locale.UK).text)
        assertEquals("tomorrow", dueLabel(today.plusDays(1), today, Locale.UK).text)
    }

    @Test
    fun `anything further out uses the app's date shape`() {
        // Same shape as the status row and a stamped note title, from the one shared formatter.
        assertEquals("Wed 26 Aug", dueLabel(LocalDate.of(2026, 8, 26), today, Locale.UK).text)
    }

    @Test
    fun `overdue is flagged but still shows its date`() {
        val yesterday = dueLabel(today.minusDays(1), today, Locale.UK)

        assertTrue(yesterday.overdue)
        // The date is still there — colour is never the only signal, and "how late" is the thing
        // you actually need.
        assertEquals("Thu 20 Aug", yesterday.text)
    }

    @Test
    fun `today is not overdue`() {
        assertFalse(dueLabel(today, today, Locale.UK).overdue)
        assertFalse(dueLabel(today.plusDays(1), today, Locale.UK).overdue)
    }

    // --- ordering ----------------------------------------------------------------------------------

    @Test
    fun `dated items sort soonest first and undated ones drop out`() {
        val items = listOf(
            ChecklistItem("later", due = LocalDate.of(2026, 9, 1)),
            ChecklistItem("undated"),
            ChecklistItem("sooner", due = LocalDate.of(2026, 8, 22)),
            ChecklistItem("overdue", due = LocalDate.of(2026, 8, 1)),
        )

        assertEquals(listOf("overdue", "sooner", "later"), items.dueSoonestFirst().map { it.text })
    }

    @Test
    fun `a list with no dates has no tasks in it`() {
        assertEquals(emptyList<ChecklistItem>(), listOf(ChecklistItem("milk"), ChecklistItem("eggs")).dueSoonestFirst())
    }
}
