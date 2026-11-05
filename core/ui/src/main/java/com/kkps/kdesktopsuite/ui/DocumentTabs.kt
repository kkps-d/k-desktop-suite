@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.kkps.kdesktopsuite.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Visual state only; each application retains ownership of its tab content and session. */
data class DocumentTab(
    val id: String,
    val title: String,
    val state: String = "",
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val error: Boolean = false,
)

object TabBarDimensions {
    val topPadding = 8.dp
    val minTabWidth = 120.dp
    val maxTabWidth = 240.dp
    val contentStartPadding = 22.dp
    val contentEndPadding = 8.dp
    val buttonSize = 48.dp
    val minBarHeight get() = topPadding + buttonSize
    val focusOutlineWidth = 2.dp
}

object TabTypography {
    val fontFamily = FontFamily.Default
    val titleFontSize = 14.sp
    val titleLineHeight = 20.sp

    val title
        @Composable get() = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = fontFamily, fontSize = titleFontSize, lineHeight = titleLineHeight,
        )
}

@Composable
fun SuiteTabHeader(
    documents: List<DocumentTab>, activeId: String, pickerPending: Boolean,
    select: (String, Boolean) -> Unit, close: (String) -> Unit, new: () -> Unit,
    caption: DesktopCaption, region: Rect, list: LazyListState = rememberLazyListState(),
    interactions: DocumentTabInteractions? = null,
    onWelcome: () -> Unit = {},
    moveToWindow: (String) -> Unit = {},
) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val lineHeight = with(density) { TabTypography.title.lineHeight.toDp() }
    val contentHeight = lineHeight.coerceAtLeast(DesktopCaptionDimensions.minContentHeight)
    val layout = captionLayout(caption, region, density.density, with(density) { contentHeight.toPx() })
    val controls = if (layout.integrated) layout.controls(density.density, direction == LayoutDirection.Rtl) else null
    val input = rememberCaptionInput(controls)
    val captionHeight = with(density) { if (layout.integrated) (caption.height - region.top).toDp() else 0.dp }
    // Reduce the decorative top inset before giving up native caption integration.
    val topPadding = if (layout.integrated) (captionHeight - contentHeight).coerceIn(0.dp, TabBarDimensions.topPadding)
        else TabBarDimensions.topPadding
    // Subtract rounded pixels so the tab and top inset exactly fill the caption.
    val tabHeight = if (layout.integrated) with(density) { (captionHeight.roundToPx() - topPadding.roundToPx()).toDp() }
        else lineHeight.coerceAtLeast(TabBarDimensions.buttonSize)
    if (layout.mode == CaptionMode.BelowCaption) {
        Spacer(Modifier.fillMaxWidth().height(with(density) { (caption.height - region.top).coerceAtLeast(0f).toDp() })
            .background(MaterialTheme.colorScheme.surfaceContainerHigh))
    }
    // System clearances use physical coordinates; only app controls follow RTL.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .heightIn(min = captionHeight).testTag("document-header"),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (controls != null) Spacer(Modifier.width(with(density) { (controls.left - region.left).toDp() }))
            BoxWithConstraints(if (controls == null) Modifier.weight(1f) else Modifier.width(with(density) { controls.width.toDp() })) {
                val compact = maxWidth < TabBarDimensions.minTabWidth + TabBarDimensions.buttonSize
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    key(layout.mode, compact) {
                        if (documents.isEmpty()) {
                            Row(Modifier.fillMaxWidth().padding(top = topPadding)
                                .captionControl(if (layout.integrated && interactions?.dragged != null) input else null, "welcome-drop-area")
                                .testTag("tab-drop-area")
                                .suiteTabDropTarget(interactions, dropped = { token ->
                                    interactions?.move?.invoke(token, 0) == true
                                }), verticalAlignment = Alignment.CenterVertically) {
                                SuiteWelcomeTab(
                                    onClick = onWelcome,
                                    modifier = Modifier.weight(1f, fill = false).width(TabBarDimensions.maxTabWidth)
                                        .heightIn(min = tabHeight).captionControl(if (layout.integrated) input else null, "welcome"),
                                )
                                TabIconButton(stringResource(R.string.ui_tabs_new_tab), R.drawable.ui_ic_tab_new, iconSize = 20.dp,
                                    modifier = Modifier.captionControl(if (layout.integrated) input else null, "new"),
                                    targetHeight = tabHeight.coerceAtMost(TabBarDimensions.buttonSize), onClick = new)
                            }
                        } else if (compact) {
                            DocumentSwitcher(documents, activeId, pickerPending, select, close, new, tabHeight,
                                interactions, moveToWindow)
                        } else {
                            DocumentTabs(documents, activeId, pickerPending, select, close, new, list,
                                if (layout.integrated) input else null, tabHeight, topPadding,
                                interactions, moveToWindow)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentTabStatus(doc: DocumentTab) {
    if (!doc.error && !doc.saving && !doc.dirty) return
    Box(Modifier.width(12.dp), contentAlignment = Alignment.Center) {
        when {
            doc.error -> Icon(painterResource(R.drawable.ui_ic_tab_error), null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.error)
            doc.saving -> CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
            doc.dirty -> Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
        }
    }
    Spacer(Modifier.width(4.dp))
}

@Composable
private fun DocumentSwitcher(
    documents: List<DocumentTab>, activeId: String, pickerPending: Boolean,
    select: (String, Boolean) -> Unit, close: (String) -> Unit, new: () -> Unit,
    minTabHeight: Dp,
    interactions: DocumentTabInteractions?, moveToWindow: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val moveLeftLabel = stringResource(R.string.ui_tabs_move_tab_left)
    val moveRightLabel = stringResource(R.string.ui_tabs_move_tab_right)
    val moveWindowLabel = stringResource(R.string.ui_tabs_move_to_window)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var actionId by remember { mutableStateOf<String?>(null) }
    val active = documents.find { it.id == activeId }
    val title = active?.let { it.title }.orEmpty()
    val accessibleTitle = if (active?.dirty == true) stringResource(R.string.ui_tabs_unsaved_tab, title) else title
    val switcherLabel = stringResource(R.string.ui_tabs_switch_document, accessibleTitle)
    val state = active?.state.orEmpty()
    Row(Modifier.fillMaxWidth().padding(top = TabBarDimensions.topPadding), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            TextButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = minTabHeight)
                    .testTag("document-switcher")
                    .suiteTabDragSource(interactions, activeId, title)
                    .suiteTabDropTarget(interactions, dropped = { token ->
                        interactions?.move?.invoke(token, documents.size) == true
                    })
                    .semantics { contentDescription = switcherLabel; stateDescription = state },
                shape = DocumentTabShape,
                colors = ButtonDefaults.textButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                contentPadding = PaddingValues(start = TabBarDimensions.contentStartPadding, end = TabBarDimensions.contentEndPadding),
            ) {
                active?.let { DocumentTabStatus(it) }
                Text(title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TabTypography.title)
                Icon(painterResource(R.drawable.ui_ic_tab_expand), null, Modifier.size(16.dp))
            }
            AppDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                documents.forEach { doc ->
                    val docTitle = doc.title
                    val label = if (doc.dirty) stringResource(R.string.ui_tabs_unsaved_tab, docTitle) else docTitle
                    val docState = doc.state
                    Row(Modifier.widthIn(min = 240.dp).testTag("document-entry-${doc.id}"), verticalAlignment = Alignment.CenterVertically) {
                        AppMenuItem(
                            text = {
                                Column {
                                    Text(docTitle, style = TabTypography.title)
                                    Text(docState, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("document-tab-${doc.id}").semantics {
                                selected = doc.id == activeId
                                contentDescription = label
                                stateDescription = docState
                                if (interactions != null) customActions = tabMoveActions(interactions, documents, doc.id,
                                    moveLeftLabel, moveRightLabel, moveWindowLabel,
                                    { expanded = false; moveToWindow(it) }, rtl)
                            },
                            trailingIcon = if (doc.id == activeId) ({ Text("✓") }) else null,
                            onClick = { expanded = false; select(doc.id, true) },
                        )
                        if (interactions != null) Box {
                            TextButton(onClick = { actionId = doc.id }) {
                                Text("⋮", Modifier.semantics { contentDescription = moveWindowLabel })
                            }
                            AppDropdownMenu(expanded = actionId == doc.id, onDismissRequest = { actionId = null }) {
                                TabMoveMenu(interactions, documents, doc.id, moveToWindow = {
                                    actionId = null; expanded = false; moveToWindow(it)
                                }, dismiss = { actionId = null })
                            }
                        }
                        TabIconButton(stringResource(R.string.ui_tabs_close_tab, docTitle), R.drawable.ui_ic_tab_close,
                            enabled = !doc.saving && !pickerPending, targetHeight = AppMenuDimensions.itemHeight) {
                            expanded = false
                            close(doc.id)
                        }
                    }
                }
            }
        }
        TabIconButton(stringResource(R.string.ui_tabs_new_tab), R.drawable.ui_ic_tab_new, iconSize = 20.dp, onClick = new)
    }
}

@Composable
private fun DocumentTabs(
    documents: List<DocumentTab>, activeId: String, pickerPending: Boolean,
    select: (String, Boolean) -> Unit, close: (String) -> Unit, new: () -> Unit,
    list: LazyListState, input: CaptionInput?, minTabHeight: Dp, topPadding: Dp,
    interactions: DocumentTabInteractions?, moveToWindow: (String) -> Unit,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()
    var contextId by remember { mutableStateOf<String?>(null) }
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val separatorColor = MaterialTheme.colorScheme.outlineVariant
    val moveLeftLabel = stringResource(R.string.ui_tabs_move_tab_left)
    val moveRightLabel = stringResource(R.string.ui_tabs_move_tab_right)
    val moveWindowLabel = stringResource(R.string.ui_tabs_move_to_window)
    var insertion by remember { mutableStateOf<Int?>(null) }
    var dragPosition by remember { mutableStateOf<Offset?>(null) }
    var stripBounds by remember { mutableStateOf(Rect.Zero) }
    val dragged = interactions?.dragged
    val transfer = dragged
    val displayed = previewTabOrder(documents, dragged, insertion) { it.id }
    BoxWithConstraints(
        Modifier.fillMaxWidth().heightIn(min = topPadding + minTabHeight).padding(top = topPadding),
    ) {
        val availableWidth = (maxWidth - TabBarDimensions.buttonSize).coerceAtLeast(0.dp)
        val overlap = DocumentTabShapeDimensions.overlap
        val tabCount = documents.size + if (dragged != null && documents.none { it.id == dragged.id }) 1 else 0
        val sharedWidth = overlap * (tabCount - 1).coerceAtLeast(0)
        val tabWidth = ((availableWidth + sharedWidth) / tabCount.coerceAtLeast(1))
            .coerceIn(TabBarDimensions.minTabWidth, TabBarDimensions.maxTabWidth)
        val viewportWidth = if (transfer != null) availableWidth
            else (tabWidth * documents.size - sharedWidth).coerceAtMost(availableWidth)
        val density = LocalDensity.current
        LaunchedEffect(activeId, documents.map { it.id }, tabWidth, viewportWidth, density, transfer) {
            if (transfer != null) return@LaunchedEffect
            val index = documents.indexOfFirst { it.id == activeId }
            val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
            val fits = with(density) {
                item != null && item.offset >= 0 && item.offset + tabWidth.roundToPx() <= viewportWidth.roundToPx()
            }
            if (index >= 0 && !fits) list.scrollToItem(index)
        }
        val widthPx = with(density) { tabWidth.toPx() }
        val stridePx = with(density) { (tabWidth - overlap).toPx() }
        val edgePx = with(density) { 32.dp.toPx() }
        fun previewAt(index: Int) {
            if (index == insertion) return
            // Keep the viewport fixed when its first visible key moves in the preview order.
            list.requestScrollToItem(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
            insertion = index
        }
        fun updateInsertion(position: Offset) {
            val x = if (rtl) stripBounds.right - position.x else position.x - stripBounds.left
            dragPosition = Offset(x, position.y)
            val scroll = list.firstVisibleItemIndex * stridePx + list.firstVisibleItemScrollOffset
            previewAt(tabInsertionIndex(x + scroll, widthPx, stridePx, documents.size,
                documents.indexOfFirst { it.id == dragged?.id }))
        }
        LaunchedEffect(dragPosition, transfer, viewportWidth, rtl) {
            val position = dragPosition ?: return@LaunchedEffect
            if (transfer == null) return@LaunchedEffect
            val viewport = with(density) { viewportWidth.toPx() }
            val speed = when {
                position.x < edgePx -> -((edgePx - position.x) / edgePx).coerceIn(0f, 1f)
                position.x > viewport - edgePx -> ((position.x - viewport + edgePx) / edgePx).coerceIn(0f, 1f)
                else -> 0f
            }
            while (speed != 0f) {
                withFrameNanos { }
                list.scrollBy(speed * with(density) { 8.dp.toPx() })
                val scroll = list.firstVisibleItemIndex * stridePx + list.firstVisibleItemScrollOffset
                previewAt(tabInsertionIndex(position.x + scroll, widthPx, stridePx, documents.size,
                    documents.indexOfFirst { it.id == dragged?.id }))
            }
        }
        Row(Modifier.fillMaxWidth()
            // Claim the spare caption space during dragging, preserving the system's drag margin.
            .captionControl(if (transfer != null) input else null, "tab-drop-area")
            .testTag("tab-drop-area")
            .onGloballyPositioned { stripBounds = it.boundsInRoot() }
            .suiteTabDropTarget(interactions,
                moved = { updateInsertion(it) },
                exited = { insertion = null; dragPosition = null },
                dropped = { token ->
                    interactions?.move?.invoke(token, insertion ?: documents.size) == true
                }), verticalAlignment = Alignment.CenterVertically) {
            LazyRow(
                state = list,
                horizontalArrangement = Arrangement.spacedBy(-overlap),
                modifier = Modifier.width(viewportWidth).testTag("document-tabs")
                    .pointerInput(list, rtl) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Scroll && !event.keyboardModifiers.isCtrlPressed) {
                                    // Native horizontal scrolling is handled by LazyRow. Map vertical wheels to tabs.
                                    val delta = event.changes.sumOf { it.scrollDelta.y.toDouble() }.toFloat()
                                    if (event.changes.none { it.isConsumed || it.scrollDelta.x != 0f } && delta != 0f) {
                                        scope.launch { list.scrollBy(delta * TabBarDimensions.buttonSize.toPx() * if (rtl) -1f else 1f) }
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        }
                    },
            ) {
                items(displayed, key = { it.id }) { doc ->
                    val title = doc.title
                    val selected = doc.id == activeId
                    val shape = if (selected) DocumentTabShape else DocumentTabHoverShape
                    val interaction = remember { MutableInteractionSource() }
                    val accessibleTitle = if (doc.dirty) stringResource(R.string.ui_tabs_unsaved_tab, title) else title
                    val state = doc.state
                    val tabFocus = remember { FocusRequester() }
                    DisposableEffect(doc.id) {
                        focusRequesters[doc.id] = tabFocus
                        onDispose { focusRequesters.remove(doc.id) }
                    }
                    var focused by remember { mutableStateOf(false) }
                    val index = displayed.indexOf(doc)
                    val separator = !selected && displayed.getOrNull(index + 1)?.id != activeId
                    val placeholder = dragged?.id == doc.id
                    Box(Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null,
                        placementSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
                        .zIndex(if (selected) 1f else 0f).drawBehind {
                        if (separator) {
                            val width = 1.dp.toPx()
                            val height = 20.dp.toPx()
                            // Center the separator between tab bodies inside the shared feet.
                            val inset = if (index < documents.lastIndex) overlap.toPx() / 2 else 0f
                            drawRect(separatorColor, Offset(if (rtl) inset else size.width - inset - width, (size.height - height) / 2), Size(width, height))
                        }
                    }) {
                        Surface(
                            modifier = Modifier.width(tabWidth).heightIn(min = minTabHeight).testTag("document-tab-${doc.id}")
                                .captionControl(input, doc.id).clip(shape)
                                .suiteTabDragSource(interactions, doc.id, title)
                                .alpha(if (placeholder) 0.35f else 1f)
                                .then(if (placeholder && insertion != null) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                                    .testTag("tab-drag-placeholder") else Modifier)
                                .focusRequester(tabFocus).onFocusChanged { focused = it.hasFocus }
                                .then(if (focused) Modifier.border(TabBarDimensions.focusOutlineWidth, MaterialTheme.colorScheme.primary, shape) else Modifier)
                                .semantics {
                                    this.selected = selected
                                    role = Role.Tab
                                    contentDescription = accessibleTitle
                                    stateDescription = state
                                    if (interactions != null) customActions = tabMoveActions(interactions, documents, doc.id,
                                        moveLeftLabel, moveRightLabel, moveWindowLabel, moveToWindow, rtl)
                                }
                                .onPreviewKeyEvent {
                                    if (it.type != KeyEventType.KeyDown || it.isCtrlPressed) false
                                    else when (it.key) {
                                        Key.DirectionLeft, Key.DirectionRight -> {
                                            val direction = (if (it.key == Key.DirectionLeft) -1 else 1) * (if (rtl) -1 else 1)
                                            val targetIndex = Math.floorMod(index + direction, documents.size)
                                            val target = documents[targetIndex].id
                                            scope.launch {
                                                val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }
                                                if (item == null || item.offset < 0 || item.offset + item.size > list.layoutInfo.viewportEndOffset) {
                                                    list.scrollToItem(targetIndex)
                                                    // Wait for an offscreen tab's focus modifier to attach after layout.
                                                    withFrameNanos { }
                                                }
                                                focusRequesters[target]?.requestFocus()
                                            }
                                            true
                                        }
                                        Key.Menu -> { contextId = doc.id; true }
                                        Key.F10 -> if (it.isShiftPressed) { contextId = doc.id; true } else false
                                        else -> false
                                    }
                                }.pointerInput(doc.id) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                                contextId = doc.id
                                                event.changes.forEach { it.consume() }
                                            }
                                        }
                                    }
                                }.clickable(
                                    interactionSource = interaction,
                                    indication = if (selected) null else LocalIndication.current,
                                    role = Role.Tab,
                                    onClick = { select(doc.id, true) },
                                ),
                            shape = shape,
                            color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ) {
                            Row(
                                Modifier.padding(start = TabBarDimensions.contentStartPadding, end = TabBarDimensions.contentEndPadding),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                DocumentTabStatus(doc)
                                Text(title, Modifier.weight(1f).testTag("tab-title-${doc.id}"), maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, style = TabTypography.title)
                                TabIconButton(stringResource(R.string.ui_tabs_close_tab, title), R.drawable.ui_ic_tab_close,
                                    enabled = !doc.saving && !pickerPending, targetHeight = minTabHeight.coerceAtMost(TabBarDimensions.buttonSize)) { close(doc.id) }
                            }
                        }
                        AppDropdownMenu(expanded = contextId == doc.id, onDismissRequest = { contextId = null }) {
                            TabMoveMenu(interactions, documents, doc.id, moveToWindow, dismiss = { contextId = null })
                            AppMenuItem(
                                text = { Text(stringResource(R.string.ui_tabs_close_document)) },
                                trailingIcon = { Text("Ctrl+W", style = MaterialTheme.typography.labelMedium) },
                                enabled = !doc.saving && !pickerPending,
                                onClick = { contextId = null; close(doc.id) },
                            )
                        }
                    }
                }
            }
            TabIconButton(stringResource(R.string.ui_tabs_new_tab), R.drawable.ui_ic_tab_new, iconSize = 20.dp,
                modifier = Modifier.captionControl(input, "new"), targetHeight = minTabHeight.coerceAtMost(TabBarDimensions.buttonSize), onClick = new)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabIconButton(
    label: String, @DrawableRes icon: Int, modifier: Modifier = Modifier,
    enabled: Boolean = true, iconSize: Dp = 16.dp, targetSize: Dp = TabBarDimensions.buttonSize,
    targetHeight: Dp = targetSize, onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val tooltip = rememberTooltipState()
    LaunchedEffect(enabled, hovered, focused) {
        if (enabled && (hovered || focused)) {
            delay(500)
            tooltip.show(MutatePriority.UserInput)
        } else tooltip.dismiss()
    }
    val colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = tooltip,
        enableUserInput = false,
    ) {
        // Keep the hit target independent of the smaller, bounded feedback around the icon.
        Box(modifier.size(width = targetSize, height = targetHeight).semantics { contentDescription = label }
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.key == Key.Escape && tooltip.isVisible) {
                    tooltip.dismiss()
                    true
                } else false
            }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size((iconSize + 8.dp).coerceAtMost(targetHeight)).clip(CircleShape).indication(interaction, ripple(focusRingShape = CircleShape)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(icon), null, Modifier.size(iconSize), tint = if (enabled) colors.contentColor else colors.disabledContentColor)
            }
        }
    }
}

@Composable
fun SuiteWelcomeTab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(
        modifier = modifier.testTag("welcome-tab").clip(DocumentTabShape)
            .then(if (focused) Modifier.border(TabBarDimensions.focusOutlineWidth, MaterialTheme.colorScheme.primary, DocumentTabShape) else Modifier)
            .semantics { selected = true }
            .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick),
        shape = DocumentTabShape,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(Modifier.padding(start = TabBarDimensions.contentStartPadding, end = TabBarDimensions.contentEndPadding),
            contentAlignment = Alignment.CenterStart) {
            Text(stringResource(R.string.ui_tabs_welcome), style = TabTypography.title,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
