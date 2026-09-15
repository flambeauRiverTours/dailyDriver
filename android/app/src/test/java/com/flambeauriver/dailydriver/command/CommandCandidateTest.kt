package com.flambeauriver.dailydriver.command

import com.flambeauriver.dailydriver.apps.AppEntry
import com.flambeauriver.dailydriver.apps.MatchStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T182 — one candidate list, one ranking.
 *
 * The two properties worth pinning hardest are at the top: that an exact match anywhere beats a
 * fuzzy match anywhere (which is what preserves T0-before-T1 structurally), and that usage ranks
 * *within* a band rather than across one.
 */
class CommandCandidateTest {

    private fun appCandidate(pkg: String, label: String) =
        Candidate.App(AppEntry(packageName = pkg, label = label), label)

    private val apps = listOf(
        appCandidate("com.android.chrome", "Chrome"),
        appCandidate("com.android.calculator2", "Calculator"),
        appCandidate("com.android.camera", "Camera"),
    )

    private val commands = listOf(Candidate.Command(":apps"), Candidate.Command(":task"))

    private fun rank(
        input: String,
        candidates: List<Candidate> = apps + commands,
        usage: Map<String, Int> = emptyMap(),
    ) = rankCandidates(input, candidates, usage)

    // --- the cascade, now structural rather than special-cased -------------------------------------

    @Test
    fun `an exact alias beats a fuzzy app match`() {
        // ⚠️ The property CommandDispatchTest pins for the old cascade. "cam" is an alias name AND
        // fuzzy-matches Camera. Before T182 an explicit ordering in dispatchCommand made T0 win;
        // now it falls out of exact-pass-before-fuzzy-pass, and this test is what says so.
        val candidates = apps + Candidate.Alias("cam", ":settings")

        val top = rank("cam", candidates).first()

        assertEquals(":settings", top.candidate.target)
        assertEquals(MatchStrategy.EXACT, top.strategy)
    }

    @Test
    fun `an exact command token beats a fuzzy app match`() {
        val candidates = apps + Candidate.Command(":cam")

        assertEquals(":cam", rank(":cam", candidates).first().candidate.target)
    }

    @Test
    fun `an exact app name beats a fuzzy command match`() {
        // The rule has no favoured type — whichever matched exactly wins, regardless of what it is.
        val candidates = listOf(appCandidate("com.task.app", "Task"), Candidate.Command(":task"))

        assertEquals("com.task.app", rank("task", candidates).first().candidate.target)
    }

    // --- usage ranks within a band, never across one -----------------------------------------------

    @Test
    fun `within one band, the most-used candidate wins`() {
        // "ca" prefixes both Calculator and Camera, so both land in PREFIX. Usage decides.
        val ranked = rank("ca", apps, usage = mapOf("com.android.calculator2" to 100))

        assertEquals("com.android.calculator2", ranked.first().candidate.target)
        assertEquals(MatchStrategy.PREFIX, ranked.first().strategy)
    }

    @Test
    fun `usage flips the old coverage-based winner`() {
        // ⚠️ Deliberate behaviour change. Coverage alone used to rank Camera above Calculator for
        // "ca" because its label is shorter. With no usage data that still holds; with usage it
        // does not, and that is the whole point of the change.
        assertEquals("com.android.camera", rank("ca", apps).first().candidate.target)

        val withUsage = rank("ca", apps, usage = mapOf("com.android.calculator2" to 5))
        assertEquals("com.android.calculator2", withUsage.first().candidate.target)
    }

    @Test
    fun `a heavily-used weak match never outranks an unused strong match`() {
        // ⚠️ The guard on decision 1. If usage were a global sort key rather than a within-band
        // one, "Chrome Beta" would win here on 500 uses despite only prefix-matching, and the
        // exact match would lose. That is the failure mode the band ordering exists to prevent.
        val candidates = listOf(
            appCandidate("com.chrome", "Chrome"),
            appCandidate("com.chrome.beta", "Chrome Beta"),
        )

        val ranked = rank("chrome", candidates, usage = mapOf("com.chrome.beta" to 500))

        assertEquals("com.chrome", ranked.first().candidate.target)
        assertEquals(MatchStrategy.EXACT, ranked.first().strategy)
        assertEquals(MatchStrategy.PREFIX, ranked[1].strategy)
    }

