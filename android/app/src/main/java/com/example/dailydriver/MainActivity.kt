package com.example.dailydriver

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dailydriver.shell.ShellScreen
import com.example.dailydriver.shell.ShellViewModel
import com.example.dailydriver.ui.theme.DailyDriverTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Forces this app's own night-mode Configuration flag on, independent of the system-wide
        // light/dark setting — well-behaved IMEs (Gboard included) key their own keyboard color
        // scheme off the focused app's flag, not off its Compose theme, which is otherwise
        // invisible to a separate process. Must be set before super.onCreate() so the theme
        // resolves to its dark variant on first creation rather than triggering a recreate.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DailyDriverTheme {
                val viewModel: ShellViewModel = viewModel()
                ShellScreen(
                    transcript = viewModel.transcript,
                    input = viewModel.input,
                    onInputChange = viewModel::onInputChange,
                    onSubmit = viewModel::onSubmit,
                )
            }
        }
    }
}
