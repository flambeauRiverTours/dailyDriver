package com.example.dailydriver.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TranscriptTest {

    @Test
    fun `starts empty`() {
        assertEquals(emptyList<TranscriptEntry>(), Transcript().entries)
    }

    @Test
    fun `record appends the input then the output`() {
        val transcript = Transcript().record("chrome", "opening Chrome")

        assertEquals(
            listOf<TranscriptEntry>(
                TranscriptEntry.Input("chrome"),
                TranscriptEntry.Output("opening Chrome"),
            ),
            transcript.entries,
        )
    }

    @Test
    fun `record accumulates across calls, oldest first`() {
        val transcript = Transcript()
            .record("one", "1")
            .record("two", "2")

        assertEquals(listOf("one", "1", "two", "2"), transcript.entries.map { it.text })
    }

    @Test
    fun `record trims surrounding whitespace from input`() {
        val transcript = Transcript().record("   chrome  ", "opening Chrome")

        assertEquals("chrome", transcript.entries.first().text)
    }

    @Test
    fun `record preserves internal whitespace, only trims the edges`() {
        val transcript = Transcript().record("  chrome  --flag  ", "output")

        assertEquals("chrome  --flag", transcript.entries.first().text)
    }

    @Test
    fun `record does not mutate the receiver`() {
        val original = Transcript().record("first", "1")
        val originalEntries = original.entries

        original.record("second", "2")

        assertEquals(originalEntries, original.entries)
    }

    @Test
    fun `blank input is ignored entirely`() {
        val empty = Transcript()

        assertSame(empty, empty.record("", "should not appear"))
        assertSame(empty, empty.record("   ", "should not appear"))
        assertSame(empty, empty.record("\t\n", "should not appear"))
    }

    @Test
    fun `blank input does not disturb existing entries`() {
        val transcript = Transcript().record("chrome", "opening Chrome")

        assertEquals(transcript.entries, transcript.record("  ", "ignored").entries)
    }

    @Test
    fun `output is preserved verbatim, including blank output`() {
        val transcript = Transcript().record("chrome", "")

        assertEquals(listOf("chrome", ""), transcript.entries.map { it.text })
    }
}
