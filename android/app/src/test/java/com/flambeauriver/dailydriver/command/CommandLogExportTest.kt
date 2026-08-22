package com.flambeauriver.dailydriver.command

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** T166 — what the in-app export writes. */
class CommandLogExportTest {

    private fun entry(
        input: String,
        target: String = "com.spotify.music",
        tier: String = "T0",
        count: Int = 1,
        firstSeen: Long = 1_000L,
        lastSeen: Long = 2_000L,
    ) = CommandLogEntry(input, target, tier, count, firstSeen, lastSeen)

    private fun lines(jsonl: String) = jsonl.trim().lines().filter { it.isNotEmpty() }.map { JSONObject(it) }

    // --- the cross-tool contract -------------------------------------------------------------------

    @Test
    fun `every documented key is present, and no others`() {
        // ⚠️ Load-bearing. scripts/export-command-log.sh writes the same JSONL from a pulled
        // database, and T075 hand-labels whichever one it is handed. Two producers, one consumer:
        // if this test is failing because a key was added, add it to the script too.
        val json = lines(commandLogJsonl(listOf(entry("spotify")))).single()

        assertEquals(COMMAND_LOG_KEYS.toSet(), json.keys().asSequence().toSet())
    }

    @Test
    fun `fields carry through unchanged`() {
        val json = lines(
            commandLogJsonl(
                listOf(entry("spotify", target = "com.spotify.music", tier = "T1", count = 7, firstSeen = 11, lastSeen = 22)),
            ),
        ).single()

        assertEquals("spotify", json.getString("input"))
        assertEquals("com.spotify.music", json.getString("resolution"))
        assertEquals("T1", json.getString("tier"))
        assertEquals(7, json.getInt("count"))
        assertEquals(11L, json.getLong("firstSeen"))
        assertEquals(22L, json.getLong("lastSeen"))
    }

    @Test
    fun `an unresolved row reports a null resolution, not an empty string`() {
        // The script writes `target or None`. A reader that saw "" here and null there would split
        // the T3 examples in two, which are precisely the ones §8 cannot invent.
        val json = lines(commandLogJsonl(listOf(entry("wat", target = "", tier = "T3")))).single()

        assertTrue(json.isNull("resolution"))
    }

    @Test
    fun `casing and spacing in the input survive`() {
        // What was typed is the training input. Normalising it here would throw away a variation
        // the model has to handle at inference.
        val json = lines(commandLogJsonl(listOf(entry("  Call Mum  ")))).single()

        assertEquals("  Call Mum  ", json.getString("input"))
    }

    // --- JSONL shape -------------------------------------------------------------------------------

    @Test
    fun `one object per line, in the order given`() {
        val jsonl = commandLogJsonl(listOf(entry("a"), entry("b"), entry("c")))

        assertEquals(listOf("a", "b", "c"), lines(jsonl).map { it.getString("input") })
        assertEquals(3, jsonl.trim().lines().size)
    }

    @Test
    fun `a newline in the input does not break the line format`() {
        // JSONL's one rule. An unescaped newline would turn one row into two unparseable ones, and
        // the prompt does not stop you pasting a multi-line string into it.
        val jsonl = commandLogJsonl(listOf(entry("call\nmum")))

        assertEquals(1, jsonl.trim().lines().size)
        assertEquals("call\nmum", lines(jsonl).single().getString("input"))
    }

    @Test
    fun `an empty log is an empty file, not a crash`() {
        assertEquals("", commandLogJsonl(emptyList()))
    }

    // --- stats -------------------------------------------------------------------------------------

    @Test
    fun `stats count rows, submissions and unresolved separately`() {
        val stats = commandLogStats(
            listOf(
                entry("spotify", count = 10, tier = "T1"),
                entry(":apps", count = 4, tier = "T0"),
                entry("wat", count = 2, tier = "T3", target = ""),
            ),
        )

        assertEquals(3, stats.distinct)
        assertEquals(16, stats.submissions)
        assertEquals(1, stats.unresolved)
    }

    @Test
    fun `an empty log has zero of everything`() {
        assertEquals(CommandLogStats(0, 0, 0), commandLogStats(emptyList()))
    }

    // --- what the transcript says ------------------------------------------------------------------

    @Test
    fun `the message reports counts and the location`() {
        val text = exportedText(CommandLogStats(distinct = 42, submissions = 137, unresolved = 9), "Download/x.jsonl")

        assertTrue(text, text.contains("42 inputs"))
        assertTrue(text, text.contains("137 submissions"))
        assertTrue(text, text.contains("9 unresolved"))
        assertTrue(text, text.contains("Download/x.jsonl"))
    }

    @Test
    fun `an empty export still says so rather than looking like nothing happened`() {
        // The whole failure mode T166 exists to fix is a route that quietly does nothing and
        // reports success. Zero has to be visible.
        val text = exportedText(CommandLogStats(0, 0, 0), "Download/x.jsonl")

        assertTrue(text, text.contains("0 inputs"))
    }

    @Test
    fun `a single row is not reported in the plural`() {
        val text = exportedText(CommandLogStats(distinct = 1, submissions = 1, unresolved = 0), "Download/x.jsonl")

        assertTrue(text, text.contains("1 input "))
        assertTrue(text, text.contains("1 submission,"))
    }

    // --- file name ---------------------------------------------------------------------------------

    @Test
    fun `the file name is stamped to the second and ISO-ordered`() {
        assertEquals(
            "dailydriver-command-log-2026-08-22-131045.jsonl",
            commandLogFileName(LocalDateTime.of(2026, 8, 22, 13, 10, 45)),
        )
    }

    @Test
    fun `every field is zero-padded`() {
        // So a directory listing sorts chronologically, which is the point of the stamp being in
        // the name at all.
        assertEquals(
            "dailydriver-command-log-2026-01-05-090304.jsonl",
            commandLogFileName(LocalDateTime.of(2026, 1, 5, 9, 3, 4)),
        )
    }

    @Test
    fun `two exports a second apart get different names`() {
        // ⚠️ Load-bearing, and found on-device. MediaStore de-duplicates a repeated display name
        // by appending " (1)" AFTER the extension — "….jsonl (1)" — which a *.jsonl glob then
        // skips entirely. The name has to be unique before MediaStore ever sees it.
        val first = commandLogFileName(LocalDateTime.of(2026, 8, 22, 13, 10, 45))
        val second = commandLogFileName(LocalDateTime.of(2026, 8, 22, 13, 10, 46))

        assertTrue(first.endsWith(".jsonl") && second.endsWith(".jsonl"))
        assertEquals(2, setOf(first, second).size)
    }
}
