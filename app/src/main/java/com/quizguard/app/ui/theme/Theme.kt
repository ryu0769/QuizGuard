package com.quizguard.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Primary = Color(0xFF4CAF50)
private val PrimaryDark = Color(0xFF388E3C)
private val Accent = Color(0xFFFF6F00)

private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = PrimaryDark,
    secondary = Accent,
    onSecondary = Color.White,
    background = Color(0xFFF7F7F7),
    surface = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Primary,
    onPrimary = Color.Black,
    primaryContainer = PrimaryDark,
    secondary = Accent,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E)
)

@Composable
fun QuizGuardTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
