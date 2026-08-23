package com.flambeauriver.dailydriver.command

import com.flambeauriver.dailydriver.alias.DefineAliasResult
import com.flambeauriver.dailydriver.apps.AppEntry
import com.flambeauriver.dailydriver.notes.Note
import com.flambeauriver.dailydriver.notes.NoteType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * T178 — what each command actually does.
 *
 * **None of this was testable before.** Every command's behaviour lived in `ShellScreen`'s Enter
 * handler, inside a composable, with no seam a unit test could reach; `ShellViewModel.onSubmit`
 * held twelve branches of which eleven were a bare `return`. So the launcher's entire command
 * surface — the thing the app *is* — had no coverage at all.
 *
 * `runBlocking` rather than a coroutines-test dispatcher, deliberately: dispatch suspends but
 * never delays, so there is nothing to control, and this needs no new dependency.
 */
class CommandDispatchTest {

    private fun app(pkg: String, label: String) = AppEntry(packageName = pkg, label = label)

    private fun note(id: String, title: String) =
        Note(
            id = id,
            type = NoteType.NOTE,
            title = title,
            body = "b",
            createdAt = 0L,
            updatedAt = 0L,
            sourceDevice = "test",
        )

    private val apps = listOf(
        app("com.android.chrome", "Chrome"),
        app("com.android.calculator2", "Calculator"),
        app("com.android.camera", "Camera"),
    )

    private fun dispatch(
        input: String,
        env: FakeCommandEnvironment,
        today: LocalDate = LocalDate.of(2026, 8, 23),
    ) = runBlocking { dispatchCommand(input, env, today) }

    // --- the cascade -------------------------------------------------------------------------------

    @Test
    fun `a reserved command wins outright over any fuzzy match`() {
        // `:apps` is exact; nothing about it may reach T1.
        val env = FakeCommandEnvironment(apps = apps)

        dispatch(":apps", env)

        assertTrue(env.launchedApps.isEmpty())
    }

    @Test
    fun `an alias beats a fuzzy app match`() {
        // ⚠️ The cascade's order, and the reason it matters: "cam" is an alias here *and* fuzzy-
        // matches Camera. T0 must win, or defining an alias that resembles an app name would
        // silently stop working.
        val env = FakeCommandEnvironment(apps = apps, aliases = mapOf("cam" to ":settings"))

        dispatch("cam", env)

        assertEquals(1, env.settingsOpened)
        assertTrue(env.launchedApps.isEmpty())
    }

    @Test
    fun `a definition is never read as a use of the alias it defines`() {
        val env = FakeCommandEnvironment(aliases = mapOf("a" to ":settings"))

        dispatch("alias a = :apps", env)

        assertEquals(listOf("a" to ":apps"), env.definedAliases)
        assertEquals(0, env.settingsOpened)
    }

    @Test
    fun `an alias expands and its expansion is dispatched, not just pasted`() {
        val env = FakeCommandEnvironment(apps = apps, aliases = mapOf("c" to "chrome"))

        dispatch("c", env)

        assertEquals(listOf("com.android.chrome"), env.launchedApps)
    }

    @Test
    fun `an alias carries its argument`() {
        // T173.
        val env = FakeCommandEnvironment(apps = apps, aliases = mapOf("a" to ":apps"))

        dispatch("a chrome", env)

        assertEquals(listOf("com.android.chrome"), env.launchedApps)
    }

    @Test
    fun `bare input falls to T1 and launches the best match`() {
        val env = FakeCommandEnvironment(apps = apps)

        val outcome = dispatch("chr", env)

        assertEquals(listOf("com.android.chrome"), env.launchedApps)
        assertNull(outcome.transcript)
    }

    @Test
    fun `unmatched input falls to T3 and says so`() {
        val outcome = dispatch("zzzznotanapp", FakeCommandEnvironment(apps = apps))

        assertTrue(outcome.transcript!!.contains(":help"))
    }

    @Test
    fun `blank input does nothing at all`() {
        val env = FakeCommandEnvironment(apps = apps)

        assertNull(dispatch("   ", env).transcript)
        assertTrue(env.launchedApps.isEmpty())
    }

    // --- browse views keep the prompt --------------------------------------------------------------

