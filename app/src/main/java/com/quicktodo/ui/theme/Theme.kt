package com.quicktodo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Apple Reminders-inspired clean palette
val Blue = Color(0xFF007AFF)
val Green = Color(0xFF34C759)
val Red = Color(0xFFFF3B30)
val Orange = Color(0xFFFF9500)
val Yellow = Color(0xFFFFCC00)

val Background = Color(0xFFF5F5F5)
val Surface = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFF1C1C1E)
val TextSecondary = Color(0xFF8E8E93)
val Divider = Color(0xFFE5E5EA)
val CheckedColor = Color(0xFF34C759)

val DarkBackground = Color(0xFF1C1C1E)
val DarkSurface = Color(0xFF2C2C2E)
val DarkTextPrimary = Color(0xFFF5F5F5)
val DarkTextSecondary = Color(0xFF8E8E93)

private val LightColorScheme = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    secondary = Green,
    onSecondary = Color.White,
    background = Background,
    surface = Surface,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = Divider,
    surfaceVariant = Background,
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    secondary = Color(0xFF30D158),
    onSecondary = Color.White,
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = DarkTextPrimary,
    onSurface = DarkTextPrimary,
    outline = Color(0xFF38383A),
    surfaceVariant = DarkSurface,
)

@Composable
fun QuickTodoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
