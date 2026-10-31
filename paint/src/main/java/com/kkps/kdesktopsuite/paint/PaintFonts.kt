package com.kkps.kdesktopsuite.paint

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.kkps.kdesktopsuite.ui.R as SharedR

/** Both Compose text and the raster fallback use these same bundled typefaces. */
internal object PaintFonts {
    val names = listOf("Sans-serif", "Serif", "Monospace", "System")
    private var faces: Map<String, Typeface> = emptyMap()

    fun load(context: Context) {
        faces = mapOf(
            "Sans-serif" to context.resources.getFont(SharedR.font.noto_sans),
            "Serif" to context.resources.getFont(SharedR.font.noto_serif),
            "Monospace" to context.resources.getFont(SharedR.font.noto_sans_mono),
        )
    }

    fun typeface(name: String): Typeface = faces[name] ?: Typeface.DEFAULT

    // Resource families let Compose synthesize bold and italic from the bundled fonts.
    fun family(name: String): FontFamily = when (name) {
        "Sans-serif" -> FontFamily(Font(SharedR.font.noto_sans))
        "Serif" -> FontFamily(Font(SharedR.font.noto_serif))
        "Monospace" -> FontFamily(Font(SharedR.font.noto_sans_mono))
        else -> FontFamily.Default
    }
}
