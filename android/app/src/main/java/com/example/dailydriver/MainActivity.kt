package com.example.dailydriver

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dailydriver.alias.AliasRepository
import com.example.dailydriver.apps.AppIndexRepository
import com.example.dailydriver.apps.AppLauncher
import com.example.dailydriver.shell.ShellScreen
import com.example.dailydriver.shell.ShellViewModel
import com.example.dailydriver.ui.theme.DailyDriverTheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    // Constructed in onCreate, not as a property initializer — `this` isn't a valid Context yet
    // at property-initializer time (attachBaseContext runs after the object is constructed).
    private lateinit var appIndexRepository: AppIndexRepository
    private lateinit var appLauncher: AppLauncher
    private lateinit var aliasRepository: AliasRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        // Forces this app's own night-mode Configuration flag on, independent of the system-wide
        // light/dark setting — well-behaved IMEs (Gboard included) key their own keyboard color
        // scheme off the focused app's flag, not off its Compose theme, which is otherwise
        // invisible to a separate process. Must be set before super.onCreate() so the theme
        // resolves to its dark variant on first creation rather than triggering a recreate.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        appIndexRepository = AppIndexRepository(this)
        appLauncher = AppLauncher(this)
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
                    // Same non-reactive-getter caveat as `apps` above — refreshed by
                    // AliasRepository.define, picked up on the next recomposition, which the
                    // transcript update after a definition reliably triggers.
                    aliases = aliasRepository.aliases,
                    onAliasExpanded = viewModel::onAliasExpanded,
                    onDefineAlias = { input, name, command ->
                        val result = aliasRepository.define(name, command)
                        viewModel.onAliasDefined(input, result)
                    },
                    onAppCandidates = viewModel::onAppCandidates,
                )
            }
        }
    }

    override fun onDestroy() {
        appIndexRepository.unregister()
        super.onDestroy()
    }
}
