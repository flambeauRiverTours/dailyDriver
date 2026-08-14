package com.example.dailydriver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dailydriver.shell.ShellScreen
import com.example.dailydriver.shell.ShellViewModel
import com.example.dailydriver.ui.theme.DailyDriverTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Dark-only, no dynamic colour (design doc §4.1). Forced here rather than in the theme
            // itself — stripping the light palette and Material You out of DailyDriverTheme is T005.
            DailyDriverTheme(darkTheme = true, dynamicColor = false) {
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
