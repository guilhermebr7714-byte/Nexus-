package com.nexus.mobiladorpro.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NexusDarkScheme = darkColorScheme(
    primary = Color(0xFF00E5FF),
    secondary = Color(0xFF00E676),
    tertiary = Color(0xFFFFCC80),
    background = Color(0xFF0B0E14),
    surface = Color(0xFF141923),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun NexusMobiladorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NexusDarkScheme,
        content = content
    )
}
