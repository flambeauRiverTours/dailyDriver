package com.example.dailydriver.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteQueryTest {

    // --- parsing ---------------------------------------------------------------------------------

    @Test
    fun `an empty query means the whole list`() {
        assertEquals(NoteQuery.All, parseNoteQuery(""))
        assertEquals(NoteQuery.All, parseNoteQuery("   "))
        assertEquals(NoteQuery.All, parseNoteQuery("\t\n"))
    }

    @Test
    fun `plain words are a text search`() {
        assertEquals(NoteQuery.Text("standup"), parseNoteQuery("standup"))
        assertEquals(NoteQuery.Text("morning pages"), parseNoteQuery("morning pages"))
    }

    @Test
    fun `surrounding whitespace is trimmed off the term`() {
        assertEquals(NoteQuery.Text("standup"), parseNoteQuery("  standup  "))
    }

    @Test
    fun `a lone hashtag token is a tag filter`() {
        assertEquals(NoteQuery.Tag("work"), parseNoteQuery("#work"))
    }

    @Test
    fun `a tag filter is normalised the same way tags are stored`() {
        // Tags are lowercased on the way into note_tags (T019), so a search that didn't
        // lowercase would silently never match anything typed with a capital.
        assertEquals(NoteQuery.Tag("work"), parseNoteQuery("#Work"))
        assertEquals(NoteQuery.Tag("work"), parseNoteQuery("#WORK"))
    }

    @Test
    fun `a hashtag followed by more words is a text search, not a tag filter`() {
        // Otherwise "notes" would be silently dropped and the user would get every #work note
        // back, having asked for something narrower.
        assertEquals(NoteQuery.Text("#work notes"), parseNoteQuery("#work notes"))
    }

    @Test
    fun `a hashtag that is not a valid tag falls through to text search`() {
        // T019 requires a letter, so "#123" is not a tag. Searching for it should find notes that
        // literally contain "#123" — which is what someone typing an issue number wants.
        assertEquals(NoteQuery.Text("#123"), parseNoteQuery("#123"))
        assertEquals(NoteQuery.Text("#"), parseNoteQuery("#"))
    }

    @Test
    fun `tag grammar is not re-implemented — it tracks T019`() {
        // Hyphens and underscores are valid in tags; non-ASCII is too. If these diverged from
        // TagParsing, searching for a tag you wrote would stop finding it.
        assertEquals(NoteQuery.Tag("work-item"), parseNoteQuery("#work-item"))
        assertEquals(NoteQuery.Tag("work_item"), parseNoteQuery("#work_item"))
        assertEquals(NoteQuery.Tag("café"), parseNoteQuery("#café"))
    }

    @Test
    fun `every tag the parser accepts is one parseTags would produce`() {
        // The property that keeps search and storage in agreement, asserted rather than trusted.
        listOf("#work", "#Work", "#work-item", "#2026goals", "#café").forEach { raw ->
            val parsed = parseNoteQuery(raw)
            assertTrue("$raw parsed as $parsed", parsed is NoteQuery.Tag)
            assertEquals(raw, parseTags(raw), listOf((parsed as NoteQuery.Tag).tag))
        }
    }

    @Test
    fun `parsing is pure — repeated calls agree`() {
        repeat(3) {
            assertEquals(NoteQuery.Tag("work"), parseNoteQuery("#work"))
            assertEquals(NoteQuery.Text("standup"), parseNoteQuery("standup"))
        }
    }

    // --- LIKE escaping -----------------------------------------------------------------------------

    @Test
    fun `ordinary terms pass through untouched`() {
        assertEquals("standup", escapeLike("standup"))
        assertEquals("morning pages", escapeLike("morning pages"))
    }

    @Test
    fun `percent is escaped, since unescaped it matches everything`() {
        assertEquals("""50\%""", escapeLike("50%"))
    }

    @Test
    fun `underscore is escaped, since unescaped it matches any character`() {
        // Without this, searching "snake_case" would also return "snakeXcase".
        assertEquals("""snake\_case""", escapeLike("snake_case"))
    }

    @Test
    fun `the escape character itself is escaped first`() {
        // Escaping in the wrong order corrupts terms that legitimately contain a backslash.
        assertEquals("""a\\b""", escapeLike("""a\b"""))
        assertEquals("""\\\%""", escapeLike("""\%"""))
    }

    @Test
    fun `escaping an empty term is empty`() {
        assertEquals("", escapeLike(""))
    }

    @Test
    fun `escaping is idempotent in length terms — every wildcard gains exactly one prefix`() {
        assertEquals(escapeLike("%%%").count { it == LIKE_ESCAPE }, 3)
    }

    // --- sole auto-open target -------------------------------------------------------------------------

    private fun note(id: String) = Note(
        id = id,
        type = NoteType.NOTE,
        title = id,
        body = "",
        createdAt = 0L,
        updatedAt = 0L,
        sourceDevice = "test",
    )

    @Test
    fun `exactly one result with a real query is an auto-open target`() {
        assertEquals(note("a"), listOf(note("a")).soleAutoOpenTarget("standup"))
    }

    @Test
    fun `several results are not an auto-open target`() {
        assertEquals(null, listOf(note("a"), note("b")).soleAutoOpenTarget("standup"))
    }

    @Test
    fun `no results are not an auto-open target`() {
        assertEquals(null, emptyList<Note>().soleAutoOpenTarget("standup"))
    }

    @Test
    fun `a blank query never auto-opens, even with a single note stored`() {
        // Auto-open is a response to *filtering down* to one result, never to simply owning one
        // note — bare `:notes` means "show me my notes", and you should be able to just look.
        // Same rule `:apps` applies, for the same reason.
        assertEquals(null, listOf(note("only")).soleAutoOpenTarget(""))
        assertEquals(null, listOf(note("only")).soleAutoOpenTarget("   "))
    }

    @Test
    fun `filtering down to one is what makes auto-open fire`() {
        val two = listOf(note("a"), note("b"))

        assertEquals(null, two.soleAutoOpenTarget("note"))
        assertEquals(note("a"), listOf(note("a")).soleAutoOpenTarget("note"))
    }

    // --- snippets ------------------------------------------------------------------------------------

    @Test
    fun `the snippet is the first line of the body`() {
        assertEquals("milk, eggs", noteSnippet("milk, eggs\nbread\ncheese"))
    }

    @Test
    fun `leading blank lines are skipped`() {
        // A body that opens with newlines would otherwise preview as nothing at all.
        assertEquals("actual content", noteSnippet("\n\n   \nactual content\nmore"))
    }

    @Test
    fun `the snippet is trimmed`() {
        assertEquals("indented", noteSnippet("   indented   \nrest"))
    }

    @Test
    fun `an empty or blank body has no snippet`() {
        assertEquals("", noteSnippet(""))
        assertEquals("", noteSnippet("   \n\t\n  "))
    }

    @Test
    fun `a single-line body is its own snippet`() {
        assertEquals("just this", noteSnippet("just this"))
    }

    @Test
    fun `a long first line is truncated with an ellipsis`() {
        val long = "x".repeat(200)

        val snippet = noteSnippet(long)

        assertTrue(snippet.endsWith("…"))
        assertTrue("was ${snippet.length}", snippet.length <= 81)
    }

    @Test
    fun `a line exactly at the limit is not truncated`() {
        val exact = "x".repeat(80)

        assertEquals(exact, noteSnippet(exact))
    }

    @Test
    fun `snippets never span lines`() {
        listOf("a\nb", "first\nsecond\nthird", "\nx\ny").forEach { body ->
            assertTrue("'$body' produced a multi-line snippet", !noteSnippet(body).contains('\n'))
        }
    }
}
