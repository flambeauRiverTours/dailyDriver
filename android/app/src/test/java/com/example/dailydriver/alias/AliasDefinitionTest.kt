package com.example.dailydriver.alias

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AliasDefinitionTest {

    // --- parsing: "alias <name> = <command>" -----------------------------------------------

    @Test
    fun `parses the design doc's own example`() {
        assertEquals(
            AliasDefinitionAttempt("gm", """create_note "standup notes" #work"""),
            parseAliasDefinition("""alias gm = create_note "standup notes" #work"""),
        )
    }

    @Test
    fun `tolerates extra whitespace around the equals sign`() {
        assertEquals(AliasDefinitionAttempt("gm", "chrome"), parseAliasDefinition("alias gm   =   chrome"))
    }

    @Test
    fun `name must be a single word — no space between it and the equals sign is required`() {
        assertEquals(AliasDefinitionAttempt("gm", "chrome"), parseAliasDefinition("alias gm=chrome"))
    }

    @Test
    fun `alias names can never start with a colon — that namespace belongs to reserved commands`() {
        assertNull(parseAliasDefinition("alias :apps = chrome"))
    }

    @Test
    fun `not an alias definition at all — no leading alias-space token`() {
        assertNull(parseAliasDefinition("gm = chrome"))
        assertNull(parseAliasDefinition("chrome"))
        assertNull(parseAliasDefinition(""))
    }

    @Test
    fun `malformed — missing the equals sign`() {
        assertNull(parseAliasDefinition("alias gm chrome"))
    }

    @Test
    fun `malformed — empty command after the equals sign`() {
        assertNull(parseAliasDefinition("alias gm = "))
        assertNull(parseAliasDefinition("alias gm ="))
    }

    @Test
    fun `malformed — empty name before the equals sign`() {
        assertNull(parseAliasDefinition("alias  = chrome"))
    }

    // --- conflict evaluation -----------------------------------------------------------------

    @Test
    fun `defining a genuinely new name is Created`() {
        assertEquals(
            DefineAliasResult.Created("gm", "chrome"),
            evaluateAliasDefinition("gm", "chrome", existingAliases = emptyMap()),
        )
    }

    @Test
    fun `redefining an existing name is Overwritten, not blocked`() {
        assertEquals(
            DefineAliasResult.Overwritten("gm", "firefox", previousCommand = "chrome"),
            evaluateAliasDefinition("gm", "firefox", existingAliases = mapOf("gm" to "chrome")),
        )
    }

    @Test
    fun `an alias cannot point at itself, even brand new`() {
        assertEquals(
            DefineAliasResult.RejectedSelfReference("gm"),
            evaluateAliasDefinition("gm", "gm", existingAliases = emptyMap()),
        )
    }

    @Test
    fun `an alias cannot point at another existing alias — the direct case`() {
        assertEquals(
            DefineAliasResult.RejectedPointsAtAlias("b", targetAliasName = "a"),
            evaluateAliasDefinition("b", "a", existingAliases = mapOf("a" to "chrome")),
        )
    }

    @Test
    fun `the classic two-alias cycle is rejected on whichever definition completes it`() {
        // "a" defined first, pointing at literal text "b" — allowed, "b" isn't an alias yet.
        val afterFirstDefinition = mapOf("a" to "b")
        // Now closing the loop: "b" pointing at existing alias "a" — this is where it must fail.
        assertEquals(
            DefineAliasResult.RejectedPointsAtAlias("b", targetAliasName = "a"),
            evaluateAliasDefinition("b", "a", existingAliases = afterFirstDefinition),
        )
    }

    @Test
    fun `command text that merely happens to match a non-alias word is not flagged`() {
        // "b" is just a word here, not an alias — nothing wrong with another alias' command
        // text also being "b".
        assertEquals(
            DefineAliasResult.Created("c", "b"),
            evaluateAliasDefinition("c", "b", existingAliases = mapOf("a" to "something else")),
        )
    }

    @Test
    fun `command is trimmed before every comparison`() {
        assertEquals(
            DefineAliasResult.RejectedSelfReference("gm"),
            evaluateAliasDefinition("gm", "  gm  ", existingAliases = emptyMap()),
        )
    }

    // --- describing the outcome for the transcript -------------------------------------------

    @Test
    fun `describes a fresh alias`() {
        assertEquals(
            "alias 'gm' created",
            describeDefineAliasResult(DefineAliasResult.Created("gm", "chrome")),
        )
    }

    @Test
    fun `describes an overwrite, naming what it used to point at`() {
        assertEquals(
            "alias 'gm' updated (was: chrome)",
            describeDefineAliasResult(DefineAliasResult.Overwritten("gm", "firefox", previousCommand = "chrome")),
        )
    }

    @Test
    fun `describes a self-reference rejection`() {
        assertEquals(
            "can't alias 'gm' to itself",
            describeDefineAliasResult(DefineAliasResult.RejectedSelfReference("gm")),
        )
    }

    @Test
    fun `describes a points-at-alias rejection, naming the target`() {
        assertEquals(
            "can't alias 'b' to 'a' — aliases can't point at other aliases",
            describeDefineAliasResult(DefineAliasResult.RejectedPointsAtAlias("b", targetAliasName = "a")),
        )
    }
}
