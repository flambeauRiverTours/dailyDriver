package com.example.dailydriver.ui.theme

import androidx.compose.ui.graphics.Color

// Dark-only palette (design doc §4.1 — no light palette exists, no theme switch exists).
// Deliberately no literal pure black (#000000) or pure white (#FFFFFF) anywhere, including for
// values that read as "black"/"white" at a glance — every one is a slightly desaturated
// near-black or near-white tone instead.

val Background = Color(0xFF121212)
val Surface = Color(0xFF1E1E20)
val OnBackground = Color(0xFFE4E1E6)
val OnSurface = Color(0xFFE4E1E6)
val OnSurfaceVariant = Color(0xFF948F99)

val Primary = Color(0xFFD0BCFF)
val Secondary = Color(0xFFCCC2DC)
val Tertiary = Color(0xFFEFB8C8)
