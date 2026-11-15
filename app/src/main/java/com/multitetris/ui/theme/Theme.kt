package com.multitetris.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

object MultiTetrisPalette {
    var isDark by mutableStateOf(true)
}

val ObsidianBg: Color
    get() = if (MultiTetrisPalette.isDark) Color(0xFF070B14) else Color(0xFFF3F6FC)

val SlateSurface: Color
    get() = if (MultiTetrisPalette.isDark) Color(0xFF101829) else Color(0xFFFFFFFF)

val ElevatedCard: Color
    get() = if (MultiTetrisPalette.isDark) Color(0xFF162138) else Color(0xFFE7EEF9)

val SubtleBorder: Color
    get() = if (MultiTetrisPalette.isDark) Color(0xFF263554) else Color(0xFFC5D3E8)

val CyberCyan: Color
    get() = if (MultiTetrisPalette.isDark) Color(0xFF00F0FF) else Color(0xFF0088A3)

val NeonMagenta: Color
    get() = if (MultiTetrisPalette.isDark) Color(0xFFFF2A85) else Color(0xFFD81B60)

val ElectricGold: Color
    get() = if (MultiTetrisPalette.isDark) Color(0xFFFFD600) else Color(0xFFD97706)

val TextPrimary: Color
    get() = if (MultiTetrisPalette.isDark) Color(0xFFF2F6FF) else Color(0xFF0F172A)

val TextSecondary: Color
    get() = if (MultiTetrisPalette.isDark) Color(0xFF97A7C7) else Color(0xFF475569)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF00F0FF),
    onPrimary = Color(0xFF070B14),
    secondary = Color(0xFFFF2A85),
    onSecondary = Color(0xFFF2F6FF),
    tertiary = Color(0xFFFFD600),
    background = Color(0xFF070B14),
    onBackground = Color(0xFFF2F6FF),
    surface = Color(0xFF101829),
    onSurface = Color(0xFFF2F6FF),
    surfaceVariant = Color(0xFF162138),
    onSurfaceVariant = Color(0xFF97A7C7),
    outline = Color(0xFF263554)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0088A3),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFD81B60),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFFD97706),
    background = Color(0xFFF3F6FC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE7EEF9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFC5D3E8)
)

@Composable
fun MultiTetrisTheme(
    isDarkMode: Boolean = true,
    content: @Composable () -> Unit
) {
    MultiTetrisPalette.isDark = isDarkMode
    MaterialTheme(
        colorScheme = if (isDarkMode) DarkColorScheme else LightColorScheme,
        content = content
    )
}
