package com.kkps.kdesktopsuite.notepad

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

internal object NotepadSpacing {
    val small = 8.dp
    val medium = 16.dp
    val touchTarget = 48.dp
}

@Composable
internal fun NotepadTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) {
        darkColorScheme(primary = Color(0xFFA4D0B5), primaryContainer = Color(0xFF164D37))
    } else {
        lightColorScheme(primary = Color(0xFF2F6650), primaryContainer = Color(0xFFC0ECD1))
    }
    MaterialExpressiveTheme(colorScheme = colors, content = content)
}
