@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kkps.kdesktopsuite.paint

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kkps.kdesktopsuite.ui.R as UiR

@Composable
internal fun PaintDebugScreen(onBack: () -> Unit) {
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { backFocus.requestFocus() }

    Column(Modifier.fillMaxSize().testTag("paint-debug-screen").onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
            onBack()
            true
        } else false
    }) {
        TopAppBar(title = { Text(stringResource(R.string.ui_debug)) },navigationIcon = {
            IconButton(onClick = onBack,modifier = Modifier.focusRequester(backFocus)) {
                Icon(painterResource(UiR.drawable.ic_back),stringResource(R.string.ui_back))
            }
        },windowInsets = WindowInsets(0))
        PrimaryTabRow(selectedTabIndex = 0) {
            Tab(selected = true,onClick = {},text = { Text(stringResource(R.string.ui_components)) })
        }
        Box(Modifier.fillMaxSize().padding(24.dp)) {
            Text(stringResource(R.string.ui_components_empty),style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