    @Test
    fun `apps launches only when the filter reaches exactly one`() {
        val env = FakeCommandEnvironment(apps = apps)

        val several = dispatch(":apps ca", env)
        assertTrue(env.launchedApps.isEmpty())
        assertTrue("a browse view must keep its filter", !several.clearsInput)

        val one = dispatch(":apps chr", env)
        assertEquals(listOf("com.android.chrome"), env.launchedApps)
        assertTrue("launching hands off, so the prompt clears", one.clearsInput)
    }

    @Test
    fun `apps can be filtered by a disambiguated name`() {
        // T167 — Enter must reach the same app the list shows, suffix and all. Dropping the
        // display names here would undo that fix for Enter while leaving the list looking right.
        val env = FakeCommandEnvironment(
            apps = listOf(
                app("com.google.android.apps.messaging", "Messages"),
                app("com.samsung.android.messaging", "Messages"),
            ),
        )

        dispatch(":apps Messages (Google)", env)

        assertEquals(listOf("com.google.android.apps.messaging"), env.launchedApps)
    }

    @Test
    fun `notes opens only when the query reaches exactly one`() {
        val env = FakeCommandEnvironment(notes = listOf(note("1", "a"), note("2", "b")))
        assertTrue(env.openedNotes.isEmpty())
        assertTrue(!dispatch(":notes x", env).clearsInput)

        env.notes = listOf(note("1", "standup"))
        dispatch(":notes standup", env)
        assertEquals(listOf("1"), env.openedNotes.map { it.id })
    }

    @Test
    fun `bare notes never opens anything, however few notes exist`() {
        // The blank-query guard: `:notes` on a device with one note would otherwise open it the
        // instant you finished typing the command, when what you asked for was to browse.
        val env = FakeCommandEnvironment(notes = listOf(note("1", "only")))

        dispatch(":notes", env)

        assertTrue(env.openedNotes.isEmpty())
    }

    @Test
    fun `task lists keep the prompt`() {
        assertTrue(!dispatch(":tasks", FakeCommandEnvironment()).clearsInput)
        assertTrue(!dispatch(":tasksdone", FakeCommandEnvironment()).clearsInput)
    }

    // --- commands that act -------------------------------------------------------------------------

    @Test
    fun `note opens the editor with its argument as the title`() {
        val env = FakeCommandEnvironment()

        val outcome = dispatch(":note standup", env)

        assertEquals(listOf("standup"), env.openedEditors)
        // The editor appearing is the response; a `:note` line would be scrollback noise.
        assertNull(outcome.transcript)
    }

    @Test
    fun `settings hands off`() {
        val env = FakeCommandEnvironment()

        dispatch(":settings", env)

        assertEquals(1, env.settingsOpened)
    }

    @Test
    fun `notif deep-links only when access is missing`() {
        val granted = FakeCommandEnvironment(hasNotificationAccess = true)
        dispatch(":notif", granted)
        assertEquals(0, granted.notificationAccessOpened)

        val denied = FakeCommandEnvironment(hasNotificationAccess = false)
        dispatch(":notif", denied)
        assertEquals(1, denied.notificationAccessOpened)
    }

    @Test
    fun `alias lists what is defined`() {
        val env = FakeCommandEnvironment(aliases = mapOf("gm" to "chrome"))

        assertTrue(dispatch(":alias", env).transcript!!.contains("gm = chrome"))
    }

    @Test
    fun `help prints the generated listing`() {
        assertEquals(helpText(), dispatch(":help", FakeCommandEnvironment()).transcript)
        assertEquals(helpText(), dispatch("?", FakeCommandEnvironment()).transcript)
    }

    // --- :task -------------------------------------------------------------------------------------

    @Test
    fun `task resolves a date shortcut before storing it`() {
        // T153: what reaches the body is always a literal ISO date, and the echo confirms the
        // resolved day — the only feedback that `@tomorrow` was understood at all.
        val env = FakeCommandEnvironment()

        val outcome = dispatch(":task buy milk @tomorrow", env)

        assertEquals(listOf("buy milk" to LocalDate.of(2026, 8, 24)), env.addedTasks)
        assertTrue(outcome.transcript!!.contains("buy milk"))
        assertTrue(outcome.transcript!!.contains("24"))
    }

    @Test
    fun `an undated task is due today`() {
        val env = FakeCommandEnvironment()

        dispatch(":task buy milk", env)

        assertEquals(listOf("buy milk" to LocalDate.of(2026, 8, 23)), env.addedTasks)
    }

