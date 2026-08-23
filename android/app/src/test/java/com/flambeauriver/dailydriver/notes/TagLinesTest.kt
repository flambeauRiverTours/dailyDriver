package com.flambeauriver.dailydriver.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** T169 — tag lines are metadata, not checklist items. */
class TagLinesTest {

    // --- the bug -----------------------------------------------------------------------------------

    @Test
    fun `a tag appended by a chip is not a checklist item`() {
        // The reported bug: adding #shopping to a checklist produced a checkbox saying "#shopping".
        val body = withTag("- [ ] milk\n- [ ] eggs", "shopping")

        val items = parseChecklist(splitTagLines(body).content)

        assertEquals(listOf("milk", "eggs"), items.map { it.text })
    }

    @Test
    fun `the tag survives a parse and render round-trip`() {
        // ⚠️ The reason this is a split rather than a parser change. The editor runs
        // render(transform(parse(body))) on every keystroke, so anything the parser drops is
        // deleted a frame later. Dropping tag lines inside parseChecklist would have "fixed" the
        // checkbox by destroying the tag.
        val body = withTag("- [ ] milk", "shopping")
        val parts = splitTagLines(body)

        val rebuilt = parts.rejoin(renderChecklist(parseChecklist(parts.content)))

        assertEquals(listOf("shopping"), parseTags(rebuilt))
        assertEquals(listOf("milk"), parseChecklist(splitTagLines(rebuilt).content).map { it.text })
    }

    @Test
    fun `many round trips neither duplicate nor lose the tags`() {
        var body = withTag(withTag("- [ ] milk", "shopping"), "home")
        repeat(5) {
            val parts = splitTagLines(body)
            body = parts.rejoin(renderChecklist(parseChecklist(parts.content)))
        }

        assertEquals(setOf("shopping", "home"), parseTags(body).toSet())
        assertEquals(listOf("milk"), parseChecklist(splitTagLines(body).content).map { it.text })
    }

    // --- what counts as a tag line -----------------------------------------------------------------

    @Test
    fun `a line of only tags is lifted`() {
        val parts = splitTagLines("content\n#work #home")

        assertEquals(listOf("#work #home"), parts.tagLines)
        assertEquals("content", parts.content)
    }

    @Test
    fun `a line mixing a tag with prose stays content`() {
        // A tag written inline belongs to the sentence it is in. Lifting it would move the words
        // around it, and parseTags still finds it where it is.
        val parts = splitTagLines("remember the #milk today")

        assertTrue(parts.tagLines.isEmpty())
        assertEquals("remember the #milk today", parts.content)
    }

    @Test
    fun `a line with a non-tag token stays content`() {
        // Deliberately conservative: #include has a second token beside it, so pasted code is not
        // quietly reclassified as metadata.
        val parts = splitTagLines("#include <stdio.h>")

        assertTrue(parts.tagLines.isEmpty())
    }

    @Test
    fun `a digits-only hashtag is not a tag`() {
        // Matches parseTags' at-least-one-letter rule, so an issue reference stays prose.
        val parts = splitTagLines("#123")

        assertTrue(parts.tagLines.isEmpty())
        assertTrue(parseTags("#123").isEmpty())
    }

    @Test
    fun `blank lines are not tag lines`() {
        val parts = splitTagLines("a\n\nb")

        assertTrue(parts.tagLines.isEmpty())
        assertEquals("a\n\nb", parts.content)
    }

    @Test
    fun `a body with no tags is returned unchanged`() {
        val parts = splitTagLines("- [ ] milk\n- [x] eggs")

        assertTrue(parts.tagLines.isEmpty())
        assertEquals("- [ ] milk\n- [x] eggs", parts.content)
        assertEquals("- [ ] milk\n- [x] eggs", parts.rejoin(parts.content))
    }

    @Test
    fun `a body that is only tags leaves empty content`() {
        val parts = splitTagLines("#work")

        assertEquals(listOf("#work"), parts.tagLines)
        assertEquals("", parts.content)
        assertEquals("#work", parts.rejoin(""))
    }

    // --- rejoin ------------------------------------------------------------------------------------

    @Test
    fun `rejoin puts tags below the content`() {
        val parts = splitTagLines("#work\n- [ ] milk")

        assertEquals("- [ ] milk\n#work", parts.rejoin(parts.content))
    }

    @Test
    fun `rejoin accepts content the transform grew or shrank`() {
        val parts = splitTagLines("- [ ] milk\n#work")

        assertEquals("- [ ] milk\n- [ ] eggs\n#work", parts.rejoin("- [ ] milk\n- [ ] eggs"))
        assertEquals("#work", parts.rejoin(""))
    }

    // --- conversion (T024) -------------------------------------------------------------------------

    @Test
    fun `tags survive note to checklist conversion`() {
        // A tag is a property of the note, not of how it currently renders.
        val parts = splitTagLines("milk\neggs\n#shopping")

        val asChecklist = parts.rejoin(toChecklistBody(parts.content))

        assertEquals("- [ ] milk\n- [ ] eggs\n#shopping", asChecklist)
    }

    @Test
    fun `tags survive checklist to note conversion`() {
        val parts = splitTagLines("- [ ] milk\n- [ ] eggs\n#shopping")

        assertEquals("milk\neggs\n#shopping", parts.rejoin(toNoteBody(parts.content)))
    }

    @Test
    fun `a bare hash is not a tag line, which is why checklists need their own input`() {
        // ⚠️ This is the gap the split cannot close, and the reason T169 also changed the UI.
        // T139's "+ tag" appends a bare "#" for the user to type after — fine in a prose note,
        // which has a body field. A checklist has none, so the "#" lands as a checkbox, and typing
        // into it yields "- [ ] #shopping": an item shaped like a tag that can never become one.
        // A bare "#" is correctly *not* a tag (no name, no letter), so nothing here can rescue it.
        val body = "- [ ] milk\n#"

        assertTrue(splitTagLines(body).tagLines.isEmpty())
        assertEquals(listOf("milk", "#"), parseChecklist(splitTagLines(body).content).map { it.text })
    }

    @Test
    fun `a tag committed from the checklist input is lifted normally`() {
        // What the checklist tag input actually produces: withTag, exactly as the chips use.
        val body = withTag("- [ ] milk", "shopping")

        assertEquals(listOf("#shopping"), splitTagLines(body).tagLines)
        assertEquals(listOf("milk"), parseChecklist(splitTagLines(body).content).map { it.text })
    }

    @Test
    fun `converting does not turn the tag into an item`() {
        // Without the split this produced "- [ ] #shopping", which is the bug one layer down.
        val parts = splitTagLines("milk\n#shopping")

        assertTrue(toChecklistBody(parts.content).lines().none { it.contains("#shopping") })
    }
}
