package com.kkps.kdesktopsuite.explorer

import android.os.Build
import android.view.WindowInsetsController
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import com.kkps.kdesktopsuite.ui.AppDropdownMenu
import com.kkps.kdesktopsuite.ui.AppMenuItem
import com.kkps.kdesktopsuite.ui.CaptionInput
import com.kkps.kdesktopsuite.ui.DesktopCaption
import com.kkps.kdesktopsuite.ui.captionControl
import com.kkps.kdesktopsuite.ui.rememberCaptionInput
import com.kkps.kdesktopsuite.ui.rememberDesktopCaption
import kotlin.math.roundToInt

@Composable
internal fun ExplorerScreen(
    explorer: ExplorerViewModel,
    newWindow: () -> Unit,
    closeTab: (Int) -> Unit,
    closeWindow: () -> Unit,
    caption: DesktopCaption = rememberDesktopCaption(true),
    contentInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var region by remember { mutableStateOf(Rect.Zero) }
    val minimumHeight = with(density) {
        (MaterialTheme.typography.bodyMedium.lineHeight.toDp() + 4.dp).coerceAtLeast(24.dp).toPx()
    }
    val controls = explorerCaptionControls(caption, region, density.density, density.fontScale, minimumHeight, rtl)
    val input = rememberCaptionInput(controls)
    val layoutInsets = if (caption.height > 0f) {
        contentInsets.exclude(WindowInsets(top = caption.height.roundToInt()))
            .union(WindowInsets(top = caption.safeTop.roundToInt()))
    } else contentInsets
    val view = LocalView.current
    val headerColor = MaterialTheme.colorScheme.surfaceContainer
    SideEffect {
        if (Build.VERSION.SDK_INT >= 35) {
            view.windowInsetsController?.setSystemBarsAppearance(
                if (headerColor.luminance() > 0.5f) WindowInsetsController.APPEARANCE_LIGHT_CAPTION_BARS else 0,
                WindowInsetsController.APPEARANCE_LIGHT_CAPTION_BARS,
            )
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.windowInsetsPadding(layoutInsets).onGloballyPositioned {
            val origin = it.positionInWindow()
            region = Rect(origin.x, origin.y, origin.x + it.size.width, origin.y + it.size.height)
        }) {
            if (controls == null && caption.height > region.top) {
                Spacer(Modifier.fillMaxWidth().height(with(density) { (caption.height - region.top).toDp() })
                    .background(headerColor))
            }
            val headerModifier = if (controls == null) Modifier else Modifier.absolutePadding(
                left = with(density) { (controls.left - region.left).toDp() },
                right = with(density) { (region.right - controls.right).toDp() },
            )
            ExplorerHeader(explorer, newWindow, closeTab, closeWindow, headerModifier,
                controls?.let { with(density) { it.height.toDp() } }, if (controls == null) null else input)
            val workspace = stringResource(R.string.blank_workspace)
            Box(Modifier.fillMaxSize().testTag("explorer-workspace").semantics { contentDescription = workspace })
        }
    }
}

@Composable
private fun ExplorerHeader(
    explorer: ExplorerViewModel, newWindow: () -> Unit, closeTab: (Int) -> Unit, closeWindow: () -> Unit,
    modifier: Modifier, captionHeight: Dp?, input: CaptionInput?,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val height = captionHeight ?: 52.dp
    val buttonWidth = if (captionHeight == null) 48.dp else 32.dp
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides if (captionHeight == null) 48.dp else 0.dp) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)
                .then(modifier).then(if (captionHeight == null) Modifier.heightIn(min = height) else Modifier.height(height))
                .padding(horizontal = if (captionHeight == null) 8.dp else 0.dp)
                .testTag(if (captionHeight == null) "explorer-header-below" else "explorer-header-caption"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExplorerTabs(explorer, closeTab, Modifier.weight(1f), captionHeight, input)
            ExplorerIconButton(R.drawable.ic_add, stringResource(R.string.new_tab), explorer::newTab,
                Modifier.width(buttonWidth).height(height.coerceAtMost(48.dp)).captionControl(input, "new"))
            Box {
                ExplorerIconButton(R.drawable.ic_more, stringResource(R.string.window_menu), { menuOpen = true },
                    Modifier.width(buttonWidth).height(height.coerceAtMost(48.dp)).captionControl(input, "menu"))
                AppDropdownMenu(menuOpen, { menuOpen = false }) {
                    AppMenuItem(text = { Text(stringResource(R.string.new_tab)) }, shortcut = "Ctrl+T",
                        onClick = { menuOpen = false; explorer.newTab() })
                    AppMenuItem(text = { Text(stringResource(R.string.new_window)) }, shortcut = "Ctrl+N",
                        onClick = { menuOpen = false; newWindow() })
                    AppMenuItem(text = { Text(stringResource(R.string.close_tab)) }, shortcut = "Ctrl+W",
                        onClick = { menuOpen = false; closeTab(explorer.activeId) })
                    AppMenuItem(text = { Text(stringResource(R.string.close_window)) }, shortcut = "Ctrl+Shift+W",
                        onClick = { menuOpen = false; closeWindow() })
                }
            }
        }
    }
}

