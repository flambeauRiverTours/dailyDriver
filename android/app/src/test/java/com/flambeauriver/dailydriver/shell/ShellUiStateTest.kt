package com.flambeauriver.dailydriver.shell

import com.flambeauriver.dailydriver.apps.AppEntry
import com.flambeauriver.dailydriver.apps.displayNames
import com.flambeauriver.dailydriver.command.ReservedCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T179 — the shell's derived state.
 *
 * None of this was reachable from a test before the extraction: it lived inside `ShellScreen` as a
 * run of `remember` blocks. `previewText` and `plainMatchTarget` decide what T1 shows you and what
 * Enter commits to, so this is the coverage that was most conspicuously missing.
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

    private fun state(input: String, aliasMap: Map<String, String> = aliases) =
        shellUiState(input, apps, aliasMap, displayNames(apps))

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
    fun `an alias definition is recognised and is not a plain query`() {
        val ui = state("alias x = Spotify")

        assertEquals("x", ui.aliasDefinition?.name)
        assertEquals("Spotify", ui.aliasDefinition?.command)
        assertTrue(!ui.isPlainQuery)
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

    // --- T1 at the bare prompt ---------------------------------------------------------------------

    @Test
    fun `bare input resolves to the best fuzzy match`() {
        val ui = state("chr")

        assertTrue(ui.isPlainQuery)
        assertEquals("com.android.chrome", ui.plainMatchTarget?.packageName)
        assertEquals("Chrome", ui.previewText)
    }

    @Test
    fun `T1 does not run while a reserved command is active`() {
        // Otherwise `:apps chr` would carry a bare-prompt target as well as a filter target, and
        // Enter would have two answers.
        val ui = state(":apps chr")

        assertTrue(!ui.isPlainQuery)
        assertNull(ui.plainMatchTarget)
        assertNull(ui.previewText)
    }

    @Test
    fun `T1 does not run on an alias name`() {
        // An alias expands; it must not also fuzzy-match. `s` would otherwise hit Spotify by two
        // routes at once.
        val ui = state("s")

        assertTrue(!ui.isPlainQuery)
        assertNull(ui.plainMatchTarget)
    }

    @Test
    fun `an alias previews its expansion rather than an app`() {
        assertEquals("Spotify", state("s").previewText)
        assertEquals(":apps", state("a").previewText)
    }

    @Test
    fun `a definition being typed previews nothing`() {
        assertNull(state("alias x = Spotify").previewText)
    }

    // --- alias expansion ---------------------------------------------------------------------------

    @Test
    fun `an alias expands, with any argument appended`() {
        assertEquals(":apps", state("a").pendingAliasExpansion)
        assertEquals(":apps firefox", state("a firefox").pendingAliasExpansion)
    }

    @Test
    fun `a definition never expands as an alias`() {
        // The two paths must never race — `alias a = x` starts with a token that is itself an
        // alias name in this fixture.
        assertNull(state("alias a = Spotify").pendingAliasExpansion)
    }

    @Test
    fun `input that is not an alias expands to nothing`() {
        assertNull(state("chr").pendingAliasExpansion)
    }

    // --- what a pause acts on ----------------------------------------------------------------------

    @Test
    fun `the pause target is the apps sole match in apps mode, and T1's guess otherwise`() {
        assertEquals("com.android.chrome", state(":apps chr").currentAutoLaunchTarget?.packageName)
        assertEquals("com.android.chrome", state("chr").currentAutoLaunchTarget?.packageName)
    }

    @Test
    fun `there is nothing to act on for empty input`() {
        val ui = state("")

        assertNull(ui.currentAutoLaunchTarget)
        assertNull(ui.pendingAliasExpansion)
        assertNull(ui.reservedCommand)
    }
}
