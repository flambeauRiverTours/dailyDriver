package com.example.dailydriver

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dailydriver.alias.AliasRepository
import com.example.dailydriver.apps.AppIndexRepository
import com.example.dailydriver.apps.AppLauncher
import com.example.dailydriver.editor.NoteEditorViewModel
import com.example.dailydriver.notes.NotesRepository
import com.example.dailydriver.notes.parseNoteQuery
import com.example.dailydriver.notifications.NotificationRepository
import com.example.dailydriver.settings.SettingsLauncher
import com.example.dailydriver.shell.ShellScreen
import com.example.dailydriver.shell.ShellViewModel
import com.example.dailydriver.ui.theme.DailyDriverTheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    // Constructed in onCreate, not as a property initializer — `this` isn't a valid Context yet
    // at property-initializer time (attachBaseContext runs after the object is constructed).
    private lateinit var appIndexRepository: AppIndexRepository
    private lateinit var appLauncher: AppLauncher
    private lateinit var settingsLauncher: SettingsLauncher
    private lateinit var aliasRepository: AliasRepository
    private lateinit var notificationRepository: NotificationRepository
    private lateinit var notesRepository: NotesRepository

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
        appLauncher = AppLauncher(this)
        settingsLauncher = SettingsLauncher(this)
        notificationRepository = NotificationRepository(this)
        notesRepository = NotesRepository(this)
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
                val viewModel: ShellViewModel = viewModel()
                val editor: NoteEditorViewModel =
                    viewModel(factory = NoteEditorViewModel.factory(notesRepository))
                ShellScreen(
                    transcript = viewModel.transcript,
                    input = viewModel.input,
                    onInputChange = viewModel::onInputChange,
                    onSubmit = viewModel::onSubmit,
                    reservedCommand = viewModel.reservedCommand,
                    // Reading a plain (non-State) property here means this only picks up a
                    // background refresh (package install/uninstall) the next time something else
                    // triggers recomposition of this scope — typing does, so in practice this is
                    // never stale for more than a moment while actually using :apps. Sitting idle
                    // at the plain prompt when a package changes could show stale data until the
                    // next keystroke or transcript update. Acceptable for T012; revisit if it's
                    // ever actually noticeable.
                    apps = appIndexRepository.apps,
                    onAppClick = { app ->
                        appLauncher.launch(app.packageName)
                        // Leave apps mode on the way out — see ShellViewModel.onAppLaunched.
                        viewModel.onAppLaunched()
                    },
                    onSettingsCommand = {
                        settingsLauncher.open()
                        // Same reasoning as onAppClick above — see ShellViewModel.onSettingsLaunched.
                        viewModel.onSettingsLaunched()
                    },
                    // Same non-reactive-getter caveat as `apps` above — refreshed by
                    // AliasRepository.define, picked up on the next recomposition, which the
                    // transcript update after a definition reliably triggers.
                    aliases = aliasRepository.aliases,
                    onAliasListRequested = viewModel::onAliasListRequested,
                    onAliasExpanded = viewModel::onAliasExpanded,
                    onDefineAlias = { input, name, command ->
                        val result = aliasRepository.define(name, command)
                        viewModel.onAliasDefined(input, result)
                    },
                    // Same non-reactive-getter caveat as `apps`/`aliases` above — both properties
                    // are read fresh on each recomposition, not backed by State/Flow.
                    notifications = notificationRepository.notifications,
                    hasNotificationAccess = notificationRepository.hasAccess(),
                    onNotificationClick = { notification ->
                        notificationRepository.open(notification.key)
                        viewModel.onNotificationOpened()
                    },
                    onNotifAccessSettings = {
                        notificationRepository.openNotificationAccessSettings()
                        viewModel.onNotifAccessSettingsOpened()
                    },
                    // Passed as a lambda rather than a value on purpose — StatusLine polls it on
                    // its own tick, so the dot updates without waiting for this scope to
                    // recompose. See StatusLine's doc comment.
                    hasUnreadNotifications = { notificationRepository.hasUnreadNotifications },
                    editor = editor,
                    // The editor has taken over the bottom of the screen, so ":note ..." should
                    // not still be sitting on the prompt behind it.
                    onNoteEditorOpened = viewModel::onNoteEditorOpened,
                    // Query string in, live results out. Parsing happens here rather than in the
                    // composable so ShellScreen never has to know a tag filter and a text search
                    // are different queries.
                    observeNotes = { query ->
                        notesRepository.observe(parseNoteQuery(query))
                    },
                )
            }
        }
    }

    /**
     * The system bars come back whenever the window loses focus — returning from another app, or
     * dismissing the notification shade — so re-hiding on focus is what makes immersive mode
     * stick rather than being a one-shot at launch.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
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
