package com.flambeauriver.dailydriver.notes

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistParsingTest {

    // --- the shared fixture corpus -----------------------------------------------------------------

    // These cases live in fixtures/checklist-parsing.json, outside android/, because §4.6 has the
    // Android and desktop UIs rendering "two views of one Markdown body" — the same drift seam
    // §7.3 names for tag parsing. Both suites execute the same file so a disagreement fails a test
    // instead of a grocery list quietly reordering itself between devices. Add cases to the
    // fixture, not here.

    @Test
    fun `every parse case in the shared fixture corpus passes`() {
        val failures = fixture().getJSONArray("cases").objects().mapNotNull { case ->
            val body = case.getString("body")
            val expected = case.getJSONArray("items").items()
            val actual = parseChecklist(body)
            if (actual == expected) null else "${case.getString("name")}: expected $expected, got $actual"
        }

        assertTrue("${failures.size} fixture case(s) failed:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test
    fun `every render case in the shared fixture corpus passes`() {
        val failures = fixture().getJSONArray("renderCases").objects().mapNotNull { case ->
            val expected = case.getString("body")
            val actual = renderChecklist(case.getJSONArray("items").items())
            if (actual == expected) null else "${case.getString("name")}: expected '$expected', got '$actual'"
        }

        assertTrue("${failures.size} fixture case(s) failed:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test
    fun `every conversion case in the shared fixture corpus passes`() {
        val failures = fixture().getJSONArray("conversionCases").objects().mapNotNull { case ->
            val name = case.getString("name")
            val noteBody = case.getString("noteBody")
            val checklistBody = case.getString("checklistBody")
            when {
                toChecklistBody(noteBody) != checklistBody ->
                    "$name: note→checklist gave '${toChecklistBody(noteBody)}', expected '$checklistBody'"

                toNoteBody(checklistBody) != noteBody.lines().filter { it.isNotBlank() }.joinToString("\n") ->
                    "$name: checklist→note gave '${toNoteBody(checklistBody)}'"

                else -> null
            }
        }

        assertTrue("${failures.size} fixture case(s) failed:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test
    fun `every normalize case in the shared fixture corpus passes`() {
        val failures = fixture().getJSONArray("normalizeCases").objects().mapNotNull { case ->
            val expected = case.getJSONArray("normalized").items()
            val actual = case.getJSONArray("items").items().normalized()
            if (actual == expected) null else "${case.getString("name")}: expected $expected, got $actual"
        }

        assertTrue("${failures.size} fixture case(s) failed:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test
    fun `every clean case in the shared fixture corpus passes`() {
        val failures = fixture().getJSONArray("cleanCases").objects().mapNotNull { case ->
            val expected = case.getJSONArray("cleaned").items()
            val actual = case.getJSONArray("items").items().cleaned()
            if (actual == expected) null else "${case.getString("name")}: expected $expected, got $actual"
        }

        assertTrue("${failures.size} fixture case(s) failed:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test
    fun `the fixture corpus is actually loaded and non-trivial`() {
        // Guards the failure where the resources srcDir breaks and every case above silently
        // passes over an empty list — see the same guard in TagParsingTest.
        val parseCases = fixture().getJSONArray("cases").objects()

        assertTrue("fixture looks empty — check the test resources srcDir", parseCases.size >= 15)
        assertTrue("fixture has no checked cases", parseCases.any { it.getJSONArray("items").items().any { i -> i.checked } })
        assertTrue("fixture has no empty-result cases", parseCases.any { it.getJSONArray("items").length() == 0 })
    }

    // --- properties the fixture format can't express -----------------------------------------------

    @Test
    fun `render then parse returns the same items`() {
        val items = listOf(
            ChecklistItem("milk"),
            ChecklistItem("eggs", checked = true),
            ChecklistItem(""),
            ChecklistItem("bread [2 loaves]"),
        )

        assertEquals(items, parseChecklist(renderChecklist(items)))
    }

    @Test
    fun `note to checklist to note returns the original prose exactly`() {
        // The lossless direction, and the one T024's "round-trip without losing content" is about.
        val prose = "milk\neggs\nbread"

        assertEquals(prose, toNoteBody(toChecklistBody(prose)))
    }

    @Test
    fun `checklist to note to checklist keeps every word but loses the ticks`() {
        // Recorded as a deliberate consequence of stripping markers (design check-in, 2026-08-20),
        // not a bug: prose has nowhere to put a checkbox.
        val checklist = "- [x] milk\n- [ ] eggs"

        val roundTripped = toChecklistBody(toNoteBody(checklist))

        assertEquals("- [ ] milk\n- [ ] eggs", roundTripped)
        assertEquals(listOf("milk", "eggs"), parseChecklist(roundTripped).map { it.text })
    }

    @Test
    fun `converting an already-checklist body does not double-mark it`() {
        val checklist = "- [x] milk\n- [ ] eggs"

        assertEquals(checklist, toChecklistBody(checklist))
    }

    @Test
    fun `normalizing is idempotent`() {
        val items = listOf(ChecklistItem("a", true), ChecklistItem("b"), ChecklistItem("c", true))

        val once = items.normalized()

        assertEquals(once, once.normalized())
    }

    @Test
    fun `normalizing never adds or drops items`() {
        val items = listOf(ChecklistItem("a", true), ChecklistItem("b"), ChecklistItem("c", true))

        assertEquals(items.size, items.normalized().size)
        assertEquals(items.toSet(), items.normalized().toSet())
    }

    // --- reordering ---------------------------------------------------------------------------------

    private val abc = listOf(ChecklistItem("a"), ChecklistItem("b"), ChecklistItem("c"))

    @Test
    fun `moving an item down shifts the ones it passes`() {
        assertEquals(listOf("b", "c", "a"), abc.moved(0, 2).map { it.text })
    }

    @Test
    fun `moving an item up shifts the ones it passes`() {
        assertEquals(listOf("c", "a", "b"), abc.moved(2, 0).map { it.text })
    }

    @Test
    fun `moving an item to its own position changes nothing`() {
        assertEquals(abc, abc.moved(1, 1))
    }

    @Test
    fun `an out-of-range move is ignored rather than throwing`() {
        // `to` comes from a drag offset, so a fling past either end is ordinary input for the
        // gesture layer to hand over — not something worth crashing the editor over.
        assertEquals(abc, abc.moved(0, 9))
        assertEquals(abc, abc.moved(-1, 0))
        assertEquals(abc, abc.moved(0, -1))
        assertEquals(emptyList<ChecklistItem>(), emptyList<ChecklistItem>().moved(0, 0))
    }

    @Test
    fun `moving never adds or drops items`() {
        assertEquals(abc.toSet(), abc.moved(0, 2).toSet())
        assertEquals(abc.size, abc.moved(2, 0).size)
    }

    // --- empties ------------------------------------------------------------------------------------

    @Test
    fun `empty items are dropped on close but survive while editing`() {
        val items = listOf(ChecklistItem("milk"), ChecklistItem(""), ChecklistItem("eggs"))

        // Survives a parse/render cycle, so a half-typed row doesn't vanish under the cursor.
        assertEquals(items, parseChecklist(renderChecklist(items)))
        // ...and is dropped once the editor closes.
        assertEquals(listOf("milk", "eggs"), items.cleaned().map { it.text })
    }

    @Test
    fun `a list with nothing but empties empties completely`() {
        assertEquals(emptyList<ChecklistItem>(), listOf(ChecklistItem(""), ChecklistItem("", true)).cleaned())
    }

    // --- typing a space (regression) ----------------------------------------------------------------

    @Test
    fun `a trailing space survives the round trip through the body`() {
        // The regression this section exists for. Item text is edited *through the body* — the
        // editor renders to Markdown and re-parses on every keystroke — so trimming at parse time
        // ate the space the moment it was typed, and a two-word item was impossible to enter.
        val typed = ChecklistItem("peanut ")

        assertEquals(listOf(typed), parseChecklist(renderChecklist(listOf(typed))))
    }

    @Test
    fun `typing a two-word item one character at a time arrives intact`() {
        // Simulates what the editor actually does: after each keystroke the whole list is rendered
        // to the body and parsed back out again. Anything lost in that cycle is lost for good.
        val target = "peanut butter"

        var body = renderChecklist(listOf(ChecklistItem("")))
        target.forEach { character ->
            val current = parseChecklist(body).single()
            body = renderChecklist(listOf(current.copy(text = current.text + character)))
        }

        assertEquals(target, parseChecklist(body).single().text)
    }

    @Test
    fun `trailing whitespace is tidied away on close`() {
        assertEquals(listOf("peanut"), listOf(ChecklistItem("peanut ")).cleaned().map { it.text })
        assertEquals(listOf("peanut butter"), listOf(ChecklistItem("  peanut butter  ")).cleaned().map { it.text })
    }

    @Test
    fun `converting to prose does not carry whitespace across`() {
        assertEquals("peanut\nmilk", toNoteBody("- [ ] peanut \n- [x] milk  "))
    }

    // --- fixture loading ----------------------------------------------------------------------------

    private fun fixture(): JSONObject {
        val json = javaClass.getResourceAsStream("/checklist-parsing.json")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("fixtures/checklist-parsing.json not on the test classpath — see the test resources srcDir in build.gradle.kts")
        return JSONObject(json)
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun JSONArray.items(): List<ChecklistItem> = objects().map {
        ChecklistItem(text = it.getString("text"), checked = it.getBoolean("checked"))
    }
}
