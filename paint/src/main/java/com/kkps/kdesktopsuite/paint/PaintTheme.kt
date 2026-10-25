package com.kkps.kdesktopsuite.paint

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun PaintTheme(appearance: String = "System", content: @Composable () -> Unit) {
    val dark = when (appearance) { "Dark" -> true; "Light" -> false; else -> isSystemInDarkTheme() }
    val context = LocalContext.current
    val colors = if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColorScheme(primary = Color(0xFFA4D0B5), primaryContainer = Color(0xFF164D37))
    else lightColorScheme(primary = Color(0xFF2F6650), primaryContainer = Color(0xFFC0ECD1))
    MaterialExpressiveTheme(colorScheme = colors, content = content)
}
