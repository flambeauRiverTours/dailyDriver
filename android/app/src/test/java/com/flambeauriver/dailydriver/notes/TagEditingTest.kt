package com.flambeauriver.dailydriver.notes

import org.junit.Assert.assertEquals
import org.junit.Test

/** T139 — adding and removing tags by editing the body, so the body stays the only writer. */
class TagEditingTest {

    // --- adding ------------------------------------------------------------------------------------

    @Test
    fun `a tag is appended on its own line`() {
        assertEquals("groceries\n#errands", withTag("groceries", "errands"))
    }

    @Test
    fun `an empty body takes the tag alone`() {
        assertEquals("#errands", withTag("", "errands"))
    }

    @Test
    fun `a trailing newline is not doubled`() {
        assertEquals("groceries\n#errands", withTag("groceries\n", "errands"))
    }

    @Test
    fun `adding a tag the body already carries changes nothing`() {
        // Idempotent, so the chip row can't produce "#work #work" by double-tapping.
        assertEquals("buy milk #errands", withTag("buy milk #errands", "errands"))
        assertEquals("buy milk #errands", withTag("buy milk #errands", "#errands"))
        assertEquals("buy milk #errands", withTag("buy milk #errands", "ERRANDS"))
    }

    @Test
    fun `a leading hash is accepted and not doubled`() {
        assertEquals("note\n#work", withTag("note", "#work"))
    }

    @Test
    fun `tags are lowercased, matching how they parse`() {
        assertEquals("note\n#work", withTag("note", "Work"))
    }

    @Test
    fun `a tag with no letters is refused`() {
        // parseTags requires a letter, so "#123" would be written and then never parse back —
        // a chip that vanishes the moment you add it.
        assertEquals("note", withTag("note", "123"))
        assertEquals("note", withTag("note", ""))
        assertEquals("note", withTag("note", "  "))
    }

    @Test
    fun `an added tag parses back out`() {
        // The round trip that makes chips a view rather than a second store.
        assertEquals(listOf("errands"), parseTags(withTag("groceries", "errands")))
    }

    // --- removing ----------------------------------------------------------------------------------

    @Test
    fun `removing a tag on its own line drops the line`() {
        assertEquals("groceries", withoutTag("groceries\n#errands", "errands"))
    }

    @Test
    fun `removing an inline tag leaves the prose intact`() {
        assertEquals("buy milk today", withoutTag("buy milk #errands today", "errands"))
    }

    @Test
    fun `removing a trailing inline tag leaves no trailing space`() {
        assertEquals("buy milk", withoutTag("buy milk #errands", "errands"))
    }

    @Test
    fun `every occurrence goes, not just the first`() {
        // A tag is a property of the note, not of a position in it. Leaving a second one would put
        // the chip straight back on the next parse and read as the tap having failed.
        assertEquals("a\nb", withoutTag("a #work\nb #work", "work"))
        assertEquals(emptyList<String>(), parseTags(withoutTag("a #work\nb #work", "work")))
    }

    @Test
    fun `removal is case insensitive, since parsing lowercases`() {
        assertEquals("note", withoutTag("note #Work", "work"))
        assertEquals("note", withoutTag("note #work", "WORK"))
    }

    @Test
    fun `a tag that is a prefix of another is not removed by mistake`() {
        // The word boundary earns its place here: removing "work" must not touch "#workout".
        assertEquals("note #workout", withoutTag("note #workout", "work"))
    }

    @Test
    fun `removing a tag that is not there changes nothing`() {
        assertEquals("note #work", withoutTag("note #work", "errands"))
    }

    @Test
    fun `blank lines in prose survive removal`() {
        assertEquals("para one\n\npara two", withoutTag("para one\n\npara two\n#work", "work"))
    }

    @Test
    fun `add then remove returns the original body`() {
        val body = "groceries"

        assertEquals(body, withoutTag(withTag(body, "errands"), "errands"))
    }

    @Test
    fun `chips and inline hashtags produce the same tag set`() {
        // T139's actual requirement: one operation, not two sources of truth.
        val viaChip = withTag("buy milk", "errands")
        val viaTyping = "buy milk #errands"

        assertEquals(parseTags(viaTyping), parseTags(viaChip))
    }
}
