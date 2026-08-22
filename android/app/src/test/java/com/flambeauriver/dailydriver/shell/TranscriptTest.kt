package com.flambeauriver.dailydriver.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
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

    // --- entry shape ----------------------------------------------------------------------------

    @Test
    fun `entries always alternate input then output`() {
        // TranscriptLine renders the two types differently (prompt glyph vs dimmed), so a pair
        // landing out of order would render as a command that was never typed.
        val transcript = Transcript()
            .record("one", "1")
            .record("two", "2")
            .record("three", "3")

        transcript.entries.forEachIndexed { index, entry ->
            val expectInput = index % 2 == 0
            assertEquals("entry $index", expectInput, entry is TranscriptEntry.Input)
        }
    }

    @Test
    fun `every record adds exactly two entries`() {
        val transcript = (1..10).fold(Transcript()) { acc, i -> acc.record("cmd$i", "out$i") }

        assertEquals(20, transcript.entries.size)
    }

    @Test
    fun `order is preserved across many records`() {
        val transcript = (1..50).fold(Transcript()) { acc, i -> acc.record("cmd$i", "out$i") }

        assertEquals("cmd1", transcript.entries.first().text)
        assertEquals("out50", transcript.entries.last().text)
    }

    // --- the cap (T016) ---------------------------------------------------------------------------

    /** [records] commands, each of which contributes two entries. */
    private fun transcriptOf(records: Int) =
        (1..records).fold(Transcript()) { acc, i -> acc.record("cmd$i", "out$i") }

    /** The number of commands that exactly fills the cap. */
    private val recordsAtCap = MAX_TRANSCRIPT_ENTRIES / 2

    @Test
    fun `a transcript filled exactly to the cap keeps everything`() {
        val transcript = transcriptOf(recordsAtCap)

        assertEquals(MAX_TRANSCRIPT_ENTRIES, transcript.entries.size)
        assertEquals("cmd1", transcript.entries.first().text)
    }

    @Test
    fun `one record past the cap evicts exactly one exchange`() {
        val transcript = transcriptOf(recordsAtCap + 1)

        assertEquals(MAX_TRANSCRIPT_ENTRIES, transcript.entries.size)
        assertEquals("cmd2", transcript.entries.first().text)
    }

    @Test
    fun `the cap is not exceeded no matter how many records arrive`() {
        // The launcher is the home screen — this process is resumed and paused for months rather
        // than restarted, so "eventually" here is a real state, not a stress test.
        val transcript = transcriptOf(MAX_TRANSCRIPT_ENTRIES * 3)

        assertEquals(MAX_TRANSCRIPT_ENTRIES, transcript.entries.size)
    }

    @Test
    fun `it is the oldest entries that fall off`() {
        val records = recordsAtCap * 2

        val transcript = transcriptOf(records)

        // Only the most recent `recordsAtCap` commands survive.
        assertEquals("cmd${records - recordsAtCap + 1}", transcript.entries.first().text)
        assertEquals("out$records", transcript.entries.last().text)
    }

    @Test
    fun `trimming never orphans an output from its input`() {
        // The reason the cap is even. An input is always followed by its own output, so a
        // scrollback can never open on an answer to a question that's already been dropped.
        val transcript = transcriptOf(MAX_TRANSCRIPT_ENTRIES * 2)

        transcript.entries.forEachIndexed { index, entry ->
            assertEquals("entry $index", index % 2 == 0, entry is TranscriptEntry.Input)
        }
    }

    @Test
    fun `a transcript under the cap is left completely alone`() {
        val transcript = transcriptOf(3)

        assertEquals(6, transcript.entries.size)
        assertEquals("cmd1", transcript.entries.first().text)
    }

    @Test
    fun `blank input does not consume cap headroom`() {
        // record() ignores blank input entirely, so a run of stray Enters can't evict real history.
        val filled = transcriptOf(1)

        val afterBlanks = (1..10).fold(filled) { acc, _ -> acc.record("   ", "ignored") }

        assertEquals(filled, afterBlanks)
    }

    @Test
    fun `record returns a new instance rather than the receiver`() {
        val original = Transcript()

        assertNotSame(original, original.record("chrome", "out"))
    }

    // --- content handling -------------------------------------------------------------------------

    @Test
    fun `multi-line output is kept intact — help and alias listings depend on it`() {
        // helpText() and formatAliasListing() both return several lines in one output entry.
        val listing = "a = 1\nb = 2"

        assertEquals(listing, Transcript().record(":alias", listing).entries[1].text)
    }

    @Test
    fun `newlines inside input survive the edge trim`() {
        assertEquals("a\nb", Transcript().record("  a\nb  ", "out").entries.first().text)
    }

    @Test
    fun `long input is recorded in full`() {
        val long = "x".repeat(1000)

        assertEquals(long, Transcript().record(long, "out").entries.first().text)
    }

    @Test
    fun `non-ASCII input and output survive`() {
        val transcript = Transcript().record("日本語", "réponse")

        assertEquals(listOf("日本語", "réponse"), transcript.entries.map { it.text })
    }

    @Test
    fun `whitespace-only output is preserved rather than normalised away`() {
        // Only the *input* side is trimmed; output is whatever the pipeline produced.
        assertEquals("   ", Transcript().record("chrome", "   ").entries[1].text)
    }

    // --- value semantics --------------------------------------------------------------------------

    @Test
    fun `transcripts with the same entries are equal`() {
        // Compose recomposition keys off this — two structurally identical transcripts must not
        // read as a change.
        assertEquals(Transcript().record("a", "1"), Transcript().record("a", "1"))
    }

    @Test
    fun `transcripts with different entries are not equal`() {
        assertNotEquals(Transcript().record("a", "1"), Transcript().record("a", "2"))
    }

    @Test
    fun `an entry's own equality is by type as well as text`() {
        // Input("x") and Output("x") both have text "x" but must never compare equal, or the
        // alternation assertion above could pass on a malformed transcript.
        val input: TranscriptEntry = TranscriptEntry.Input("x")
        val output: TranscriptEntry = TranscriptEntry.Output("x")

        assertNotEquals(input, output)
    }
}
