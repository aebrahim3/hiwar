package com.hiwar.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Hiwar palette: deep night blue, warm sand, and a saffron accent.
val HiwarInk = Color(0xFF0F1B2D)
val HiwarNavy = Color(0xFF1E3A5F)
val HiwarSand = Color(0xFFF4EBDD)
val HiwarSaffron = Color(0xFFE8A33D)
val HiwarSage = Color(0xFF4F8A6E)
val HiwarRose = Color(0xFFC0564B)

private val LightColors = lightColorScheme(
    primary = HiwarNavy,
    onPrimary = Color.White,
    secondary = HiwarSaffron,
    onSecondary = HiwarInk,
    tertiary = HiwarSage,
    background = Color(0xFFFBF8F3),
    surface = Color.White,
    surfaceVariant = HiwarSand,
    onSurfaceVariant = Color(0xFF4A4A4A),
    error = HiwarRose,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CC3F0),
    onPrimary = HiwarInk,
    secondary = HiwarSaffron,
    onSecondary = HiwarInk,
    tertiary = Color(0xFF8CC7A8),
    background = Color(0xFF0B1422),
    surface = HiwarInk,
    surfaceVariant = Color(0xFF1C2A3E),
    onSurfaceVariant = Color(0xFFC9D1DC),
    error = Color(0xFFE88A80),
)

@Composable
fun HiwarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
