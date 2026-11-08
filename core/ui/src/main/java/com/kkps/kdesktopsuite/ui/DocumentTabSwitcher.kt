package com.kkps.kdesktopsuite.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class TabActionAnchor(val id: String, val bounds: Rect)

@Composable
internal fun DocumentSwitcher(
    documents: List<DocumentTab>, activeId: String, pickerPending: Boolean,
    select: (String, Boolean) -> Unit, close: (String) -> Unit, new: () -> Unit,
    minTabHeight: Dp, topPadding: Dp, region: Rect, input: CaptionInput?,
    interactions: DocumentTabInteractions?, moveToWindow: (String) -> Unit, onWelcome: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var actionAnchor by remember { mutableStateOf<TabActionAnchor?>(null) }
    val triggerFocus = remember { FocusRequester() }
    val active = documents.find { it.id == activeId }
    val title = active?.title ?: stringResource(R.string.ui_tabs_welcome)
    val accessibleTitle = if (active?.dirty == true) stringResource(R.string.ui_tabs_unsaved_tab, title) else title
    val switcherLabel = stringResource(R.string.ui_tabs_switch_document, accessibleTitle)
    val density = LocalDensity.current
    val view = LocalView.current
    val margin = with(density) { 8.dp.roundToPx() }
    val bounds = region.copy(left = region.left + margin, right = region.right - margin)
    val position = remember(bounds, margin) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val left = bounds.left.roundToInt().coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                val bottom = bounds.bottom.roundToInt().coerceAtMost(windowSize.height)
                val top = bounds.top.roundToInt().coerceAtLeast(0).coerceAtMost((bottom - popupContentSize.height).coerceAtLeast(0))
                return IntOffset(left, (anchorBounds.bottom + margin).coerceIn(top, (bottom - popupContentSize.height).coerceAtLeast(top)))
            }
        }
    }
    fun dismiss(restoreFocus: Boolean = false) {
        actionAnchor = null
        expanded = false
        if (restoreFocus) triggerFocus.requestFocus()
    }
    LaunchedEffect(region) { actionAnchor = null }
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = topPadding, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = minTabHeight)
                    .focusRequester(triggerFocus).testTag("document-switcher")
                    .captionControl(input, "switcher")
                    .suiteTabDropTarget(interactions, dropped = { token -> interactions?.move?.invoke(token, documents.size) == true })
                    .semantics { contentDescription = switcherLabel; stateDescription = active?.state.orEmpty() },
                shape = MaterialTheme.shapes.medium,
                border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                active?.let { DocumentTabStatus(it) }
                Text(title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TabTypography.title)
                Spacer(Modifier.width(8.dp))
                Icon(painterResource(R.drawable.ui_ic_tab_expand), null, Modifier.size(20.dp))
            }
            if (expanded) Popup(popupPositionProvider = position, onDismissRequest = { dismiss(true) },
                properties = PopupProperties(focusable = true)) {
                DocumentSwitcherPanel(documents, activeId, pickerPending, select, close, new, interactions,
                    moveToWindow = { dismiss(); moveToWindow(it) }, onWelcome = { dismiss(); onWelcome() },
                    compactActions = with(density) { bounds.width.toDp() } < 280.dp,
                    showActions = { id, screenBounds ->
                        // Place this sibling popup in the host window, avoiding nested-popup offsets.
                        val screen = IntArray(2); val window = IntArray(2)
                        view.getLocationOnScreen(screen); view.getLocationInWindow(window)
                        actionAnchor = TabActionAnchor(id, screenBounds.translate(Offset((window[0] - screen[0]).toFloat(),
                            (window[1] - screen[1]).toFloat())))
                    }, dismiss = { dismiss(it) },
                    modifier = Modifier.width(with(density) { bounds.width.coerceAtLeast(0f).toDp() })
                        .heightIn(max = with(density) { (region.height * 0.6f).coerceAtLeast(0f).toDp() }))
            }
        }
        TabIconButton(stringResource(R.string.ui_tabs_new_tab), R.drawable.ui_ic_tab_new, iconSize = 20.dp,
            modifier = Modifier.captionControl(input, "new"), onClick = new)
    }
    val anchor = actionAnchor
    val doc = documents.find { it.id == anchor?.id }
    if (expanded && anchor != null && doc != null && interactions != null) {
        val gap = with(density) { 4.dp.roundToPx() }
        val actionPosition = remember(anchor, region, gap, margin) { TabActionMenuPosition(anchor.bounds, region, gap, margin) }
        Popup(popupPositionProvider = actionPosition, onDismissRequest = { actionAnchor = null },
            properties = PopupProperties(focusable = true)) {
            Surface(Modifier.width(with(density) { bounds.width.toDp().coerceAtMost(240.dp) })
                .testTag("document-tab-actions-menu"), shape = MenuDefaults.shape, color = MenuDefaults.containerColor,
                tonalElevation = MenuDefaults.TonalElevation, shadowElevation = MenuDefaults.ShadowElevation) {
                Column {
                    tabMoveActions(interactions, documents, doc.id, stringResource(R.string.ui_tabs_move_tab_up),
                        stringResource(R.string.ui_tabs_move_tab_down), stringResource(R.string.ui_tabs_move_to_window),
                        { dismiss(); moveToWindow(it) }).forEach { action ->
                        AppMenuItem(text = { Text(action.label) }, modifier = Modifier.heightIn(min = 48.dp),
                            onClick = { actionAnchor = null; action.action() })
                    }
                }
            }
        }
    }
}

