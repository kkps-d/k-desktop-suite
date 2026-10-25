package com.kkps.kdesktopsuite.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun AppAboutDialog(
    title: String, version: String, developer: String, sourceLabel: String, dismissLabel: String,
    icon: @Composable () -> Unit, onSourceClick: () -> Unit, onDismiss: () -> Unit,
) {
    val dismissFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { dismissFocus.requestFocus() }
    AlertDialog(onDismissRequest = onDismiss,
        modifier = Modifier.testTag("about-dialog").onPreviewKeyEvent {
            if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { onDismiss(); true } else false
        },icon = icon,title = { Text(title) },text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(version)
                Text(developer,color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onSourceClick) { Text(sourceLabel) }
            }
        },confirmButton = {
            TextButton(onClick = onDismiss,modifier = Modifier.focusRequester(dismissFocus)) { Text(dismissLabel) }
        })
}
