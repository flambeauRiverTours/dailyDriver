package com.flambeauriver.dailydriver.notes

import org.junit.Assert.assertEquals
import org.junit.Test

/** T168 — the `:notes` snippet shows content, not Markdown. */
class NoteSnippetT168Test {

    @Test
    fun `a task note previews without its marker or date token`() {
        // The reported bug, reproduced on an emulator: the snippet read
        // "- [ ] buy milk @2026-08-23".
        assertEquals("buy milk", noteSnippet("- [ ] buy milk @2026-08-23"))
    }

    @Test
    fun `a checked item loses its marker too`() {
        assertEquals("buy milk", noteSnippet("- [x] buy milk"))
    }

    @Test
    fun `the marker is stripped even without a date`() {
        // Fixing the token and leaving the marker would say the leak was a formatting slip rather
        // than the snippet never having been decoded at all.
        assertEquals("milk", noteSnippet("- [ ] milk\n- [ ] eggs"))
    }

    @Test
    fun `plain prose is unchanged`() {
        assertEquals("just some prose", noteSnippet("just some prose"))
    }

    @Test
    fun `a leading tag line is skipped in favour of content`() {
        // A note whose body opens with a chip-added tag should preview what it says, not how it
        // is filed.
        assertEquals("the actual content", noteSnippet("#work\nthe actual content"))
    }

    @Test
    fun `an empty item is skipped in favour of the first with text`() {
        // "- [ ]" is the half-typed row you are about to fill in; it makes a useless preview.
        assertEquals("eggs", noteSnippet("- [ ]\n- [ ] eggs"))
    }

    @Test
    fun `a body with nothing to say has no snippet`() {
        assertEquals("", noteSnippet(""))
        assertEquals("", noteSnippet("#work"))
        assertEquals("", noteSnippet("- [ ]"))
    }

    @Test
    fun `a long line is still truncated`() {
        val snippet = noteSnippet("- [ ] " + "x".repeat(500))

        assert(snippet.endsWith("…")) { snippet }
        assert(snippet.length < 200) { snippet }
    }
}
