package com.climbtracker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The colours of the app that change between the light and the dark theme. */
class Palette(
    val dark: Boolean,
    /** Screen background. */
    val cream: Color,
    /** Cards and buttons on the background. */
    val card: Color,
    /** Text, and the fill of what is selected. */
    val ink: Color,
    /** Text and icons on [ink]. */
    val onInk: Color,
    val muted: Color,
    val hairline: Color,
    /** Soft terracotta, behind the selected tab and attempts. */
    val terracottaTint: Color,
    /** Soft green, behind sends. */
    val sendTint: Color,
    val secondaryTint: Color,
)

private val Light = Palette(
    dark = false,
    cream = Color(0xFFF4EFEA),
    card = Color(0xFFFDFCF9),
    ink = Color(0xFF1C1917),
    onInk = Color.White,
    muted = Color(0xFFA39B91),
    hairline = Color(0xFFE7DED3),
    terracottaTint = Color(0xFFF6E3D8),
    sendTint = Color(0xFFE3F1E7),
    secondaryTint = Color(0xFFEFE6DC),
)

private val Dark = Palette(
    dark = true,
    cream = Color(0xFF171412),
    card = Color(0xFF24201D),
    ink = Color(0xFFF2ECE5),
    onInk = Color(0xFF1C1917),
    muted = Color(0xFF9A9187),
    hairline = Color(0xFF3B3531),
    terracottaTint = Color(0xFF4A2B1B),
    sendTint = Color(0xFF1F3A2A),
    secondaryTint = Color(0xFF302B27),
)

val LocalPalette = staticCompositionLocalOf { Light }

val Cream: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.cream
val CardWhite: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.card
val Ink: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.ink
val OnInk: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.onInk
val Muted: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.muted
val Hairline: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.hairline

// The accents are the same in both themes.
val Terracotta = Color(0xFFB85423)
val SendGreen = Color(0xFF2E9E5B)

private fun scheme(p: Palette) = (if (p.dark) darkColorScheme() else lightColorScheme()).copy(
    primary = Terracotta,
    onPrimary = Color.White,
    primaryContainer = p.terracottaTint,
    onPrimaryContainer = p.ink,
    secondary = p.ink,
    onSecondary = p.onInk,
    secondaryContainer = p.secondaryTint,
    onSecondaryContainer = p.ink,
    background = p.cream,
    onBackground = p.ink,
    surface = p.cream,
    onSurface = p.ink,
    surfaceVariant = p.card,
    onSurfaceVariant = p.muted,
    surfaceContainerLowest = p.card,
    surfaceContainerLow = p.card,
    surfaceContainer = p.card,
    surfaceContainerHigh = p.card,
    surfaceContainerHighest = p.card,
    outline = p.hairline,
    outlineVariant = p.hairline,
)

/** Warm theme used by every screen; light or dark as the system is set. */
@Composable
fun ClimbTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (dark) Dark else Light
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme(palette), content = content)
    }
}
