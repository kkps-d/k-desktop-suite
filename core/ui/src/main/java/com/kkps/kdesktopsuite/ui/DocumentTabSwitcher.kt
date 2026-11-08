package com.kkps.kdesktopsuite.ui

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
import androidx.compose.ui.platform.LocalDensity
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

@Composable
internal fun DocumentSwitcher(
    documents: List<DocumentTab>, activeId: String, pickerPending: Boolean,
    select: (String, Boolean) -> Unit, close: (String) -> Unit, new: () -> Unit,
    minTabHeight: Dp, topPadding: Dp, region: Rect, input: CaptionInput?,
    interactions: DocumentTabInteractions?, moveToWindow: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val triggerFocus = remember { FocusRequester() }
    val active = documents.find { it.id == activeId }
    val title = active?.title.orEmpty()
    val accessibleTitle = if (active?.dirty == true) stringResource(R.string.ui_tabs_unsaved_tab, title) else title
    val switcherLabel = stringResource(R.string.ui_tabs_switch_document, accessibleTitle)
    val density = LocalDensity.current
    val margin = with(density) { 8.dp.toPx() }
    val bounds = region.copy(left = region.left + margin, right = region.right - margin)
    val position = remember(bounds) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val left = bounds.left.roundToInt().coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                val bottom = bounds.bottom.roundToInt().coerceAtMost(windowSize.height)
                val top = bounds.top.roundToInt().coerceAtLeast(0).coerceAtMost((bottom - popupContentSize.height).coerceAtLeast(0))
                return IntOffset(left, anchorBounds.bottom.coerceIn(top, (bottom - popupContentSize.height).coerceAtLeast(top)))
            }
        }
    }
    fun dismiss(restoreFocus: Boolean = false) {
        expanded = false
        if (restoreFocus) triggerFocus.requestFocus()
    }
    Row(Modifier.fillMaxWidth().padding(top = topPadding), verticalAlignment = Alignment.CenterVertically) {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.weight(1f).heightIn(min = minTabHeight)
                .focusRequester(triggerFocus).testTag("document-switcher")
                .captionControl(input, "switcher")
                .suiteTabDropTarget(interactions, dropped = { token ->
                    interactions?.move?.invoke(token, documents.size) == true
                })
                .semantics { contentDescription = switcherLabel; stateDescription = active?.state.orEmpty() },
            shape = DocumentTabShape,
            colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface),
            contentPadding = PaddingValues(start = TabBarDimensions.contentStartPadding, end = TabBarDimensions.contentEndPadding),
        ) {
            active?.let { DocumentTabStatus(it) }
            Text(title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TabTypography.title)
            Icon(painterResource(R.drawable.ui_ic_tab_expand), null, Modifier.size(16.dp))
        }
        TabIconButton(stringResource(R.string.ui_tabs_new_tab), R.drawable.ui_ic_tab_new, iconSize = 20.dp,
            modifier = Modifier.captionControl(input, "new"), onClick = new)
        if (expanded) {
            Popup(popupPositionProvider = position, onDismissRequest = { dismiss(true) },
                properties = PopupProperties(focusable = true)) {
                DocumentSwitcherPanel(documents, activeId, pickerPending, select, close, new,
                    interactions, moveToWindow, compactActions = with(density) { bounds.width.toDp() } < 280.dp,
                    dismiss = { dismiss(it) },
                    modifier = Modifier.width(with(density) { bounds.width.coerceAtLeast(0f).toDp() })
                        .height(with(density) { (region.height * 0.6f).coerceAtLeast(0f).toDp() }))
            }
        }
    }
}

