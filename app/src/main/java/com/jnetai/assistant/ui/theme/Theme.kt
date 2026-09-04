package com.jnetai.assistant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val NeonPurple = Color(0xFF7C5CFF)
val NeonPurpleLight = Color(0xFF9D85FF)
val NeonCyan = Color(0xFF00E5FF)
val NeonPink = Color(0xFFFF2D78)
val bgDark = Color(0xFF0B0F14)
val surfaceDark = Color(0xFF141A23)
val surfaceDarkElevated = Color(0xFF1C2530)
val textPrimaryDark = Color(0xFFE8ECF1)
val textSecondaryDark = Color(0xFF9AA7B8)

private val DarkColors = darkColorScheme(
    primary = NeonPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2A2350),
    onPrimaryContainer = NeonPurpleLight,
    secondary = NeonCyan,
    onSecondary = Color(0xFF00363F),
    tertiary = NeonPink,
    background = bgDark,
    onBackground = textPrimaryDark,
    surface = surfaceDark,
    onSurface = textPrimaryDark,
    surfaceVariant = surfaceDarkElevated,
    onSurfaceVariant = textSecondaryDark,
    error = Color(0xFFFF6B6B)
)

private val LightColors = lightColorScheme(
    primary = NeonPurple,
    secondary = Color(0xFF00838F),
    background = Color(0xFFF5F6FA),
    surface = Color.White
)

@Composable
fun JNetAssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content
    )
}