@Composable
private fun ExplorerTabs(
    explorer: ExplorerViewModel, closeTab: (Int) -> Unit, modifier: Modifier, captionHeight: Dp?, input: CaptionInput?,
) {
    val minimumWidth = 128.dp * LocalDensity.current.fontScale
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val focus = remember(explorer.tabs) { explorer.tabs.associateWith { FocusRequester() } }
    val topPadding = if (captionHeight == null) 4.dp else (captionHeight - 24.dp).coerceIn(0.dp, 4.dp)
    val tabHeight = captionHeight?.minus(topPadding)
    BoxWithConstraints(modifier) {
        val availableWidth = maxWidth
        if (maxWidth < minimumWidth) {
            var expanded by remember { mutableStateOf(false) }
            val chooseTab = stringResource(R.string.choose_tab)
            Box {
                TextButton(onClick = { expanded = true }, modifier = Modifier.heightIn(min = 48.dp)
                    .testTag("tab-selector").semantics { contentDescription = chooseTab }) {
                    Text(stringResource(R.string.tab_title, explorer.activeId), maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Text(" ▾", modifier = Modifier.semantics { contentDescription = "" })
                }
                AppDropdownMenu(expanded, { expanded = false }) {
                    explorer.tabs.forEach { id ->
                        val title = stringResource(R.string.tab_title, id)
                        AppMenuItem(text = { Text(title) }, checked = id == explorer.activeId,
                            onClick = { expanded = false; explorer.select(id) }, trailingIcon = {
                                ExplorerIconButton(R.drawable.ic_close, stringResource(R.string.close_named_tab, title),
                                    { closeTab(id) })
                            })
                    }
                }
            }
        } else {
            val width = (maxWidth / explorer.tabs.size).coerceIn(minimumWidth, 240.dp.coerceAtLeast(minimumWidth))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup()
                .padding(top = topPadding), verticalAlignment = Alignment.CenterVertically) {
                explorer.tabs.forEachIndexed { index, id ->
                    val bringIntoView = remember(id) { BringIntoViewRequester() }
                    var focused by remember(id) { mutableStateOf(false) }
                    val selected = id == explorer.activeId
                    val shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                    val title = stringResource(R.string.tab_title, id)
                    LaunchedEffect(explorer.activeId, availableWidth, width) {
                        if (selected) bringIntoView.bringIntoView()
                    }
                    LaunchedEffect(focused) {
                        if (focused) bringIntoView.bringIntoView()
                    }
                    Row(
                        Modifier.width(width).then(if (tabHeight == null) Modifier.heightIn(min = 48.dp) else Modifier.height(tabHeight))
                            .captionControl(input, "tab-$id").bringIntoViewRequester(bringIntoView)
                            .focusRequester(focus.getValue(id)).onFocusChanged { focused = it.isFocused }
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown || event.isCtrlPressed || event.isAltPressed) false
                                else {
                                    val target = when (event.key) {
                                        Key.DirectionRight -> index + if (rtl) -1 else 1
                                        Key.DirectionLeft -> index + if (rtl) 1 else -1
                                        Key.MoveHome -> 0
                                        Key.MoveEnd -> explorer.tabs.lastIndex
                                        else -> return@onPreviewKeyEvent false
                                    }
                                    explorer.tabs.getOrNull(target)?.let { focus.getValue(it).requestFocus() }
                                    true
                                }
                            }
                            .clip(shape)
                            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .border(2.dp, if (focused) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
                            .selectable(selected, role = Role.Tab, onClick = { explorer.select(id) })
                            .testTag("tab-$id").padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        ExplorerIconButton(R.drawable.ic_close, stringResource(R.string.close_named_tab, title), { closeTab(id) },
                            if (tabHeight == null) Modifier.size(48.dp) else Modifier.width(32.dp).height(tabHeight))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExplorerIconButton(icon: Int, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) {
        IconButton(onClick, modifier.size(48.dp)) {
            Icon(painterResource(icon), label, Modifier.size(20.dp))
        }
    }
}

@Preview(name = "Desktop", widthDp = 1200, heightDp = 800)
@Preview(name = "Compact", widthDp = 320, heightDp = 640)
@Preview(name = "Short dark", widthDp = 840, heightDp = 360, uiMode = 0x20)
@Preview(name = "Large text", widthDp = 320, heightDp = 640, fontScale = 2f)
@Composable
private fun ExplorerPreview() {
    val explorer = remember { ExplorerViewModel(SavedStateHandle()).apply { repeat(4) { newTab() } } }
    ExplorerTheme { ExplorerScreen(explorer, {}, { explorer.close(it) }, {}) }
}
