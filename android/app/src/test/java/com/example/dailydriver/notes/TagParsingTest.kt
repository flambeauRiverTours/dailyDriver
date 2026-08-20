package com.example.dailydriver.notes

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TagParsingTest {

    // --- the shared contract ---------------------------------------------------------------------
    //
    // These cases live in fixtures/tag-parsing.json, outside android/, because design doc §7.3 has
    // Notes.Shared implementing the same rules in C# (T042) and names tag parsing as a place the
    // two will drift. Both suites execute this file, so a disagreement fails a test instead of
    // tags quietly differing between devices. Add cases to the fixture, not here.

    @Test
    fun `every case in the shared fixture corpus passes`() {
        val failures = fixtureCases().mapNotNull { case ->
            val actual = parseTags(case.body)
            if (actual == case.expected) null else "${case.name}: expected ${case.expected}, got $actual"
        }

        assertTrue(
            "${failures.size} fixture case(s) failed:\n${failures.joinToString("\n")}",
            failures.isEmpty(),
        )
    }

    @Test
    fun `the fixture corpus is actually loaded and non-trivial`() {
        // Guards the resource wiring itself. If the srcDir mapping broke, every case above would
        // vacuously pass over an empty list and the contract would silently stop being tested.
        val cases = fixtureCases()

        assertTrue("fixture looks empty — check the test resources srcDir", cases.size >= 20)
        assertTrue("fixture has no positive cases", cases.any { it.expected.isNotEmpty() })
        assertTrue("fixture has no negative cases", cases.any { it.expected.isEmpty() })
    }

    // --- properties the fixture format can't express ------------------------------------------------

    @Test
    fun `parsing is pure — repeated calls agree`() {
        val body = "#work #urgent\n```\n#include\n```\n#work"

        assertEquals(parseTags(body), parseTags(body))
    }

    @Test
    fun `results never contain the hash itself`() {
        parseTags("#work #café #2026goals").forEach {
            assertTrue("'$it' kept its #", !it.startsWith("#"))
        }
    }

    @Test
    fun `results are already lowercase, so callers never need to normalise again`() {
        // NoteDao writes these straight into note_tags, whose (noteId, tag) key would otherwise
        // admit "#Work" and "#work" as two rows.
        parseTags("#Work #URGENT #MiXeD").forEach {
            assertEquals(it, it.lowercase())
        }
    }

    @Test
    fun `results are distinct, so the composite primary key never rejects an insert`() {
        val tags = parseTags("#work #Work #WORK #work-item")

        assertEquals(tags.distinct(), tags)
    }

    @Test
    fun `a very long body is handled without special-casing`() {
        val body = (1..500).joinToString("\n") { "line $it with #tag$it" }

        assertEquals(500, parseTags(body).size)
    }

    @Test
    fun `a body that is nothing but a fence yields nothing and does not throw`() {
        assertEquals(emptyList<String>(), parseTags("```"))
        assertEquals(emptyList<String>(), parseTags("```\n```"))
    }

    // --- fixture loading -------------------------------------------------------------------------------

    private data class FixtureCase(val name: String, val body: String, val expected: List<String>)

    private fun fixtureCases(): List<FixtureCase> {
        val json = javaClass.getResourceAsStream("/tag-parsing.json")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("fixtures/tag-parsing.json not on the test classpath — see the test resources srcDir in build.gradle.kts")

        val cases = JSONObject(json).getJSONArray("cases")
        return (0 until cases.length()).map { index ->
            val case = cases.getJSONObject(index)
            val expected = case.getJSONArray("tags")
            FixtureCase(
                name = case.getString("name"),
                body = case.getString("body"),
                expected = (0 until expected.length()).map { expected.getString(it) },
            )
        }
    }
}
