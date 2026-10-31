@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kkps.kdesktopsuite.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AppSettingsPage(
    title: String, backLabel: String, onBack: () -> Unit, modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(availableWidth: Dp) -> Unit,
) {
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { backFocus.requestFocus() }
    Column(modifier.fillMaxSize().testTag("settings-page").onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { onBack(); true } else false
    }) {
        TopAppBar(title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },navigationIcon = {
            IconButton(onClick = onBack,modifier = Modifier.focusRequester(backFocus)) {
                Icon(painterResource(R.drawable.ic_back),backLabel)
            }
        },windowInsets = WindowInsets(0))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(),contentAlignment = Alignment.TopCenter) {
            val availableWidth = maxWidth
            Column(Modifier.widthIn(max = 660.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp,vertical = 16.dp),verticalArrangement = Arrangement.spacedBy(24.dp)) {
                content(availableWidth)
            }
        }
    }
}
