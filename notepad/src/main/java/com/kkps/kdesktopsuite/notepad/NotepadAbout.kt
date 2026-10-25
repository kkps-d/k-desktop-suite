package com.kkps.kdesktopsuite.notepad

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kkps.kdesktopsuite.ui.AppAboutDialog

@Composable
internal fun NotepadAboutDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val sourceUrl = stringResource(R.string.source_code_url)
    AppAboutDialog(title = stringResource(R.string.about_notepad),
        version = stringResource(R.string.about_version,BuildConfig.VERSION_NAME),
        developer = stringResource(R.string.about_developer),sourceLabel = stringResource(R.string.source_code),
        dismissLabel = stringResource(R.string.dismiss),
        icon = { Image(painterResource(R.drawable.ic_notepad),null,Modifier.size(48.dp)) },
        onSourceClick = { uriHandler.openUri(sourceUrl) },onDismiss = onDismiss)
}

@Preview(name = "About phone", widthDp = 400, heightDp = 800)
@Preview(name = "About short", widthDp = 700, heightDp = 320)
@Preview(name = "About large text", widthDp = 320, heightDp = 600, fontScale = 2f)
@Preview(name = "About dark", widthDp = 400, heightDp = 800, uiMode = 0x20)
@Composable
private fun NotepadAboutPreview() {
    NotepadTheme { NotepadAboutDialog({}) }
}
