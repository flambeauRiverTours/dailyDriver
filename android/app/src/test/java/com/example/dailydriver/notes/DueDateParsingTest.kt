package com.example.dailydriver.notes

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DueDateParsingTest {

    // --- the shared fixture corpus -----------------------------------------------------------------

    // fixtures/due-date-parsing.json is the contract, third in the §7.3 pattern after tags and
    // checklists. Its two halves are NOT equally binding, and the tests below mirror that: a
    // storedCase failure means bodies are being read or written wrongly, which corrupts data; an
    // inputCase failure only means a typing shortcut misbehaves. Add cases to the fixture, not here.

    @Test
    fun `every stored case in the shared fixture corpus passes`() {
        val failures = fixture().getJSONArray("storedCases").objects().mapNotNull { case ->
            val expectedText = case.getString("text")
            val expectedDue = case.optString("due", null)?.let(LocalDate::parse)
            val actual = parseChecklist(case.getString("body")).singleOrNull()

            when {
                actual == null -> "${case.getString("name")}: expected one item, got none or several"
                actual.text != expectedText ->
                    "${case.getString("name")}: text expected '$expectedText', got '${actual.text}'"

                actual.due != expectedDue ->
                    "${case.getString("name")}: due expected $expectedDue, got ${actual.due}"

                actual.checked != case.getBoolean("checked") ->
                    "${case.getString("name")}: checked expected ${case.getBoolean("checked")}"

                else -> null
            }
        }

        assertTrue("${failures.size} fixture case(s) failed:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test
    fun `every render case in the shared fixture corpus passes`() {
        val failures = fixture().getJSONArray("renderCases").objects().mapNotNull { case ->
            val item = ChecklistItem(
                text = case.getString("text"),
                checked = case.getBoolean("checked"),
                due = case.optString("due", null)?.let(LocalDate::parse),
            )
            val actual = renderChecklist(listOf(item))
            val expected = case.getString("body")
            if (actual == expected) null else "${case.getString("name")}: expected '$expected', got '$actual'"
        }

        assertTrue("${failures.size} fixture case(s) failed:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test
    fun `every input case in the shared fixture corpus passes`() {
        val failures = fixture().getJSONArray("inputCases").objects().mapNotNull { case ->
            val today = LocalDate.parse(case.getString("today"))
            val expected = case.optString("resolved", null)?.let(LocalDate::parse)
            val actual = resolveDueInput(case.getString("input"), today)
            if (actual == expected) null else "${case.getString("name")}: expected $expected, got $actual"
        }

        assertTrue("${failures.size} fixture case(s) failed:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test
    fun `the fixture corpus is actually loaded and non-trivial`() {
        // Guards against the corpus silently falling off the test classpath, which would make every
        // loop above vacuously pass.
        assertTrue(fixture().getJSONArray("storedCases").length() >= 10)
        assertTrue(fixture().getJSONArray("inputCases").length() >= 15)
    }

    // --- the round trip ----------------------------------------------------------------------------

    @Test
    fun `a dated item survives render then parse`() {
        val items = listOf(
            ChecklistItem("renew passport", checked = false, due = LocalDate.of(2026, 8, 25)),
            ChecklistItem("milk"),
            ChecklistItem("book dentist", checked = true, due = LocalDate.of(2026, 8, 19)),
        )

        assertEquals(items, parseChecklist(renderChecklist(items)))
    }

    @Test
    fun `every stored case in the corpus round-trips through render`() {
        fixture().getJSONArray("storedCases").objects().forEach { case ->
            val parsed = parseChecklist(case.getString("body"))
            assertEquals(
                "round trip changed the items for '${case.getString("name")}'",
                parsed,
                parseChecklist(renderChecklist(parsed)),
            )
        }
    }

    // --- the T023 trap -----------------------------------------------------------------------------

    // Item text is edited *through* the body on every keystroke, which is what made trailing spaces
    // impossible to type until T023 fixed it ("peanut butter" came out "peanutbutter"). Stripping a
    // date token out of the text is the same shape of operation, so it gets the same scrutiny.

    @Test
    fun `removing the date token does not eat a trailing space`() {
        val typed = ChecklistItem("peanut ", due = LocalDate.of(2026, 8, 25))

        assertEquals(listOf(typed), parseChecklist(renderChecklist(listOf(typed))))
    }

    @Test
    fun `typing a two-word item on a dated row arrives intact`() {
        val target = "peanut butter"
        val due = LocalDate.of(2026, 8, 25)

        var body = renderChecklist(listOf(ChecklistItem("", due = due)))
        target.forEach { character ->
            val current = parseChecklist(body).single()
            body = renderChecklist(listOf(current.copy(text = current.text + character)))
        }

        val finished = parseChecklist(body).single()
        assertEquals(target, finished.text)
        assertEquals(due, finished.due)
    }

    @Test
    fun `an empty item keeps its date`() {
        val item = ChecklistItem("", due = LocalDate.of(2026, 8, 25))

        assertEquals(listOf(item), parseChecklist(renderChecklist(listOf(item))))
    }

    // --- whitespace at the seam --------------------------------------------------------------------

    @Test
    fun `the token takes exactly one adjacent space with it`() {
        // Not a general trim: only the space that joined the token to the text is removed, because
        // trimming here would reintroduce the T023 bug above.
        assertEquals("call about the invoice", parseChecklist("- [ ] call @2026-08-25 about the invoice").single().text)
        assertEquals("renew passport", parseChecklist("- [ ] renew passport @2026-08-25").single().text)
        assertEquals("renew passport", parseChecklist("- [ ] @2026-08-25 renew passport").single().text)
    }

    @Test
    fun `a date-only item leaves empty text`() {
        val item = parseChecklist("- [ ] @2026-08-25").single()

        assertEquals("", item.text)
        assertEquals(LocalDate.of(2026, 8, 25), item.due)
    }

    // --- things that are text, not dates -----------------------------------------------------------

    @Test
    fun `an email address is left alone`() {
        val item = parseChecklist("- [ ] email jack@example.com").single()

        assertEquals("email jack@example.com", item.text)
        assertNull(item.due)
    }

    @Test
    fun `an unparseable at-token is preserved exactly`() {
        val item = parseChecklist("- [ ] ask @bob about it").single()

        assertEquals("ask @bob about it", item.text)
        assertNull(item.due)
    }

    @Test
    fun `an impossible calendar date is text`() {
        // 30 February parses as a well-formed pattern but is not a real date. Leniency here would
        // silently move the task to 2 March.
        val item = parseChecklist("- [ ] review @2026-02-30").single()

        assertEquals("review @2026-02-30", item.text)
        assertNull(item.due)
    }

    @Test
    fun `a stored shortcut word is text, because shortcuts are never persisted`() {
        val item = parseChecklist("- [ ] milk @tomorrow").single()

        assertEquals("milk @tomorrow", item.text)
        assertNull(item.due)
    }

    // --- shortcut resolution -----------------------------------------------------------------------

    @Test
    fun `month arithmetic clamps rather than overflowing`() {
        val endOfJanuary = LocalDate.of(2026, 1, 31)

        assertEquals(LocalDate.of(2026, 2, 28), resolveDueInput("m+1", endOfJanuary))
        assertEquals(LocalDate.of(2028, 2, 29), resolveDueInput("m+1", LocalDate.of(2028, 1, 31)))
    }

    @Test
    fun `clamping is deliberately not reversible`() {
        // Documented in the corpus as a known limit. Asserted so nobody "fixes" it into something
        // that silently remembers the original day.
        val clamped = resolveDueInput("m+1", LocalDate.of(2026, 1, 31))!!

        assertEquals(LocalDate.of(2026, 1, 28), clamped.minusMonths(1))
    }

    @Test
    fun `resolution is case insensitive`() {
        val today = LocalDate.of(2026, 8, 21)

        assertEquals(resolveDueInput("tomorrow", today), resolveDueInput("Tomorrow", today))
        assertEquals(resolveDueInput("t+3", today), resolveDueInput("T+3", today))
    }

    @Test
    fun `spaced and hyphenated shortcuts agree`() {
        val today = LocalDate.of(2026, 8, 21)

        assertEquals(resolveDueInput("next week", today), resolveDueInput("next-week", today))
        assertEquals(resolveDueInput("next month", today), resolveDueInput("next-month", today))
        assertEquals(resolveDueInput("next year", today), resolveDueInput("next-year", today))
    }

    @Test
    fun `garbage does not resolve`() {
        val today = LocalDate.of(2026, 8, 21)

        listOf("someday", "t-3", "w+", "t+", "+3", "t+x", "", "   ", "2026-13-01").forEach {
            assertNull("'$it' should not resolve", resolveDueInput(it, today))
        }
    }

    @Test
    fun `a resolved shortcut is always stored as ISO`() {
        // The whole point of resolving at entry: what lands in the body is a literal date, so the
        // C# side never has to know the shortcut vocabulary exists.
        val resolved = resolveDueInput("t+3", LocalDate.of(2026, 8, 21))!!

        assertEquals("- [ ] milk @2026-08-24", renderChecklist(listOf(ChecklistItem("milk", due = resolved))))
    }

    // --- interaction with the rest of the body -----------------------------------------------------

    @Test
    fun `hashtags survive alongside a due date`() {
        val item = parseChecklist("- [ ] renew passport @2026-08-25 #admin").single()

        assertEquals("renew passport #admin", item.text)
        assertEquals(LocalDate.of(2026, 8, 25), item.due)
        // Tag parsing reads the raw body, so the tag is still findable there.
        assertEquals(listOf("admin"), parseTags("- [ ] renew passport @2026-08-25 #admin"))
    }

    @Test
    fun `undated items are untouched by any of this`() {
        val body = "- [ ] milk\n- [x] eggs"

        assertEquals(parseChecklist(body), parseChecklist(renderChecklist(parseChecklist(body))))
        assertTrue(parseChecklist(body).all { it.due == null })
    }

    @Test
    fun `cleaning an item keeps its date`() {
        val cleaned = listOf(ChecklistItem("milk ", due = LocalDate.of(2026, 8, 25))).cleaned()

        assertEquals(listOf(ChecklistItem("milk", due = LocalDate.of(2026, 8, 25))), cleaned)
    }

    @Test
    fun `converting a checklist to prose drops the date token`() {
        // Prose has nowhere to record a due date, the same way it has nowhere to record a tick.
        assertEquals("renew passport", toNoteBody("- [ ] renew passport @2026-08-25"))
    }

    private fun fixture(): JSONObject {
        val json = javaClass.getResourceAsStream("/due-date-parsing.json")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("fixtures/due-date-parsing.json not on the test classpath — see the test resources srcDir in build.gradle.kts")
        return JSONObject(json)
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}