/** Coordinates are in the host window, including when its origin is offset by a caption or pane. */
internal class TabActionMenuPosition(private val anchor: Rect, private val region: Rect, private val gap: Int,
    private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection,
        popupContentSize: IntSize): IntOffset {
        val minX = (region.left.roundToInt() + margin).coerceAtLeast(0)
        val maxX = (region.right.roundToInt() - margin - popupContentSize.width).coerceAtLeast(minX)
        val minY = (region.top.roundToInt() + margin).coerceAtLeast(0)
        val maxY = (region.bottom.roundToInt() - margin - popupContentSize.height).coerceAtLeast(minY)
        val x = if (layoutDirection == LayoutDirection.Rtl) anchor.left.roundToInt() else anchor.right.roundToInt() - popupContentSize.width
        val below = anchor.bottom.roundToInt() + gap
        val y = if (below <= maxY) below else anchor.top.roundToInt() - gap - popupContentSize.height
        return IntOffset(x.coerceIn(minX, maxX), y.coerceIn(minY, maxY))
    }
}

@Composable
private fun DocumentSwitcherPanel(
    documents: List<DocumentTab>, activeId: String, pickerPending: Boolean,
    select: (String, Boolean) -> Unit, close: (String) -> Unit, new: () -> Unit,
    interactions: DocumentTabInteractions?, moveToWindow: (String) -> Unit, onWelcome: () -> Unit,
    compactActions: Boolean, showActions: (String, Rect) -> Unit, dismiss: (Boolean) -> Unit, modifier: Modifier,
) {
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val handleBounds = remember { mutableMapOf<String, Rect>() }
    val actionBounds = remember { mutableMapOf<String, Rect>() }
    var viewport by remember { mutableStateOf(Rect.Zero) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var insertion by remember { mutableStateOf<Int?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var grabOffset by remember { mutableFloatStateOf(0f) }
    val dragged = documents.find { it.id == draggedId }
    val displayed = previewTabOrder(documents, dragged, insertion) { it.id }
    val currentDocuments by rememberUpdatedState(documents)
    val currentDisplayed by rememberUpdatedState(displayed)
    val currentInteractions by rememberUpdatedState(interactions)
    val upLabel = stringResource(R.string.ui_tabs_move_tab_up)
    val downLabel = stringResource(R.string.ui_tabs_move_tab_down)
    val windowLabel = stringResource(R.string.ui_tabs_move_to_window)
    val density = LocalDensity.current

    fun cancelDrag() { draggedId = null; insertion = null }
    fun updateInsertion() {
        if (draggedId == null || pointer.x !in viewport.left..viewport.right || currentDocuments.isEmpty()) return
        val y = (pointer.y - viewport.top).coerceIn(0f, viewport.height)
        val visible = list.layoutInfo.visibleItemsInfo
        val next = visible.firstOrNull { it.key != draggedId && it.offset + it.size / 2f > y }
        val gap = next?.index ?: ((visible.lastOrNull()?.index ?: -1) + 1)
        val from = currentDisplayed.indexOfFirst { it.id == draggedId }
        val target = (gap - if (from in 0 until gap) 1 else 0).coerceIn(0, currentDocuments.lastIndex)
        if (insertion != target) {
            list.requestScrollToItem(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
            insertion = target
        }
    }
    fun finishDrag(position: Offset) {
        pointer = position
        updateInsertion()
        val id = draggedId
        val target = insertion
        if (id != null && target != null && viewport.contains(position) &&
            currentDocuments.indexOfFirst { it.id == id } != target) currentInteractions?.reorder?.invoke(id, target)
        cancelDrag()
    }
    fun focusTab(index: Int) {
        val target = documents[index]
        scope.launch { list.scrollToItem(index); withFrameNanos { }; focusRequesters[target.id]?.requestFocus() }
    }
    val ids = documents.map { it.id }
    LaunchedEffect(ids.toSet(), viewport.size) { cancelDrag() }
    LaunchedEffect(Unit) {
        if (documents.isNotEmpty()) {
            list.scrollToItem(documents.indexOfFirst { it.id == activeId }.coerceAtLeast(0))
            withFrameNanos { }
            focusRequesters[activeId]?.requestFocus()
        }
    }
    LaunchedEffect(draggedId) {
        val edge = with(density) { 64.dp.toPx() }.coerceAtMost(viewport.height / 3f)
        val maxSpeed = with(density) { 900.dp.toPx() }
        var lastFrame = withFrameNanos { it }
        while (draggedId != null) {
            val frame = withFrameNanos { it }
            val seconds = ((frame - lastFrame) / 1_000_000_000f).coerceAtMost(0.032f)
            lastFrame = frame
            val speed = tabEdgeScrollFraction(pointer - viewport.topLeft, viewport.width, viewport.height, edge)
            if (speed != 0f) {
                list.scrollBy(speed * maxSpeed * seconds)
                updateInsertion()
            }
        }
    }
    Surface(modifier.testTag("document-switcher-panel").onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
            if (draggedId != null) cancelDrag() else dismiss(true)
            true
        } else false
    }, shape = MaterialTheme.shapes.medium, color = MenuDefaults.containerColor,
        tonalElevation = MenuDefaults.TonalElevation, shadowElevation = MenuDefaults.ShadowElevation) {
        Column {
            Box(Modifier.weight(1f, fill = false).fillMaxWidth().clipToBounds().onGloballyPositioned { viewport = it.boundsInRoot() }
                .testTag("document-switcher-viewport")
                .pointerInput(interactions != null) {
                    if (interactions == null) return@pointerInput
                    // Keep the gesture on this stable viewport while the lazy rows move or recycle.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val id = handleBounds.entries.firstOrNull { it.value.contains(down.position + viewport.topLeft) }?.key
                            ?: return@awaitEachGesture
                        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id } ?: return@awaitEachGesture
                        var started = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || change.isConsumed || event.changes.count { it.pressed } > 1) { cancelDrag(); break }
                            if (!change.pressed) {
                                if (started) { finishDrag(change.position + viewport.topLeft); change.consume() }
                                break
                            }
                            if (!started && (change.position - down.position).getDistance() >= viewConfiguration.touchSlop) {
                                started = true
                                grabOffset = down.position.y - item.offset
                                draggedId = id
                                insertion = currentDisplayed.indexOfFirst { it.id == id }
                            }
                            if (started) { change.consume(); pointer = change.position + viewport.topLeft; updateInsertion() }
                        }
                    }
                }) {
                LazyColumn(state = list, modifier = Modifier.fillMaxWidth().testTag("document-switcher-list")) {
                    if (documents.isEmpty()) item {
                        AppMenuItem(text = { Text(stringResource(R.string.ui_tabs_welcome), style = TabTypography.title) },
                            modifier = Modifier.heightIn(min = 48.dp).background(MaterialTheme.colorScheme.secondaryContainer)
                                .testTag("welcome-tab").semantics { selected = true },
                            onClick = onWelcome)
                    }
                    items(displayed, key = { it.id }) { doc ->
                        val index = documents.indexOfFirst { it.id == doc.id }
                        val actions = if (interactions == null) emptyList() else tabMoveActions(interactions, documents,
                            doc.id, upLabel, downLabel, windowLabel, moveToWindow)
                        val focus = remember { FocusRequester() }
                        DisposableEffect(doc.id) {
                            focusRequesters[doc.id] = focus
                            onDispose { focusRequesters.remove(doc.id); handleBounds.remove(doc.id); actionBounds.remove(doc.id) }
                        }
                        DocumentSwitcherRow(doc, selected = doc.id == activeId, pickerPending = pickerPending,
                            actions = actions, compactActions = compactActions, focus = focus,
                            showHandle = interactions != null, showActions = { actionBounds[doc.id]?.let { showActions(doc.id, it) } },
                            onHandleBounds = { handleBounds[doc.id] = it }, onActionBounds = { actionBounds[doc.id] = it },
                            select = { dismiss(false); select(doc.id, true) }, close = { dismiss(false); close(doc.id) },
                            modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null,
                                placementSpec = if (draggedId == doc.id) null else spring(dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow))
                                .alpha(if (draggedId == doc.id) 0f else 1f)
                                .onPreviewKeyEvent {
                                    if (it.type != KeyEventType.KeyDown || it.isCtrlPressed || documents.isEmpty()) false
                                    else when (it.key) {
                                        Key.DirectionUp -> { focusTab(Math.floorMod(index - 1, documents.size)); true }
                                        Key.DirectionDown -> { focusTab((index + 1) % documents.size); true }
                                        Key.Menu -> { actionBounds[doc.id]?.let { showActions(doc.id, it) }; true }
                                        Key.F10 -> if (it.isShiftPressed) { actionBounds[doc.id]?.let { showActions(doc.id, it) }; true } else false
                                        else -> false
                                    }
                                })
                    }
                }
                if (dragged != null) Surface(Modifier.fillMaxWidth().offset {
                    IntOffset(0, (pointer.y - viewport.top - grabOffset).roundToInt())
                }.clearAndSetSemantics { testTag = "document-drag-preview" }, shape = MaterialTheme.shapes.small,
                    shadowElevation = 6.dp, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    DocumentSwitcherRow(dragged, selected = dragged.id == activeId, pickerPending = true,
                        actions = if (interactions == null) emptyList() else tabMoveActions(interactions, documents,
                            dragged.id, upLabel, downLabel, windowLabel, moveToWindow),
                        compactActions = compactActions, showHandle = interactions != null,
                        select = {}, close = {}, overlay = true)
                }
            }
            HorizontalDivider()
            TextButton(onClick = { dismiss(false); new() }, modifier = Modifier.fillMaxWidth()
                .heightIn(min = 48.dp).testTag("document-switcher-new"), shape = MaterialTheme.shapes.extraSmall) {
                Icon(painterResource(R.drawable.ui_ic_tab_new), null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ui_tabs_new_tab_button))
            }
        }
    }
}

