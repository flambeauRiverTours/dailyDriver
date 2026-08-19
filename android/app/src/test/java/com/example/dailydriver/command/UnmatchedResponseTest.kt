package com.example.dailydriver.command

import org.junit.Assert.assertEquals
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

        assertTrue(response.contains("natural language"))
        assertTrue(response.contains("not built yet"))
    }

    @Test
    fun `both tiers end with the same actionable pointer, so neither is a dead end`() {
        // Root hard constraint: the launcher stays fully usable even when the model is absent.
        val hint = "type :help or ? for commands"

        assertTrue(unmatchedResponse("x", EscalationTier.T3).endsWith(hint))
        assertTrue(unmatchedResponse("x y", EscalationTier.T2).endsWith(hint))
    }
}
