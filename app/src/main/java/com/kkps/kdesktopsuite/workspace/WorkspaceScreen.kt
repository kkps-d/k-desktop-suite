package com.kkps.kdesktopsuite.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kkps.kdesktopsuite.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    documentText: (String) -> String,
    onDocumentTextChange: (String, String) -> Unit,
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()
    val selectedId = navigator.currentDestination?.contentKey

    // Adaptive 1.3 updates the directive on resize, but its navigator only animates on navigation.
    // Synchronize the pane state when window size or folding posture changes too.
    LaunchedEffect(navigator.scaffoldDirective) {
        // A zero-progress seek settles the navigator on its current value without changing history.
        navigator.seekBack(fraction = 0f)
    }

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        modifier = Modifier.fillMaxSize(),
        listPane = {
            AnimatedPane {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("document-list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.documents), style = MaterialTheme.typography.headlineMedium)
                            Text(stringResource(R.string.workspace_subtitle), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    items(starterDocuments, key = { it.id }) { document ->
                        Surface(
                            onClick = {
                                scope.launch {
                                    navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, document.id)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().semantics { selected = document.id == selectedId },
                            shape = MaterialTheme.shapes.medium,
                            color = if (document.id == selectedId) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            ListItem(
                                headlineContent = { Text(document.title) },
                                supportingContent = { Text(document.subtitle) },
                                colors = androidx.compose.material3.ListItemDefaults.colors(
                                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                                ),
                            )
                        }
                    }
                }
            }
        },
        detailPane = {
            AnimatedPane {
                val document = starterDocuments.firstOrNull { it.id == selectedId }
                if (document == null) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(stringResource(R.string.select_document), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.select_document_hint), style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text(document.title, maxLines = 2) },
                                navigationIcon = {
                                    if (navigator.canNavigateBack()) {
                                        IconButton(onClick = { scope.launch { navigator.navigateBack() } }) {
                                            Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back_to_documents))
                                        }
                                    }
                                },
                            )
                        },
                    ) { padding ->
                        Column(
                            Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedTextField(
                                value = documentText(document.id),
                                onValueChange = { onDocumentTextChange(document.id, it) },
                                modifier = Modifier.fillMaxWidth().weight(1f).testTag("document-editor"),
                                label = { Text(stringResource(R.string.document_body)) },
                                textStyle = MaterialTheme.typography.bodyLarge,
                            )
                            Text(stringResource(R.string.demo_storage_hint), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
    )
}
