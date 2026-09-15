package com.flambeauriver.dailydriver.shell

import com.flambeauriver.dailydriver.apps.AppEntry
import com.flambeauriver.dailydriver.apps.displayNames
import com.flambeauriver.dailydriver.command.ReservedCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T179 — the shell's derived state, reworked at T184.
 *
 * None of this was reachable from a test before the extraction: it lived inside `ShellScreen` as a
 * run of `remember` blocks. `previewText` and what a pause commits to decide what you see and what
 * happens without Enter, so this is the coverage that was most conspicuously missing.
 */
class ShellUiStateTest {

    private fun app(pkg: String, label: String) = AppEntry(packageName = pkg, label = label)

    private val apps = listOf(
        app("com.spotify.music", "Spotify"),
        app("com.android.chrome", "Chrome"),
        app("com.android.calculator2", "Calculator"),
        app("com.google.android.apps.messaging", "Messages"),
        app("com.samsung.android.messaging", "Messages"),
    )

    private val aliases = mapOf("s" to "Spotify", "a" to ":apps")

    private fun state(
        input: String,
        aliasMap: Map<String, String> = aliases,
        usage: Map<String, Int> = emptyMap(),
    ) = shellUiState(input, apps, aliasMap, displayNames(apps), usage)

    // --- what mode are we in -----------------------------------------------------------------------

    @Test
    fun `a reserved command is recognised and exposed by variant`() {
        val ui = state(":apps chr")

        assertEquals(ReservedCommand.Apps("chr"), ui.reservedCommand)
        assertEquals("chr", ui.appsCommand?.query)
        assertNull(ui.notesCommand)
    }

    @Test
    fun `notif is only a browse mode once access is granted`() {
        // Without access Enter deep-links to the settings page instead, so the browse view must
        // not claim the screen.
        val ui = state(":notif")

        assertNull(ui.notifCommand(hasAccess = false))
        assertEquals(ReservedCommand.Notif, ui.notifCommand(hasAccess = true))
    }

    @Test
    fun `an alias definition is recognised and is not ranked as a query`() {
        val ui = state("alias x = Spotify")

        assertEquals("x", ui.aliasDefinition?.name)
        assertEquals("Spotify", ui.aliasDefinition?.command)
        assertTrue(ui.candidates.isEmpty())
    }

    // --- the apps filter ---------------------------------------------------------------------------

    @Test
    fun `apps filters live and narrows to a sole target`() {
        val ui = state(":apps chr")

        assertEquals(listOf("com.android.chrome"), ui.filteredApps.map { it.packageName })
        assertEquals("com.android.chrome", ui.autoLaunchTarget?.packageName)
    }

    @Test
    fun `a filter matching several apps has no auto-launch target`() {
        // Stricter than the bare-prompt rule below, deliberately: `:apps` is a browse view, and
        // committing to a best-of-several while you are still reading takes the list away.
        // Two apps genuinely share the label "Messages", which is the case T164 exists for.
        val ui = state(":apps mess")

        assertEquals(2, ui.filteredApps.size)
        assertNull(ui.autoLaunchTarget)
    }

    @Test
    fun `bare apps never auto-launches, however few apps are installed`() {
        val ui = shellUiState(":apps", listOf(apps.first()), aliases, displayNames(apps))

        assertNull(ui.autoLaunchTarget)
    }

    @Test
    fun `a disambiguated name is filterable`() {
        // T167 — the name the list renders has to be a name you can type.
        val ui = state(":apps Messages (Google)")

        assertEquals(listOf("com.google.android.apps.messaging"), ui.filteredApps.map { it.packageName })
    }

    // --- the ranked candidate list (T184) ----------------------------------------------------------

    @Test
    fun `bare input resolves to the best fuzzy match`() {
        val ui = state("chr")

        assertEquals("com.android.chrome", ui.topCandidate?.candidate?.target)
        assertEquals("Chrome", ui.previewText)
    }

