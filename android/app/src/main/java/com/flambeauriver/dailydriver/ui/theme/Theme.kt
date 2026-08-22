package com.flambeauriver.dailydriver.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    onSurfaceVariant = OnSurfaceVariant,
    primary = Primary,
    secondary = Secondary,
    tertiary = Tertiary,
)

/**
 * Dark-only, unconditionally (design doc §4.1 — no light palette, no theme switch, no Material
 * You dynamic color). Unlike the Android Studio scaffold this replaced, there is no
 * darkTheme/dynamicColor parameter to flip — there is exactly one theme.
 */
@Composable
fun DailyDriverTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content,
    )
}
