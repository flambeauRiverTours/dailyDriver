package com.example.dailydriver.command

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
}
