package com.remotemagic.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Electric = Color(0xFF5B8CFF)
val Gold = Color(0xFFE7C173)

private val Dark = darkColorScheme(
    primary = Color(0xFF5B8CFF), onPrimary = Color(0xFF06153A), secondary = Gold, onSecondary = Color(0xFF3A2A00),
    background = Color(0xFF0F1A33), onBackground = Color(0xFFE8ECF2),
    surface = Color(0xFF152444), onSurface = Color(0xFFE8ECF2),
    surfaceVariant = Color(0xFF22355A), onSurfaceVariant = Color(0xFFC5CDD8)
)
private val Light = lightColorScheme(
    primary = Color(0xFF00658A), secondary = Color(0xFF7A5900),
    background = Color(0xFFF2F4F8), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFE1E7EF)
)

@Composable
fun RemoteTheme(dark: Boolean, content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
