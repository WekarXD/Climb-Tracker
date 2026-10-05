package com.climbtracker.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Cream = Color(0xFFF4EFEA)
val CardWhite = Color(0xFFFDFCF9)
val Ink = Color(0xFF1C1917)
val Muted = Color(0xFFA39B91)
val Hairline = Color(0xFFE7DED3)
val Terracotta = Color(0xFFB85423)
val SendGreen = Color(0xFF2E9E5B)

private val Scheme = lightColorScheme(
    primary = Terracotta,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF6E3D8),
    onPrimaryContainer = Ink,
    secondary = Ink,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEFE6DC),
    onSecondaryContainer = Ink,
    background = Cream,
    onBackground = Ink,
    surface = Cream,
    onSurface = Ink,
    surfaceVariant = CardWhite,
    onSurfaceVariant = Muted,
    surfaceContainerLowest = CardWhite,
    surfaceContainerLow = CardWhite,
    surfaceContainer = CardWhite,
    surfaceContainerHigh = CardWhite,
    surfaceContainerHighest = CardWhite,
    outline = Hairline,
    outlineVariant = Hairline,
)

/** Light, warm theme used by every screen regardless of the system setting. */
@Composable
fun ClimbTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
