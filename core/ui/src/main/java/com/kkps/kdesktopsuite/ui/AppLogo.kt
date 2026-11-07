package com.kkps.kdesktopsuite.ui

import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource

/** Uses the app's appearance, which can differ from the system/launcher theme. */
@Composable
fun AppLogo(
    assets: AppIconAssets,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Image(painterResource(assets.resource(rememberAppIconChoice(), dark)), contentDescription, modifier)
}
