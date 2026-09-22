package com.flambeauriver.dailydriver

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flambeauriver.dailydriver.alias.AliasRepository
import com.flambeauriver.dailydriver.alias.resolveAliasTarget
import com.flambeauriver.dailydriver.command.CommandLogExporter
import com.flambeauriver.dailydriver.command.CommandLogRepository
import com.flambeauriver.dailydriver.apps.AppIndexRepository
import com.flambeauriver.dailydriver.editor.NoteEditorViewModel
import com.flambeauriver.dailydriver.notes.NotesRepository
import com.flambeauriver.dailydriver.notes.parseChecklist
import com.flambeauriver.dailydriver.notes.resolveDueInText
import com.flambeauriver.dailydriver.shell.shortDate
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import com.flambeauriver.dailydriver.notes.parseNoteQuery
import com.flambeauriver.dailydriver.notifications.NotificationRepository
import com.flambeauriver.dailydriver.search.googleSearchUrl
import com.flambeauriver.dailydriver.shell.IntentLauncher
import com.flambeauriver.dailydriver.shell.ShellScreen
import com.flambeauriver.dailydriver.shell.ShellViewModel
import androidx.compose.runtime.remember
import com.flambeauriver.dailydriver.command.CommandEnvironment
import com.flambeauriver.dailydriver.command.CommandUsageDao
import com.flambeauriver.dailydriver.data.DatabaseProvider
import com.flambeauriver.dailydriver.command.CommandResolution
import com.flambeauriver.dailydriver.notes.Note
import com.flambeauriver.dailydriver.notes.NoteQuery
import kotlinx.coroutines.flow.first
import com.flambeauriver.dailydriver.ui.theme.DailyDriverTheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    // Constructed in onCreate, not as a property initializer — `this` isn't a valid Context yet
    // at property-initializer time (attachBaseContext runs after the object is constructed).
    private lateinit var commandLog: CommandLogRepository
    private lateinit var commandUsage: CommandUsageDao
    private lateinit var commandLogExporter: CommandLogExporter
    private lateinit var appIndexRepository: AppIndexRepository
    private lateinit var launcher: IntentLauncher
    private lateinit var aliasRepository: AliasRepository
    private lateinit var notificationRepository: NotificationRepository
    private lateinit var notesRepository: NotesRepository

    // Bumped on every window-focus regain (T015) — a Compose State so ShellScreen's focus
    // LaunchedEffect can key off it and re-request focus on the input line. Covers the HOME
    // button/gesture, but also BACK-from-another-app and dismissing the notification shade —
    // all of them are "you're back at the launcher" and should behave the same way.
    private var focusRegainedSignal by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Forces this app's own night-mode Configuration flag on, independent of the system-wide
        // light/dark setting — well-behaved IMEs (Gboard included) key their own keyboard color
        // scheme off the focused app's flag, not off its Compose theme, which is otherwise
        // invisible to a separate process. Must be set before super.onCreate() so the theme
        // resolves to its dark variant on first creation rather than triggering a recreate.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        enterImmersiveMode()

        appIndexRepository = AppIndexRepository(this)
        launcher = IntentLauncher(this)
        notificationRepository = NotificationRepository(this)
        notesRepository = NotesRepository(this)
        commandLog = CommandLogRepository(this)
        commandUsage = DatabaseProvider.get(this).commandUsageDao()
        commandLogExporter = CommandLogExporter(this)
        // onCreate/onDestroy, not onStart/onStop: unlike a typical app, this Activity IS the home
        // screen — it gets paused/resumed on every app switch but rarely destroyed, so pairing
        // registration with onStart/onStop would mean constant re-registration and re-querying
        // PackageManager for a broadcast that doesn't need to be "fresh per visit," just
        // eventually consistent.
        appIndexRepository.register()

        aliasRepository = AliasRepository(this)
        lifecycleScope.launch { aliasRepository.refresh() }

        setContent {
            DailyDriverTheme {
                val editor: NoteEditorViewModel =
                    viewModel(factory = NoteEditorViewModel.factory(notesRepository))
                // Built here rather than in onCreate because it closes over the editor, which is
                // itself a ViewModel and only exists inside the composition (T178).
                val environment = remember(editor) { commandEnvironment(editor) }
                val viewModel: ShellViewModel =
                    viewModel(factory = ShellViewModel.factory(environment))

                ShellScreen(
                    transcript = viewModel.transcript,
                    input = viewModel.input,
                    onInputChange = viewModel::onInputChange,
                    onSubmit = viewModel::onSubmit,
                    onResolved = viewModel::onResolved,
                    onHandedOff = viewModel::onHandedOff,
                    reservedCommand = viewModel.reservedCommand,
                    // Reading a plain (non-State) property here means this only picks up a
                    // background refresh (package install/uninstall) the next time something else
                    // triggers recomposition of this scope — typing does, so in practice this is
                    // never visibly stale.
                    apps = appIndexRepository.apps,
                    onAppClick = { app ->
                        launcher.launchApp(app.packageName)
                        // Leave apps mode on the way out: were the input left at `:apps chrome`,
                        // returning to the launcher would re-trigger auto-launch on the still-
                        // unique match and bounce straight back out.
                        viewModel.onHandedOff()
                    },
                    // Same non-reactive-getter caveat as `apps` above.
                    aliases = aliasRepository.aliases,
                    usage = viewModel.usageCounts,
                    notifications = notificationRepository.notifications,
                    hasNotificationAccess = notificationRepository.hasAccess(),
                    onNotificationClick = { notification ->
                        notificationRepository.open(notification.key)
                        viewModel.onHandedOff()
                    },
                    // Passed as a lambda rather than a value on purpose — StatusLine polls it on
                    // its own tick, so the dot updates without waiting for this scope to
                    // recompose. See StatusLine's doc comment.
                    hasUnreadNotifications = { notificationRepository.hasUnreadNotifications },
                    editor = editor,
                    // Query string in, live results out. Parsing happens here so ShellScreen never
                    // has to know a tag filter and a text search are different queries.
                    observeNotes = { query -> notesRepository.observe(parseNoteQuery(query)) },
                    observeTasks = notesRepository::observeTasks,
                    observeCompletedTasks = notesRepository::observeCompletedTasks,
                    onToggleTask = { task ->
                        notesRepository.setTaskChecked(
                            noteId = task.noteId,
                            position = task.position,
                            checked = !task.checked,
                            now = System.currentTimeMillis(),
                        )
                    },
                    onOpenTaskNote = { task -> notesRepository.byId(task.noteId) },
                    focusRegainedSignal = focusRegainedSignal,
                )
            }
        }
    }

    /**
     * The one implementation of [CommandEnvironment] (T178).
     *
     * Everything a command needs beyond shell state, in one place: this Activity already holds the
     * launcher, every repository and the exporter, and [editor] supplies the two capabilities that
     * only exist inside the composition.
     *
     * Before T178 each of these was a separate `ShellScreen` parameter, which is how that
     * composable reached thirty of them and how dispatch ended up living in the UI.
     */
    private fun commandEnvironment(editor: NoteEditorViewModel) = object : CommandEnvironment {

        override val apps get() = appIndexRepository.apps

        override val aliases get() = aliasRepository.aliases

        override val hasNotificationAccess get() = notificationRepository.hasAccess()

        override fun launchApp(packageName: String) = launcher.launchApp(packageName)

        override fun openSettings() = launcher.openSettings()

        override fun openUrl(url: String) = launcher.openUrl(url)

        override fun openNotificationAccess(): Boolean {
            notificationRepository.openNotificationAccessSettings()
            return true
        }

        override fun openEditor(title: String) = editor.open(title)

        override fun openNote(note: Note) = editor.openExisting(note)

        override suspend fun notesMatching(query: NoteQuery) = notesRepository.observe(query).first()

        override suspend fun addTask(text: String, due: LocalDate) {
            notesRepository.addTask(
                text = text,
                due = due,
                now = System.currentTimeMillis(),
                newId = { UUID.randomUUID().toString() },
            )
        }

        override suspend fun defineAlias(name: String, command: String) = aliasRepository.define(
            name = name,
            command = command,
            // Resolved here rather than inside the repository, which owns aliases and has no
            // reason to know about the app index (T162).
            target = resolveAliasTarget(command, appIndexRepository.apps),
        )

        override suspend fun exportCommandLog() =
            commandLogExporter.export(commandLog.all(), LocalDateTime.now())

        override fun recordResolution(input: String, resolution: CommandResolution) {
            // Fire-and-forget on the activity scope: both writes are a by-product of running a
            // command, and making the command wait on a database write would put it on the
            // launcher's fast path, which §4.2 exists to keep clear.
            lifecycleScope.launch {
                val now = System.currentTimeMillis()
                commandLog.record(input, resolution, now)

                // ⚠️ The two writes gate differently, and the difference is the point. The log
                // KEEPS a T3 miss — those are §8's `unknown` examples and the half that cannot be
                // synthesised. The usage index must skip it: `UNRESOLVED` is the empty string, so
                // counting it would build one meaningless row that every unmatched line increments
                // and that nothing can ever rank.
                if (resolution.target.isNotEmpty()) {
                    commandUsage.record(resolution.target, now)
                }
            }
        }

        override suspend fun usageCounts(): Map<String, Int> =
            commandUsage.all().associate { it.target to it.count }
    }

    /**
     * The system bars come back whenever the window loses focus — returning from another app, or
     * dismissing the notification shade — so re-hiding on focus is what makes immersive mode
     * stick rather than being a one-shot at launch.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enterImmersiveMode()
            // Also the backstop for a wrong app index (2026-09-22). Package broadcasts catch
            // every real install and uninstall, but they cannot catch a query that came back
            // short — which happened on the daily-driven device, and stuck, because nothing ever
            // asked again. RefreshGate keeps this from costing anything on a typical HOME press.
            lifecycleScope.launch { appIndexRepository.refreshIfStale() }
            // T015: HOME re-focuses the input. Regaining window focus is the general form of
            // "back at the launcher" — the HOME button/gesture chief among them, but also BACK
            // from an app with an empty back stack, or dismissing the shade — so one signal here
            // covers all of them instead of special-casing the HOME intent alone.
            focusRegainedSignal++
        }
    }

    /**
     * Hides both system bars (T149) — the top one duplicated the status line's own clock, and a
     * launcher built to look like a terminal shouldn't be framed by system chrome.
     *
     * `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`, not a hard hide: swiping from an edge still brings
     * the bars back temporarily, which is what keeps the notification shade and quick settings
     * reachable. Gesture navigation is unaffected — the bars are hidden, not disabled.
     *
     * Display-cutout padding still applies (`safeDrawingPadding` in ShellScreen), so the status
     * row sits below the camera hole-punch rather than under it.
     */
    private fun enterImmersiveMode() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onDestroy() {
        appIndexRepository.unregister()
        super.onDestroy()
    }
}