@Composable
private fun DocumentSwitcherPanel(
    documents: List<DocumentTab>, activeId: String, pickerPending: Boolean,
    select: (String, Boolean) -> Unit, close: (String) -> Unit, new: () -> Unit,
    interactions: DocumentTabInteractions?, moveToWindow: (String) -> Unit,
    compactActions: Boolean, dismiss: (Boolean) -> Unit, modifier: Modifier,
) {
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val handleBounds = remember { mutableMapOf<String, Rect>() }
    var viewport by remember { mutableStateOf(Rect.Zero) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var insertion by remember { mutableStateOf<Int?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var actionId by remember { mutableStateOf<String?>(null) }
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
        if (draggedId == null || !viewport.contains(pointer)) return
        val y = pointer.y - viewport.top
        val visible = list.layoutInfo.visibleItemsInfo
        val next = visible.firstOrNull { it.key != draggedId && it.offset + it.size / 2f > y }
        val gap = next?.index ?: ((visible.lastOrNull()?.index ?: -1) + 1)
        val from = currentDisplayed.indexOfFirst { it.id == draggedId }
        val target = (gap - if (from in 0 until gap) 1 else 0).coerceIn(0, currentDocuments.lastIndex)
        if (insertion != target) {
            // A moved lazy-list key must not pull the viewport along with it.
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
        scope.launch {
            list.scrollToItem(index)
            withFrameNanos { }
            focusRequesters[target.id]?.requestFocus()
        }
    }
    val ids = documents.map { it.id }
    LaunchedEffect(ids.toSet(), viewport.size) { cancelDrag() }
    LaunchedEffect(Unit) {
        val index = documents.indexOfFirst { it.id == activeId }.coerceAtLeast(0)
        list.scrollToItem(index)
        withFrameNanos { }
        focusRequesters[activeId]?.requestFocus()
    }
    LaunchedEffect(draggedId) {
        val edge = with(density) { 32.dp.toPx() }
        while (draggedId != null) {
            withFrameNanos { }
            if (!viewport.contains(pointer)) continue
            val y = pointer.y - viewport.top
            val speed = when {
                y < edge -> -((edge - y) / edge).coerceIn(0f, 1f)
                y > viewport.height - edge -> ((y - viewport.height + edge) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (speed != 0f) {
                list.scrollBy(speed * with(density) { 8.dp.toPx() })
                updateInsertion()
            }
        }
    }
    Surface(modifier.testTag("document-switcher-panel").onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
            if (draggedId != null) cancelDrag() else dismiss(true)
            true
        } else false
    }, shape = MenuDefaults.shape, color = MenuDefaults.containerColor,
        tonalElevation = MenuDefaults.TonalElevation, shadowElevation = MenuDefaults.ShadowElevation) {
        Column {
            Box(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { viewport = it.boundsInRoot() }
                .testTag("document-switcher-viewport")
                .pointerInput(interactions != null) {
                    if (interactions == null) return@pointerInput
                    // Observe at the stable viewport, before scrolling consumes handle gestures.
                    // This is a local pointer gesture, never an Android window-transfer drag.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val id = handleBounds.entries.firstOrNull { it.value.contains(down.position + viewport.topLeft) }?.key
                            ?: return@awaitEachGesture
                        var started = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || change.isConsumed || event.changes.count { it.pressed } > 1) {
                                cancelDrag()
                                break
                            }
                            if (!change.pressed) {
                                if (started) { finishDrag(change.position + viewport.topLeft); change.consume() }
                                break
                            }
                            if (!started && (change.position - down.position).getDistance() >= viewConfiguration.touchSlop) {
                                started = true
                                draggedId = id
                                insertion = currentDisplayed.indexOfFirst { it.id == id }
                            }
                            if (started) {
                                change.consume()
                                pointer = change.position + viewport.topLeft
                                updateInsertion()
                            }
                        }
                    }
                }) {
                LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("document-switcher-list")) {
                    items(displayed, key = { it.id }) { doc ->
                        val index = documents.indexOfFirst { it.id == doc.id }
                        val actions = if (interactions == null) emptyList() else tabMoveActions(interactions, documents,
                            doc.id, upLabel, downLabel, windowLabel, { dismiss(false); moveToWindow(it) })
                        val focus = remember { FocusRequester() }
                        DisposableEffect(doc.id) {
                            focusRequesters[doc.id] = focus
                            onDispose { focusRequesters.remove(doc.id); handleBounds.remove(doc.id) }
                        }
                        var focused by remember { mutableStateOf(false) }
                        val selected = doc.id == activeId
                        val label = if (doc.dirty) stringResource(R.string.ui_tabs_unsaved_tab, doc.title) else doc.title
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                            .then(if (draggedId == doc.id) Modifier.border(2.dp, MaterialTheme.colorScheme.primary)
                                .testTag("tab-drag-placeholder") else Modifier)
                            .testTag("document-entry-${doc.id}"), verticalAlignment = Alignment.CenterVertically) {
                            if (interactions != null) {
                                val handleLabel = stringResource(R.string.ui_tabs_reorder_tab, doc.title)
                                Box(Modifier.size(48.dp).onGloballyPositioned { handleBounds[doc.id] = it.boundsInRoot() }
                                    .testTag("document-handle-${doc.id}")
                                    .then(if (compactActions) Modifier.clickable { actionId = doc.id } else Modifier)
                                    .semantics { contentDescription = handleLabel; customActions = actions },
                                    contentAlignment = Alignment.Center) {
                                    Icon(painterResource(R.drawable.ui_ic_tab_drag), null, Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (compactActions) AppDropdownMenu(actionId == doc.id, { actionId = null }) {
                                        actions.forEach { action ->
                                            AppMenuItem(text = { Text(action.label) }, onClick = { actionId = null; action.action() })
                                        }
                                    }
                                }
                            }
                            Row(Modifier.weight(1f).heightIn(min = 48.dp).focusRequester(focus)
                                .onFocusChanged { focused = it.isFocused }
                                .then(if (focused) Modifier.border(TabBarDimensions.focusOutlineWidth, MaterialTheme.colorScheme.primary) else Modifier)
                                .testTag("document-tab-${doc.id}").semantics {
                                    this.selected = selected; role = Role.Tab; contentDescription = label
                                    stateDescription = doc.state; customActions = actions
                                }.onPreviewKeyEvent {
                                    if (it.type != KeyEventType.KeyDown || it.isCtrlPressed) false
                                    else when (it.key) {
                                        Key.DirectionUp -> { focusTab(Math.floorMod(index - 1, documents.size)); true }
                                        Key.DirectionDown -> { focusTab((index + 1) % documents.size); true }
                                        Key.Menu -> { actionId = doc.id; true }
                                        Key.F10 -> if (it.isShiftPressed) { actionId = doc.id; true } else false
                                        else -> false
                                    }
                                }.clickable(role = Role.Tab) { dismiss(false); select(doc.id, true) }
                                .padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                DocumentTabStatus(doc)
                                Column(Modifier.weight(1f)) {
                                    Text(doc.title, style = TabTypography.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (doc.state.isNotEmpty()) Text(doc.state, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                if (selected) Text("✓", Modifier.padding(start = 4.dp))
                            }
                            if (actions.isNotEmpty() && !compactActions) Box {
                                TabIconButton(stringResource(R.string.ui_tabs_tab_actions, doc.title), R.drawable.ui_ic_tab_more,
                                    modifier = Modifier.testTag("document-actions-${doc.id}")) { actionId = doc.id }
                                AppDropdownMenu(actionId == doc.id, { actionId = null }) {
                                    actions.forEach { action ->
                                        AppMenuItem(text = { Text(action.label) }, onClick = { actionId = null; action.action() })
                                    }
                                }
                            }
                            TabIconButton(stringResource(R.string.ui_tabs_close_tab, doc.title), R.drawable.ui_ic_tab_close,
                                enabled = !doc.saving && !pickerPending) { dismiss(false); close(doc.id) }
                        }
                    }
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
