package com.kkps.kdesktopsuite.notepad

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
internal fun NotepadAboutDialog(onDismiss: () -> Unit) {
    val dismissFocus = remember { FocusRequester() }
    val uriHandler = LocalUriHandler.current
    val sourceUrl = stringResource(R.string.source_code_url)
    LaunchedEffect(Unit) { dismissFocus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("about-dialog").onPreviewKeyEvent {
            if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
                onDismiss()
                true
            } else false
        },
        icon = { Image(painterResource(R.drawable.ic_notepad), null, Modifier.size(48.dp)) },
        title = { Text(stringResource(R.string.about_notepad)) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME))
                Text(stringResource(R.string.about_developer), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { uriHandler.openUri(sourceUrl) }) { Text(stringResource(R.string.source_code)) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.focusRequester(dismissFocus)) {
                Text(stringResource(R.string.dismiss))
            }
        },
    )
}

@Preview(name = "About phone", widthDp = 400, heightDp = 800)
@Preview(name = "About short", widthDp = 700, heightDp = 320)
@Preview(name = "About large text", widthDp = 320, heightDp = 600, fontScale = 2f)
@Preview(name = "About dark", widthDp = 400, heightDp = 800, uiMode = 0x20)
@Composable
private fun NotepadAboutPreview() {
    NotepadTheme { NotepadAboutDialog({}) }
}