    @Test
    fun `ranking is deterministic when band, usage and score all tie`() {
        // Label is the final tiebreak purely so the list never reshuffles under equal candidates,
        // which would make the top row move while you read it.
        val candidates = listOf(appCandidate("com.b", "Same"), appCandidate("com.a", "Same"))

        repeat(3) {
            assertEquals(listOf("Same", "Same"), rank("same", candidates).map { it.candidate.label })
        }
    }

    // --- arguments ---------------------------------------------------------------------------------

    @Test
    fun `a command matches on its token and keeps the rest as an argument`() {
        val top = rank(":task buy milk").first()

        assertEquals(":task", top.candidate.target)
        assertEquals("buy milk", top.argument)
        assertEquals(MatchStrategy.EXACT, top.strategy)
    }

    @Test
    fun `an alias carries its argument too`() {
        // T173 — `a firefox` must reach dispatch as the alias plus "firefox".
        val candidates = listOf(Candidate.Alias("a", ":apps"))

        val top = rank("a firefox", candidates).first()

        assertEquals(":apps", top.candidate.target)
        assertEquals("firefox", top.argument)
    }

    @Test
    fun `an app never takes an argument`() {
        // "Chrome foo" is not a way of saying Chrome, so it must not match at all on the token.
        assertTrue(rank("chrome foo", apps).none { it.candidate.target == "com.android.chrome" })
    }

    @Test
    fun `a bare command has no argument`() {
        assertEquals("", rank(":apps").first().argument)
    }

    @Test
    fun `interior spacing in an argument is preserved`() {
        assertEquals("buy  milk", rank(":task buy  milk").first().argument)
    }

    @Test
    fun `a multi-word alias name is matched whole rather than by its first token`() {
        // Whole-input is tried first precisely so a name containing a space stays reachable —
        // mirroring what expandAlias already does rather than inventing a different rule.
        val candidates = listOf(Candidate.Alias("long name", ":tasks"))

        val top = rank("long name", candidates).first()

        assertEquals(":tasks", top.candidate.target)
        assertEquals("", top.argument)
    }

    // --- edges -------------------------------------------------------------------------------------

    @Test
    fun `blank input matches nothing`() {
        assertTrue(rank("").isEmpty())
        assertTrue(rank("   ").isEmpty())
    }

    @Test
    fun `input matching nothing returns nothing`() {
        assertTrue(rank("zzzzqqq", apps).isEmpty())
    }

    @Test
    fun `an empty candidate list is handled without complaint`() {
        assertTrue(rank("anything", emptyList()).isEmpty())
    }

    @Test
    fun `matching is case-insensitive across candidate types`() {
        assertEquals("com.android.chrome", rank("CHROME", apps).first().candidate.target)
        assertEquals(":apps", rank(":APPS", commands).first().candidate.target)
    }

    @Test
    fun `usage for an absent target is treated as zero rather than failing`() {
        val ranked = rank("chrome", apps, usage = mapOf("com.something.else" to 99))

        assertEquals(0, ranked.first().usage)
    }

    @Test
    fun `a reserved token is now fuzzy-matchable, which it was not before T182`() {
        // ⚠️ Behaviour change worth knowing about: ReservedCommandParser matches tokens exactly,
        // so `:aps` used to match nothing. In one unified list it is a typo like any other, which
        // is almost certainly what you want but is a change rather than a carry-over.
        val top = rank(":aps", commands).first()

        assertEquals(":apps", top.candidate.target)
        assertTrue(top.strategy != MatchStrategy.EXACT)
    }

    @Test
    fun `every candidate type exposes the target the log already records`() {
        // The usage index keys on this, and so does CommandResolution — one identity, not two.
        assertEquals("com.android.chrome", apps[0].target)
        assertEquals(":apps", Candidate.Command(":apps").target)
        assertEquals(":settings", Candidate.Alias("s", ":settings").target)
    }
}