/** Frame-rate-independent edge scrolling, including a finger held just beyond the list. */
internal fun tabEdgeScrollFraction(pointer: Offset, width: Float, height: Float, edge: Float): Float {
    if (pointer.x !in 0f..width || edge <= 0f) return 0f
    return when {
        pointer.y < edge -> -((edge - pointer.y) / edge).coerceIn(0f, 1f)
        pointer.y > height - edge -> ((pointer.y - height + edge) / edge).coerceIn(0f, 1f)
        else -> 0f
    }
}

@Composable
private fun DocumentSwitcherRow(
    doc: DocumentTab, selected: Boolean, pickerPending: Boolean, actions: List<CustomAccessibilityAction>,
    compactActions: Boolean, showHandle: Boolean, select: () -> Unit, close: () -> Unit,
    modifier: Modifier = Modifier, focus: FocusRequester? = null, showActions: () -> Unit = {},
    onHandleBounds: (Rect) -> Unit = {}, onActionBounds: (Rect) -> Unit = {}, overlay: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val label = if (doc.dirty) stringResource(R.string.ui_tabs_unsaved_tab, doc.title) else doc.title
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp)
        .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)
        .then(if (!overlay) Modifier.testTag("document-entry-${doc.id}") else Modifier), verticalAlignment = Alignment.CenterVertically) {
        val handleLabel = stringResource(R.string.ui_tabs_reorder_tab, doc.title)
        if (showHandle) Box(Modifier.size(48.dp).then(if (overlay) Modifier else Modifier
            .onGloballyPositioned {
                onHandleBounds(it.boundsInRoot())
                if (compactActions) onActionBounds(Rect(it.positionOnScreen(), it.size.toSize()))
            }.testTag("document-handle-${doc.id}")
            .then(if (compactActions) Modifier.clickable(onClick = showActions) else Modifier)
            .semantics { contentDescription = handleLabel; customActions = actions }), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ui_ic_tab_drag), null, Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.weight(1f).heightIn(min = 48.dp)
            .then(if (focus == null) Modifier else Modifier.focusRequester(focus))
            .onFocusChanged { focused = it.isFocused }
            .then(if (focused && !overlay) Modifier.border(TabBarDimensions.focusOutlineWidth, MaterialTheme.colorScheme.primary) else Modifier)
            .then(if (overlay) Modifier else Modifier.testTag("document-tab-${doc.id}").semantics {
                this.selected = selected; role = Role.Tab; contentDescription = label
                stateDescription = doc.state; customActions = actions
            }.clickable(role = Role.Tab, onClick = select))
            .padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            DocumentTabStatus(doc)
            Column(Modifier.weight(1f)) {
                Text(doc.title, style = TabTypography.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (doc.state.isNotEmpty()) Text(doc.state, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (actions.isNotEmpty() && !compactActions) {
            TabIconButton(stringResource(R.string.ui_tabs_tab_actions, doc.title), R.drawable.ui_ic_tab_more,
                modifier = if (overlay) Modifier else Modifier.testTag("document-actions-${doc.id}")
                    .onGloballyPositioned { onActionBounds(Rect(it.positionOnScreen(), it.size.toSize())) },
                onClick = showActions)
        }
        TabIconButton(stringResource(R.string.ui_tabs_close_tab, doc.title), R.drawable.ui_ic_tab_close,
            enabled = !doc.saving && !pickerPending, onClick = close)
    }
}
