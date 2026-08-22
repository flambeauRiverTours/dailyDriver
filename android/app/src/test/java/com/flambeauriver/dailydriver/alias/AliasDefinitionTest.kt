package com.flambeauriver.dailydriver.alias

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

    // --- cycles longer than two ------------------------------------------------------------------
    //
    // evaluateAliasDefinition's doc claims the single "points at an existing alias" check rules
    // out cycles of ANY length, by the argument that the link closing a loop must target
    // something already stored. Only the two-alias case was actually tested, so the claim rested
    // on the argument alone.

    @Test
    fun `a three-alias cycle is rejected on the link that would close it`() {
        // a -> b, b -> c both fine (b and c aren't aliases when each is defined).
        assertEquals(
            DefineAliasResult.Created("a", "b"),
            evaluateAliasDefinition("a", "b", existingAliases = emptyMap()),
        )
        assertEquals(
            DefineAliasResult.Created("b", "c"),
            evaluateAliasDefinition("b", "c", existingAliases = mapOf("a" to "b")),
        )
        // c -> a closes the loop, and "a" is stored, so this is where it must fail.
        assertEquals(
            DefineAliasResult.RejectedPointsAtAlias("c", targetAliasName = "a"),
            evaluateAliasDefinition("c", "a", existingAliases = mapOf("a" to "b", "b" to "c")),
        )
    }

    @Test
    fun `a four-alias cycle is rejected the same way, with no graph traversal involved`() {
        val stored = mapOf("a" to "b", "b" to "c", "c" to "d")

        assertEquals(
            DefineAliasResult.RejectedPointsAtAlias("d", targetAliasName = "a"),
            evaluateAliasDefinition("d", "a", existingAliases = stored),
        )
    }

    @Test
    fun `pointing at any stored alias is rejected, not only the one that would close a loop`() {
        // The check is deliberately blunter than cycle detection: chains are rejected too, which
        // is what makes ShellViewModel's single-substitution expansion safe.
        assertEquals(
            DefineAliasResult.RejectedPointsAtAlias("d", targetAliasName = "b"),
            evaluateAliasDefinition("d", "b", existingAliases = mapOf("a" to "x", "b" to "y")),
        )
    }

    // --- precedence between rejection reasons -----------------------------------------------------

    @Test
    fun `self-reference is reported even when the name already exists`() {
        // Both "overwrite" and "self-reference" apply; the rejection has to win, or redefining
        // an existing alias to itself would be accepted.
        assertEquals(
            DefineAliasResult.RejectedSelfReference("gm"),
            evaluateAliasDefinition("gm", "gm", existingAliases = mapOf("gm" to "chrome")),
        )
    }

    @Test
    fun `pointing at another alias is reported even when the name already exists`() {
        assertEquals(
            DefineAliasResult.RejectedPointsAtAlias("b", targetAliasName = "a"),
            evaluateAliasDefinition("b", "a", existingAliases = mapOf("a" to "chrome", "b" to "firefox")),
        )
    }

    @Test
    fun `an alias may point at its own previous command`() {
        // "gm" used to mean "chrome"; redefining it to "chrome" is a no-op, not a cycle.
        assertEquals(
            DefineAliasResult.Overwritten("gm", "chrome", previousCommand = "chrome"),
            evaluateAliasDefinition("gm", "chrome", existingAliases = mapOf("gm" to "chrome")),
        )
    }

    @Test
    fun `evaluation is case-sensitive on both name and command`() {
        // T0 resolution is an exact map lookup, so "GM" and "gm" really are different aliases.
        assertEquals(
            DefineAliasResult.Created("GM", "chrome"),
            evaluateAliasDefinition("GM", "chrome", existingAliases = mapOf("gm" to "chrome")),
        )
        assertEquals(
            DefineAliasResult.Created("b", "A"),
            evaluateAliasDefinition("b", "A", existingAliases = mapOf("a" to "chrome")),
        )
    }

    @Test
    fun `a command that only differs from an alias name by whitespace is still caught`() {
        assertEquals(
            DefineAliasResult.RejectedPointsAtAlias("b", targetAliasName = "a"),
            evaluateAliasDefinition("b", "  a  ", existingAliases = mapOf("a" to "chrome")),
        )
    }

    // --- more parsing ------------------------------------------------------------------------------

    @Test
    fun `the alias keyword is case-sensitive`() {
        assertNull(parseAliasDefinition("Alias gm = chrome"))
        assertNull(parseAliasDefinition("ALIAS gm = chrome"))
    }

    @Test
    fun `the keyword must be its own word`() {
        assertNull(parseAliasDefinition("aliases gm = chrome"))
        assertNull(parseAliasDefinition("aliasgm = chrome"))
    }

    @Test
    fun `leading whitespace defeats the parse, matching how reserved commands behave`() {
        assertNull(parseAliasDefinition("  alias gm = chrome"))
    }

    @Test
    fun `tabs work as separators wherever whitespace is allowed`() {
        assertEquals(
            AliasDefinitionAttempt("gm", "chrome"),
            parseAliasDefinition("alias\tgm\t=\tchrome"),
        )
    }

    @Test
    fun `the command keeps its internal spacing and trailing content`() {
        assertEquals(
            AliasDefinitionAttempt("n", "note my  standup notes"),
            parseAliasDefinition("alias n = note my  standup notes   "),
        )
    }

    @Test
    fun `a command may itself contain an equals sign`() {
        // Only the last equals before a non-space run separates name from command, so a command
        // carrying its own "=" survives intact.
        assertEquals(
            AliasDefinitionAttempt("q", "search q=kotlin"),
            parseAliasDefinition("alias q = search q=kotlin"),
        )
    }

    @Test
    fun `a name containing an equals sign is accepted — a known wart, documented not fixed`() {
        // The greedy \S+ backtracks to the LAST equals in an unspaced run, so "alias a=b=c"
        // yields the name "a=b". Harmless (you'd have to type it deliberately) and tightening
        // the pattern risks the main path, so it's recorded here rather than changed.
        assertEquals(AliasDefinitionAttempt("a=b", "c"), parseAliasDefinition("alias a=b=c"))
    }

    @Test
    fun `a multi-line input is not a definition`() {
        assertNull(parseAliasDefinition("alias gm = chrome\nrm -rf"))
    }

    @Test
    fun `a command of only whitespace is rejected, not stored blank`() {
        assertNull(parseAliasDefinition("alias gm =    "))
        assertNull(parseAliasDefinition("alias gm =\t"))
    }

    @Test
    fun `non-ASCII names and commands are accepted`() {
        assertEquals(
            AliasDefinitionAttempt("café", "日本語辞書"),
            parseAliasDefinition("alias café = 日本語辞書"),
        )
    }

    @Test
    fun `parsing is pure — repeated calls agree`() {
        repeat(3) {
            assertEquals(AliasDefinitionAttempt("gm", "chrome"), parseAliasDefinition("alias gm = chrome"))
        }
    }

    @Test
    fun `a parsed definition feeds straight into evaluation`() {
        // The real call path: ShellScreen parses, then AliasRepository evaluates what it got.
        val attempt = parseAliasDefinition("alias gm = chrome")!!

        assertEquals(
            DefineAliasResult.Created("gm", "chrome"),
            evaluateAliasDefinition(attempt.name, attempt.command, existingAliases = emptyMap()),
        )
    }
}
