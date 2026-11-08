package com.kkps.kdesktopsuite.ui

import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

/** Only explicit creation requests notify; restoring or transferring a tab does not. */
@Composable
fun NewTabOpenedToast(request: Int) {
    val context = LocalContext.current
    val message = stringResource(R.string.ui_tabs_new_tab_opened)
    val toast = remember(context, message) { Toast.makeText(context, message, Toast.LENGTH_SHORT) }
    var delivered by remember { mutableIntStateOf(request) }
    DisposableEffect(toast) { onDispose { toast.cancel() } }
    LaunchedEffect(request) {
        if (request != delivered) {
            delivered = request
            toast.show()
        }
    }
}
