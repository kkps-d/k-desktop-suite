package com.kkps.kdesktopsuite.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F6650),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC0ECD1),
    onPrimaryContainer = Color(0xFF0E3826),
    secondary = Color(0xFF526356),
    surface = Color(0xFFF6FAF5),
    surfaceContainer = Color(0xFFEAEEE9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA4D0B5),
    onPrimary = Color(0xFF003822),
    primaryContainer = Color(0xFF164D37),
    onPrimaryContainer = Color(0xFFC0ECD1),
    secondary = Color(0xFFB9CCBC),
    surface = Color(0xFF101511),
    surfaceContainer = Color(0xFF1C211D),
)

@Composable
fun KDesktopSuiteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
