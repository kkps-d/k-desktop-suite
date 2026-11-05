package com.kkps.kdesktopsuite.explorer

import android.os.Build
import android.view.WindowInsetsController
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import com.kkps.kdesktopsuite.ui.*
import com.kkps.kdesktopsuite.ui.R as UiR
import kotlin.math.roundToInt

@Composable
internal fun ExplorerScreen(
    explorer: ExplorerViewModel,
    newWindow: () -> Unit,
    closeTab: (String) -> Unit,
    closeWindow: () -> Unit,
    caption: DesktopCaption = rememberDesktopCaption(true),
    contentInsets: WindowInsets = WindowInsets.safeDrawing,
    activity: ExplorerActivity? = null,
    adaptive: WindowAdaptiveInfo = currentWindowAdaptiveInfoV2(),
) {
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    var movingId by rememberSaveable { mutableStateOf<String?>(null) }
    val workspace = explorer.workspace
    val headerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    SideEffect {
        explorer.modalOpen = movingId != null
        if (Build.VERSION.SDK_INT >= 35) view.windowInsetsController?.setSystemBarsAppearance(
            if (headerColor.luminance() > 0.5f) WindowInsetsController.APPEARANCE_LIGHT_CAPTION_BARS else 0,
            WindowInsetsController.APPEARANCE_LIGHT_CAPTION_BARS,
        )
    }
    DisposableEffect(explorer, focusManager) {
        explorer.prepareTransfer = { focusManager.clearFocus(force = true) }
        onDispose { explorer.prepareTransfer = {}; explorer.modalOpen = false }
    }
    BackHandler(workspace.dragging != null) {
        workspace.dragging?.let { workspace.cancelTransfer(it.token) }
        view.cancelDragAndDrop()
    }
    val tabs = explorer.tabs.map { DocumentTab(it.id, stringResource(R.string.tab_title, it.number)) }
    val dragged = workspace.dragging?.let(workspace::tab)?.let {
        DocumentTab(it.id, stringResource(R.string.tab_title, it.number))
    }
    val interactions = DocumentTabInteractions(dragged, ExplorerWindows.mimeType,
        { ExplorerWindows.dragData(activity, explorer, it) }, workspace::endDrag, workspace::cancelTransfer,
        { token, index -> workspace.move(token, explorer, index) }, explorer::reorder, explorer::canTransfer)
    val layoutInsets = if (caption.height > 0f) contentInsets.exclude(WindowInsets(top = caption.height.roundToInt()))
        .union(WindowInsets(top = caption.safeTop.roundToInt())) else contentInsets
    Surface(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(layoutInsets)
            .suiteTabDropTarget(interactions, dropped = { ExplorerWindows.launchTransfer(activity, explorer, it) })) {
            val density = LocalDensity.current
            var origin by remember { mutableStateOf(Offset.Zero) }
            var region = with(density) { Rect(0f, 0f, maxWidth.toPx(), maxHeight.toPx()) }
            adaptive.windowPosture.hingeList.filter { it.isSeparating || it.isOccluding }.forEach { hinge ->
                val bounds = hinge.bounds.translate(-origin)
                if (region.overlaps(bounds)) {
                    val candidates = if (hinge.isVertical) listOf(
                        region.copy(right = bounds.left.coerceIn(region.left, region.right)),
                        region.copy(left = bounds.right.coerceIn(region.left, region.right)),
                    ) else listOf(
                        region.copy(bottom = bounds.top.coerceIn(region.top, region.bottom)),
                        region.copy(top = bounds.bottom.coerceIn(region.top, region.bottom)),
                    )
                    region = candidates.maxBy { it.width * it.height }
                }
            }
            Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() })
            Column(Modifier.absoluteOffset { IntOffset(region.left.roundToInt(), region.top.roundToInt()) }
                .size(with(density) { region.width.toDp() }, with(density) { region.height.toDp() })) {
                Column(Modifier.suiteTabDropTarget(interactions, dropped = { false })) {
                    SuiteTabHeader(tabs, explorer.activeId, false, { id, _ -> explorer.select(id) }, closeTab,
                        explorer::newTab, caption, region.translate(origin), interactions = interactions,
                        moveToWindow = { movingId = it })
                    ExplorerCommands(explorer::newTab, newWindow, { closeTab(explorer.activeId) }, closeWindow,
                        explorer.tabs.isNotEmpty())
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                val workspaceLabel = stringResource(R.string.blank_workspace)
                Box(Modifier.weight(1f).fillMaxWidth().testTag("explorer-workspace")
                    .semantics { contentDescription = workspaceLabel }.focusable())
            }
        }
    }
    movingId?.let { id ->
        AlertDialog(
            onDismissRequest = { movingId = null },
            title = { Text(stringResource(UiR.string.ui_tabs_move_to_window)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    workspace.windows.filter { it !== explorer && it.attached && !it.closed }.forEachIndexed { index, target ->
                        val tab = target.tabs.find { it.id == target.activeId }
                        val title = tab?.let { stringResource(R.string.tab_title, it.number) }.orEmpty()
                        TextButton(enabled = !target.modalOpen, onClick = {
                            movingId = null
                            explorer.modalOpen = false
                            workspace.beginTransfer(explorer, id)?.let {
                                if (!workspace.move(it.token, target, target.tabs.size)) workspace.cancelTransfer(it.token)
                            }
                        }) { Text(stringResource(R.string.window_title, index + 1, title)) }
                    }
                    TextButton(enabled = activity != null && ExplorerWindows.supported(activity) && explorer.tabs.size > 1,
                        onClick = {
                            movingId = null
                            explorer.modalOpen = false
                            activity?.let { ExplorerWindows.launch(it, explorer, id) }
                        }) { Text(stringResource(R.string.new_window)) }
                }
            },
            confirmButton = { TextButton(onClick = { movingId = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ExplorerCommands(new: () -> Unit, newWindow: () -> Unit, close: () -> Unit, closeWindow: () -> Unit, hasTab: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("command-bar").padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box {
                AppMenuButton(stringResource(R.string.file_menu)) { expanded = true }
                AppDropdownMenu(expanded, { expanded = false }) {
                    AppMenuItem(text = { Text(stringResource(UiR.string.ui_tabs_new_tab)) }, shortcut = "Ctrl+N",
                        onClick = { expanded = false; new() })
                    AppMenuItem(text = { Text(stringResource(R.string.new_window)) }, shortcut = "Ctrl+Shift+N",
                        onClick = { expanded = false; newWindow() })
                    AppMenuItem(text = { Text(stringResource(UiR.string.ui_tabs_close_document)) }, shortcut = "Ctrl+W", enabled = hasTab,
                        onClick = { expanded = false; close() })
                    AppMenuItem(text = { Text(stringResource(R.string.close_window)) }, onClick = { expanded = false; closeWindow() })
                }
            }
        }
    }
}

@Preview(name = "Desktop", widthDp = 1200, heightDp = 800)
@Preview(name = "Compact", widthDp = 160, heightDp = 640)
@Preview(name = "Short dark", widthDp = 840, heightDp = 360, uiMode = 0x20)
@Preview(name = "Large text", widthDp = 320, heightDp = 640, fontScale = 2f)
@Composable
private fun ExplorerPreview() {
    val explorer = remember { ExplorerViewModel(SavedStateHandle()).apply { repeat(4) { newTab() } } }
    ExplorerTheme { ExplorerScreen(explorer, {}, explorer::close, {}) }
}