    @Test
    fun `nothing is ranked while a reserved command is active`() {
        // Otherwise `:apps chr` would carry a bare-prompt candidate as well as a filter target,
        // and Enter would have two answers. The mode already answered the question.
        val ui = state(":apps chr")

        assertTrue(ui.candidates.isEmpty())
        assertNull(ui.topCandidate)
        assertNull(ui.previewText)
    }

    @Test
    fun `an alias outranks the app it shadows rather than suppressing the ranking`() {
        // ⚠️ Changed at T184. `s` used to skip matching entirely because it was an alias key, so
        // the shell could not say what else the input might mean. Now it is one candidate among
        // several and wins on being exact — the T0-before-T1 rule falling out of the ranking
        // (ADR-0010) instead of being imposed by a branch.
        val ui = state("s")

        assertEquals("Spotify", ui.topCandidate?.candidate?.target)
        assertTrue(ui.candidates.size > 1)
    }

    @Test
    fun `an alias previews its expansion rather than its name`() {
        // The preview answers "what will Enter do", so an alias shows the far side of it.
        assertTrue(state("s").previewText!!.startsWith("Spotify"))
        assertTrue(state("a").previewText!!.startsWith(":apps"))
    }

    @Test
    fun `the preview counts every kind of candidate, not just apps`() {
        // ⚠️ Deliberate change. Before T184 the count came from the app matcher alone, so a
        // reserved command or an alias that also answered to the input went unmentioned. The
        // suffix is the only ambiguity signal there is; it has to count what it is ranking.
        assertEquals("Chrome", state("chrome").previewText)
        assertTrue(state("s").previewText!!.contains("more"))
    }

    @Test
    fun `a definition being typed previews nothing`() {
        assertNull(state("alias x = Spotify").previewText)
    }

    @Test
    fun `usage reorders the preview so it still names what Enter would open`() {
        // The promise the preview makes. Both prefix-match, so if the shell ranked without the
        // counts dispatch uses, the dim line would name one app while Enter opened another.
        assertTrue(state("me").previewText!!.startsWith("Messages (Google)"))

        val flipped = state("me", usage = mapOf("com.samsung.android.messaging" to 9))
        assertTrue(flipped.previewText!!.startsWith("Messages (Samsung)"))
        assertEquals("com.samsung.android.messaging", flipped.topCandidate?.candidate?.target)
    }

    @Test
    fun `a disambiguated app is previewed by the name the list shows`() {
        // T164 — a preview reading "Messages" while the list shows two of them would be
        // announcing a choice it is not making.
        assertTrue(state("me").previewText!!.startsWith("Messages ("))
    }

    // --- what a pause acts on (T184's single timer) ------------------------------------------------

    @Test
    fun `the pause target is the apps sole match in apps mode, and the top candidate otherwise`() {
        assertEquals("com.android.chrome", state(":apps chr").pauseTarget)
        assertEquals("com.android.chrome", state("chr").pauseTarget)
    }

    @Test
    fun `an alias commits on a pause, as it did before the timers were merged`() {
        assertEquals("Spotify", state("s").pauseTarget)
        assertEquals(":apps", state("a").pauseTarget)
    }

    @Test
    fun `a reserved command never commits on a pause`() {
        // ⚠️ The rule T184 had to state out loud. Tokens are fuzzy-matchable now, so `:note` is
        // the top candidate while `:notes` is half-typed — and a pause there must not open the
        // editor. Enter commits anything; a pause commits only what is cheap to be wrong about.
        assertNull(state(":not").pauseTarget)
        assertNull(state(":expor").pauseTarget)
    }

    @Test
    fun `a fully typed reserved command does not commit on a pause either`() {
        // It parses as a mode rather than a query, so there is nothing ranked to commit to.
        assertNull(state(":export").pauseTarget)
        assertNull(state(":tasks").pauseTarget)
    }

    @Test
    fun `a definition being typed never commits on a pause`() {
        assertNull(state("alias a = Spotify").pauseTarget)
    }

    @Test
    fun `there is nothing to act on for empty input`() {
        val ui = state("")

        assertNull(ui.pauseTarget)
        assertNull(ui.topCandidate)
        assertNull(ui.reservedCommand)
        assertNull(ui.previewText)
    }
}