    @Test
    fun `an empty task says so rather than creating a blank one`() {
        val env = FakeCommandEnvironment()

        assertTrue(dispatch(":task", env).transcript!!.contains("nothing to add"))
        assertTrue(env.addedTasks.isEmpty())
    }

    // --- :google -----------------------------------------------------------------------------------

    @Test
    fun `google encodes the query and reports it back`() {
        val env = FakeCommandEnvironment()

        val outcome = dispatch(":google flat white", env)

        assertEquals(listOf("https://www.google.com/search?q=flat+white"), env.openedUrls)
        assertEquals("searched: flat white", outcome.transcript)
    }

    @Test
    fun `google with no query opens nothing`() {
        val env = FakeCommandEnvironment()

        assertTrue(dispatch(":google", env).transcript!!.contains("nothing to search"))
        assertTrue(env.openedUrls.isEmpty())
    }

    @Test
    fun `google says so when there is no browser`() {
        val env = FakeCommandEnvironment(browserAvailable = false)

        assertTrue(dispatch(":google x", env).transcript!!.contains("no browser"))
    }

    // --- :export -----------------------------------------------------------------------------------

    @Test
    fun `export reports where the file went`() {
        val env = FakeCommandEnvironment(
            exportResult = CommandLogExportResult.Written("Download/log.jsonl", CommandLogStats(3, 9, 1)),
        )

        val outcome = dispatch(":export", env)

        assertEquals(1, env.exportsRequested)
        assertTrue(outcome.transcript!!.contains("Download/log.jsonl"))
        assertTrue(outcome.transcript!!.contains("3 inputs"))
    }

    @Test
    fun `a failed export is reported rather than swallowed`() {
        // Deliberately inverting CommandLogRepository.record's rule: writing the log must never
        // cost an app launch, but exporting *is* the command, and one that silently did nothing is
        // the exact failure T166 exists to fix.
        val env = FakeCommandEnvironment(exportResult = CommandLogExportResult.Failed("disk full"))

        assertTrue(dispatch(":export", env).transcript!!.contains("disk full"))
    }

    // --- alias definitions -------------------------------------------------------------------------

    @Test
    fun `defining an alias reports whatever the repository decided`() {
        val env = FakeCommandEnvironment(aliasResult = DefineAliasResult.RejectedSelfReference("gm"))

        assertTrue(dispatch("alias gm = gm", env).transcript!!.contains("itself"))
    }

    // --- the T014 log ------------------------------------------------------------------------------

    @Test
    fun `resolution classifies a reserved command by its token, not the whole line`() {
        // Load-bearing for the training set: `:note standup` and `:note groceries` are the same
        // command with different slots, and collapsing them is what makes the log a frequency
        // table rather than a list of every note title ever created.
        val env = FakeCommandEnvironment(apps = apps)

        val resolution = resolutionOf(":note standup", env)

        assertEquals(CommandResolution.Tier.T0, resolution.tier)
        assertEquals(":note", resolution.target)
    }

    @Test
    fun `resolution reports an alias as T0 and an app as T1`() {
        val env = FakeCommandEnvironment(apps = apps, aliases = mapOf("c" to "chrome"))

        assertEquals(CommandResolution.Tier.T0, resolutionOf("c", env).tier)
        assertEquals(CommandResolution.Tier.T1, resolutionOf("chr", env).tier)
        assertEquals("com.android.chrome", resolutionOf("chr", env).target)
    }

    @Test
    fun `an alias definition is not a training example`() {
        // ⚠️ Regression guard. T178 dropped this exclusion when logging moved, and the first
        // export afterwards showed `alias a = :apps` filed as a T3 *miss* — teaching the set that
        // a line which worked perfectly was unresolvable. Configuration is not natural language
        // anyone will later phrase at the prompt.
        assertTrue(!isTrainingExample("alias sp = spotify"))
        assertTrue(!isTrainingExample("   "))
        assertTrue(isTrainingExample(":apps"))
        assertTrue(isTrainingExample("chr"))
    }

    @Test
    fun `resolution reports an unmatched line as T3`() {
        val resolution = resolutionOf("zzzznotanapp", FakeCommandEnvironment(apps = apps))

        assertEquals(CommandResolution.Tier.T3, resolution.tier)
        assertEquals(CommandResolution.UNRESOLVED, resolution.target)
    }
}
