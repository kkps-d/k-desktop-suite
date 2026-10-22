package com.kkps.kdesktopsuite.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import com.kkps.kdesktopsuite.R
import com.kkps.kdesktopsuite.ui.theme.KDesktopSuiteTheme
import com.kkps.kdesktopsuite.workspace.WorkspaceScreen
import com.kkps.kdesktopsuite.workspace.starterDocuments

private enum class Destination(@StringRes val label: Int, @DrawableRes val icon: Int) {
    Workspace(R.string.workspace, R.drawable.ic_workspace),
    Guide(R.string.guide, R.drawable.ic_guide),
}

@Composable
fun KDesktopSuiteApp(
    documentText: (String) -> String,
    onDocumentTextChange: (String, String) -> Unit,
    contentWindowInsets: WindowInsets = WindowInsets.safeDrawing.union(WindowInsets.ime),
) {
    var destination by rememberSaveable { mutableStateOf(Destination.Workspace) }
    val destinationState = rememberSaveableStateHolder()

    Surface(modifier = Modifier.fillMaxSize()) {
        NavigationSuiteScaffold(
            modifier = Modifier.windowInsetsPadding(contentWindowInsets),
            navigationSuiteItems = {
                Destination.entries.forEach { item ->
                    item(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = { Icon(painterResource(item.icon), contentDescription = null) },
                        label = { Text(stringResource(item.label)) },
                    )
                }
            },
        ) {
            destinationState.SaveableStateProvider(destination.name) {
                when (destination) {
                    Destination.Workspace -> WorkspaceScreen(documentText, onDocumentTextChange)
                    Destination.Guide -> GuideScreen()
                }
            }
        }
    }
}

@Composable
private fun GuideScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.guide_title), style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.guide_intro), style = MaterialTheme.typography.bodyLarge)
            }
        }
        items(
            listOf(
                R.string.guide_resize_title to R.string.guide_resize_body,
                R.string.guide_input_title to R.string.guide_input_body,
                R.string.guide_state_title to R.string.guide_state_body,
            ),
        ) { (title, body) ->
            Card(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                SelectionContainer {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(body), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@PreviewScreenSizes
@Preview(name = "Desktop dark", widthDp = 1280, heightDp = 800, uiMode = 0x20)
@Composable
private fun SuitePreview() {
    KDesktopSuiteTheme {
        KDesktopSuiteApp(
            documentText = { id -> starterDocuments.first { it.id == id }.body },
            onDocumentTextChange = { _, _ -> },
        )
    }
}
