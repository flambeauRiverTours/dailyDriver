package com.flambeauriver.dailydriver.command

import org.junit.Assert.assertEquals
import org.junit.Test

class EscalationTierTest {

    @Test
    fun `single-word input escalates to T3`() {
        assertEquals(EscalationTier.T3, escalationTier("chrome"))
    }

    @Test
    fun `multi-word input escalates to T2 — multi-word is almost always prose`() {
        assertEquals(EscalationTier.T2, escalationTier("call mom tonight"))
    }

    @Test
    fun `internal whitespace of any kind counts, not just a literal space`() {
        assertEquals(EscalationTier.T2, escalationTier("call\tmom"))
        assertEquals(EscalationTier.T2, escalationTier("call\nmom"))
    }

    @Test
    fun `surrounding whitespace alone does not count — only whitespace between real words does`() {
        assertEquals(EscalationTier.T3, escalationTier("  chrome  "))
    }

    @Test
    fun `blank input is T3 by default, though callers never actually act on it`() {
        // ShellViewModel's caller already no-ops on blank input (Transcript.record does too) —
        // this case just needs to not throw or misclassify if ever reached.
        assertEquals(EscalationTier.T3, escalationTier(""))
        assertEquals(EscalationTier.T3, escalationTier("   "))
    }

    // --- the whitespace boundary, in detail -----------------------------------------------------

    @Test
    fun `exactly two words is already prose enough for T2`() {
        // The rule is "contains whitespace", not "contains several words" — two is the threshold.
        assertEquals(EscalationTier.T2, escalationTier("open chrome"))
    }

    @Test
    fun `runs of internal whitespace still count as a single boundary`() {
        assertEquals(EscalationTier.T2, escalationTier("call    mom"))
    }

    @Test
    fun `whitespace on both edges around a single word is still T3`() {
        // Only whitespace *between* real characters escalates — padding is the user's keyboard,
        // not their intent.
        assertEquals(EscalationTier.T3, escalationTier("\t chrome \n"))
    }

    @Test
    fun `a trailing space mid-type does not prematurely escalate to T2`() {
        // Real input shape: the user has typed a word and one space, on their way to a second
        // word. Escalating here would misroute every multi-word command at its halfway point.
        assertEquals(EscalationTier.T3, escalationTier("chrome "))
    }

    @Test
    fun `non-breaking space counts as whitespace, matching Kotlin's own definition`() {
        // Char.isWhitespace() is Unicode-aware, so an IME that emits U+00A0 doesn't silently
        // route prose to T3. Built with Char(0xA0) rather than pasted in as a literal: an
        // invisible character in source is one "helpful" reformat away from becoming an ordinary
        // space and silently testing nothing.
        assertEquals(EscalationTier.T2, escalationTier("call" + Char(0xA0) + "mom"))
    }

    @Test
    fun `punctuation alone does not make a single token into prose`() {
        assertEquals(EscalationTier.T3, escalationTier("e-mail"))
        assertEquals(EscalationTier.T3, escalationTier("com.android.chrome"))
        assertEquals(EscalationTier.T3, escalationTier("what?"))
    }

    @Test
    fun `a single character is T3`() {
        assertEquals(EscalationTier.T3, escalationTier("c"))
    }

    @Test
    fun `a long unbroken token is still T3, however long`() {
        assertEquals(EscalationTier.T3, escalationTier("x".repeat(500)))
    }

    @Test
    fun `non-ASCII single words are T3, and non-ASCII phrases are T2`() {
        assertEquals(EscalationTier.T3, escalationTier("日本語"))
        assertEquals(EscalationTier.T2, escalationTier("напиши заметку"))
    }

    @Test
    fun `classification is pure — repeated calls agree`() {
        repeat(3) {
            assertEquals(EscalationTier.T2, escalationTier("call mom"))
            assertEquals(EscalationTier.T3, escalationTier("chrome"))
        }
    }
}
