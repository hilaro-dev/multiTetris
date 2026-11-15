package com.multitetris.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ObsidianBg = Color(0xFF070B14)
val SlateSurface = Color(0xFF101829)
val ElevatedCard = Color(0xFF162138)
val SubtleBorder = Color(0xFF263554)
val CyberCyan = Color(0xFF00F0FF)
val NeonMagenta = Color(0xFFFF2A85)
val ElectricGold = Color(0xFFFFD600)
val TextPrimary = Color(0xFFF2F6FF)
val TextSecondary = Color(0xFF97A7C7)

private val MultiTetrisColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = ObsidianBg,
    secondary = NeonMagenta,
    onSecondary = TextPrimary,
    tertiary = ElectricGold,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = SlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = ElevatedCard,
    onSurfaceVariant = TextSecondary,
    outline = SubtleBorder
)

@Composable
fun MultiTetrisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MultiTetrisColorScheme,
        content = content
    )
}
