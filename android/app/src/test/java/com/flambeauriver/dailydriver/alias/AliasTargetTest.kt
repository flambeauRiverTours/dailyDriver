package com.flambeauriver.dailydriver.alias

import com.flambeauriver.dailydriver.apps.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** T162 — an alias has to point at something real before it is stored. */
class AliasTargetTest {

    private fun app(pkg: String, label: String) = AppEntry(packageName = pkg, label = label)

    private val apps = listOf(
        app("com.spotify.music", "Spotify"),
        app("com.google.android.apps.messaging", "Messages"),
        app("com.samsung.android.messaging", "Messages"),
        app("com.android.calculator2", "Calculator"),
        app("com.android.camera", "Camera"),
    )

    // --- reserved commands -------------------------------------------------------------------------

    @Test
    fun `a reserved command is a valid target`() {
        assertEquals(AliasTarget.Command, resolveAliasTarget(":apps", apps))
        assertEquals(AliasTarget.Command, resolveAliasTarget(":tasks", apps))
    }

    @Test
    fun `a reserved command with an argument is valid`() {
        // Must not be fuzzy-matched against app names: ":note standup" is exact by construction.
        assertEquals(AliasTarget.Command, resolveAliasTarget(":note standup", apps))
        assertEquals(AliasTarget.Command, resolveAliasTarget(":task buy milk @fri", apps))
    }

    // --- app targets -------------------------------------------------------------------------------

    @Test
    fun `an exact app name resolves to that app`() {
        val target = resolveAliasTarget("Spotify", apps)

        assertTrue(target is AliasTarget.App)
        assertEquals("com.spotify.music", (target as AliasTarget.App).entry.packageName)
    }

    @Test
    fun `an exact match is case insensitive`() {
        assertTrue(resolveAliasTarget("spotify", apps) is AliasTarget.App)
        assertTrue(resolveAliasTarget("SPOTIFY", apps) is AliasTarget.App)
    }

    @Test
    fun `a prefix matching exactly one app is accepted`() {
        // "Only a prefix was typed" is a *cause* of ambiguity, not ambiguity itself. If it narrows
        // to one app there is nothing to ask about.
        val target = resolveAliasTarget("calc", apps)

        assertTrue(target is AliasTarget.App)
        assertEquals("Calculator", (target as AliasTarget.App).entry.label)
    }

    @Test
    fun `the stored command is the app's full label, not what was typed`() {
        // Load-bearing. Aliases re-resolve through T1 on every use, so storing "calc" would leave
        // the alias meaning whatever "calc" matches *later* — installing a better-scoring app
        // would silently repoint an alias the user never touched.
        val target = resolveAliasTarget("calc", apps) as AliasTarget.App

        assertEquals("Calculator", target.resolvedCommand)
    }

    // --- ambiguity ---------------------------------------------------------------------------------

    @Test
    fun `two apps sharing a name are ambiguous`() {
        val target = resolveAliasTarget("Messages", apps)

        assertTrue(target is AliasTarget.Ambiguous)
    }

    @Test
    fun `ambiguous candidates are named with their publisher`() {
        // Listing "Messages, Messages" would be useless. T164's disambiguation is what makes the
        // rejection actionable.
        val target = resolveAliasTarget("Messages", apps) as AliasTarget.Ambiguous

        assertEquals(setOf("Messages (Google)", "Messages (Samsung)"), target.candidates.toSet())
    }

    @Test
    fun `a prefix matching several apps is ambiguous`() {
        val target = resolveAliasTarget("ca", apps)

        assertTrue("expected ambiguity between Calculator and Camera", target is AliasTarget.Ambiguous)
        assertTrue((target as AliasTarget.Ambiguous).candidates.size >= 2)
    }

    @Test
    fun `an exact name wins over the fuzzy ranking`() {
        // "Camera" also fuzzy-matches Calculator; an exact label match must not be dragged into
        // ambiguity by the ranker.
        val target = resolveAliasTarget("Camera", apps)

        assertTrue(target is AliasTarget.App)
        assertEquals("Camera", (target as AliasTarget.App).entry.label)
    }

    // --- nothing at all ----------------------------------------------------------------------------

    @Test
    fun `a target matching nothing is unknown`() {
        assertEquals(AliasTarget.Unknown, resolveAliasTarget("zzzzqqq", apps))
    }

    @Test
    fun `an empty target is unknown`() {
        assertEquals(AliasTarget.Unknown, resolveAliasTarget("", apps))
        assertEquals(AliasTarget.Unknown, resolveAliasTarget("   ", apps))
    }

    // --- how it lands in a definition --------------------------------------------------------------

    @Test
    fun `an unknown target is rejected rather than stored`() {
        val result = evaluateAliasDefinition("z", "zzzzqqq", emptyMap(), resolveAliasTarget("zzzzqqq", apps))

        assertTrue(result is DefineAliasResult.RejectedUnknownTarget)
        assertTrue(describeDefineAliasResult(result).contains("nothing matches"))
    }

    @Test
    fun `an ambiguous target is rejected and names the candidates`() {
        val result = evaluateAliasDefinition("m", "Messages", emptyMap(), resolveAliasTarget("Messages", apps))

        assertTrue(result is DefineAliasResult.RejectedAmbiguousTarget)
        val text = describeDefineAliasResult(result)
        assertTrue(text.contains("Messages (Google)"))
        assertTrue(text.contains("Messages (Samsung)"))
    }

    @Test
    fun `a resolved app is stored under its full label`() {
        val result = evaluateAliasDefinition("c", "calc", emptyMap(), resolveAliasTarget("calc", apps))

        assertEquals(DefineAliasResult.Created("c", "Calculator"), result)
    }

    @Test
    fun `structural rejections still win over target validation`() {
        // `alias a = a` is self-referential whether or not anything called "a" is installed.
        // Reporting it as an unknown target would send you hunting for a missing app, not a typo.
        val selfRef = evaluateAliasDefinition("a", "a", emptyMap(), resolveAliasTarget("a", apps))
        assertTrue(selfRef is DefineAliasResult.RejectedSelfReference)

        val pointsAtAlias = evaluateAliasDefinition(
            "b", "a", mapOf("a" to ":apps"), resolveAliasTarget("a", apps),
        )
        assertTrue(pointsAtAlias is DefineAliasResult.RejectedPointsAtAlias)
    }

    @Test
    fun `existing callers that skip validation are unaffected`() {
        // The target parameter defaults to Command, so an unvalidated definition behaves exactly
        // as it did before T162.
        assertEquals(
            DefineAliasResult.Created("x", ":apps"),
            evaluateAliasDefinition("x", ":apps", emptyMap()),
        )
    }
}
