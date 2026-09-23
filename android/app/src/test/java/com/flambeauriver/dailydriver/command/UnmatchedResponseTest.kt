package com.flambeauriver.dailydriver.command

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnmatchedResponseTest {

    @Test
    fun `T3 names what failed to match, so the transcript says which input it was about`() {
        assertEquals(
            "no match for 'chrmoe' — type :help or ? for commands",
            unmatchedResponse("chrmoe", EscalationTier.T3),
        )
    }

    @Test
    fun `T3 trims the echoed input rather than quoting stray whitespace back`() {
        assertTrue(unmatchedResponse("  chrmoe  ", EscalationTier.T3).contains("'chrmoe'"))
    }

    @Test
    fun `T2 says why prose specifically didn't work, rather than claiming no match`() {
        // "call mom tonight" isn't an app-name typo — telling the user "no match" would misdescribe
        // what happened. The tier distinction T009 established is what makes this possible.
        val response = unmatchedResponse("call mom tonight", EscalationTier.T2)

        // Asserted as an absence, because that is the actual invariant: whatever the wording
        // says, it must not call prose a failed app-name match. The message text itself changed
        // at T036 when the SLM stopped being "not built yet".
        assertFalse(response.contains("no match"))
        assertTrue(response.contains("couldn't turn that into a command"))
    }

    @Test
    fun `both tiers end with the same actionable pointer, so neither is a dead end`() {
        // Root hard constraint: the launcher stays fully usable even when the model is absent.
        val hint = "type :help or ? for commands"

        assertTrue(unmatchedResponse("x", EscalationTier.T3).endsWith(hint))
        assertTrue(unmatchedResponse("x y", EscalationTier.T2).endsWith(hint))
    }

    // --- echoing the input --------------------------------------------------------------------

    @Test
    fun `T2 deliberately does not echo the input back`() {
        // Prose can be a whole sentence; quoting it back adds a transcript line's worth of noise
        // and tells the user nothing they didn't just type. T3's echo earns its place because
        // seeing the exact token is how you spot the typo.
        val prose = "remind me to call the bank tomorrow"

        assertFalse(unmatchedResponse(prose, EscalationTier.T2).contains(prose))
    }

    @Test
    fun `T3 echoes a long input in full rather than truncating it`() {
        // Documents the current choice. If this ever becomes a problem in the transcript, this
        // test is the place that says the behaviour was intentional rather than overlooked.
        val long = "x".repeat(200)

        assertTrue(unmatchedResponse(long, EscalationTier.T3).contains("'$long'"))
    }

    @Test
    fun `T3 echoes an empty input as empty quotes rather than crashing`() {
        // ShellViewModel never reaches here with blank input (Transcript.record drops it), but
        // the function shouldn't depend on that to avoid throwing.
        assertEquals(
            "no match for '' — type :help or ? for commands",
            unmatchedResponse("", EscalationTier.T3),
        )
    }

    @Test
    fun `T3 echoes quotes inside the input without escaping or mangling them`() {
        assertTrue(unmatchedResponse("""don't""", EscalationTier.T3).contains("""'don't'"""))
    }

    @Test
    fun `T3 echoes non-ASCII input unchanged`() {
        assertTrue(unmatchedResponse("日本語", EscalationTier.T3).contains("'日本語'"))
    }

    // --- general shape ------------------------------------------------------------------------

    @Test
    fun `every tier produces a single line — the transcript renders one entry per response`() {
        EscalationTier.entries.forEach { tier ->
            assertFalse("$tier wrapped onto multiple lines", unmatchedResponse("x", tier).contains('\n'))
        }
    }

    @Test
    fun `every tier produces non-blank output`() {
        EscalationTier.entries.forEach { tier ->
            assertTrue("$tier produced blank output", unmatchedResponse("x", tier).isNotBlank())
        }
    }

    @Test
    fun `the two tiers say visibly different things`() {
        // If these ever collapse to the same string, the tier distinction has stopped earning
        // its keep and T009's classifier should be reconsidered rather than left decorative.
        assertNotEquals(
            unmatchedResponse("x", EscalationTier.T2),
            unmatchedResponse("x", EscalationTier.T3),
        )
    }

    @Test
    fun `responses are pure — repeated calls agree`() {
        repeat(3) {
            assertEquals(
                "no match for 'chrmoe' — type :help or ? for commands",
                unmatchedResponse("chrmoe", EscalationTier.T3),
            )
        }
    }
}
