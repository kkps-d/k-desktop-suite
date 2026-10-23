@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.kkps.kdesktopsuite.notepad

import android.content.ClipData
import android.content.Intent
import android.os.Build
import android.view.WindowInsetsController
import androidx.annotation.StringRes
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.TransferableContent
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.text.contextmenu.modifier.filterTextContextMenuComponents
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal object TabBarDimensions {
    val topPadding = 8.dp
    val minTabWidth = 120.dp
    val maxTabWidth = 240.dp
    val contentStartPadding = 20.dp
    val contentEndPadding = 8.dp
    val buttonSize = 48.dp
    val minBarHeight get() = topPadding + buttonSize
    val focusOutlineWidth = 2.dp
}

internal enum class NotepadCommand(@StringRes val label: Int, val shortcut: String) {
    New(R.string.new_document, "Ctrl+N"), Open(R.string.open_document, "Ctrl+O"),
    Save(R.string.save_document, "Ctrl+S"), SaveAs(R.string.save_as, "Ctrl+Shift+S"), Close(R.string.close_document, "Ctrl+W"),
    Undo(R.string.undo, "Ctrl+Z"), Redo(R.string.redo, "Ctrl+Y"),
    Cut(R.string.cut, "Ctrl+X"), Copy(R.string.copy, "Ctrl+C"), Paste(R.string.paste, "Ctrl+V"), SelectAll(R.string.select_all, "Ctrl+A"),
    ZoomIn(R.string.zoom_in, "Ctrl++"), ZoomOut(R.string.zoom_out, "Ctrl+−"), ZoomReset(R.string.zoom_reset, "Ctrl+0"),
}

@Composable
internal fun documentTitle(doc: DocumentSession): String = doc.name ?: stringResource(R.string.untitled, doc.untitledNumber)

@Composable
internal fun NotepadScreen(
    notepad: NotepadViewModel,
    activity: NotepadActivity? = null,
    contentInsets: WindowInsets = WindowInsets.safeDrawing.union(WindowInsets.ime),
    adaptive: WindowAdaptiveInfo = currentWindowAdaptiveInfoV2(),
    caption: DesktopCaption = rememberDesktopCaption(activity != null),
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val active = notepad.active
    val tabs = rememberLazyListState()
    val headerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    SideEffect {
        if (activity != null && Build.VERSION.SDK_INT >= 35) {
            activity.window.insetsController?.setSystemBarsAppearance(
                if (headerColor.luminance() > 0.5f) WindowInsetsController.APPEARANCE_LIGHT_CAPTION_BARS else 0,
                WindowInsetsController.APPEARANCE_LIGHT_CAPTION_BARS,
            )
        }
    }
    // Only the header enters caption space. Restore any status bar/cutout protection that
    // overlaps the caption inset instead of dropping all top system insets.
    val layoutInsets = if (caption.height > 0f) {
        contentInsets.exclude(WindowInsets(top = caption.height.roundToInt()))
            .union(WindowInsets(top = caption.safeTop.roundToInt()))
    } else contentInsets
    val contextCommands = NotepadCommand.entries.filter { it in NotepadCommand.Undo..NotepadCommand.SelectAll }
    val contextLabels = contextCommands.associateWith { stringResource(it.label) }
    val compact = !adaptive.windowSizeClass.isWidthAtLeastBreakpoint(600) || !adaptive.windowSizeClass.isHeightAtLeastBreakpoint(480)

    fun enabled(command: NotepadCommand): Boolean = !notepad.loading && when (command) {
        NotepadCommand.Open -> !notepad.pickerPending
        NotepadCommand.Save, NotepadCommand.SaveAs, NotepadCommand.Close -> active != null && !active.saving && !notepad.pickerPending
        NotepadCommand.Undo -> active?.editor?.undoState?.canUndo == true
        NotepadCommand.Redo -> active?.editor?.undoState?.canRedo == true
        NotepadCommand.Cut, NotepadCommand.Copy -> active?.editor?.selection?.collapsed == false
        NotepadCommand.ZoomIn -> notepad.zoom < 300
        NotepadCommand.ZoomOut -> notepad.zoom > 50
        else -> active != null
    }

    fun run(command: NotepadCommand) {
        if (!enabled(command)) return
        val doc = notepad.active ?: return
        when (command) {
            NotepadCommand.New -> notepad.newDocument()
            NotepadCommand.Open -> notepad.requestOpen()
            NotepadCommand.Save -> notepad.save()
            NotepadCommand.SaveAs -> notepad.requestSaveAs()
            NotepadCommand.Close -> notepad.requestClose(doc.id)
            NotepadCommand.Undo -> { doc.editor.undoState.undo(); focus.requestFocus() }
            NotepadCommand.Redo -> { doc.editor.undoState.redo(); focus.requestFocus() }
            NotepadCommand.SelectAll -> {
                doc.editor.edit { selection = TextRange(0, length) }
                focus.requestFocus()
            }
            NotepadCommand.ZoomIn -> notepad.changeZoom(notepad.zoom + 10)
            NotepadCommand.ZoomOut -> notepad.changeZoom(notepad.zoom - 10)
            NotepadCommand.ZoomReset -> notepad.changeZoom(100)
            NotepadCommand.Cut, NotepadCommand.Copy, NotepadCommand.Paste -> scope.launch {
                try {
                    if (command == NotepadCommand.Paste) {
                        val data = clipboard.getClipEntry()?.clipData
                        val text = if (data != null && data.itemCount > 0) data.getItemAt(0).text?.toString() else null
                        if (text != null) doc.editor.edit {
                            val start = selection.min
                            replace(start, selection.max, text)
                            selection = TextRange(start + text.length)
                        }
                    } else {
                        val selection = doc.editor.selection
                        val text = doc.editor.text.toString()
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, text.substring(selection.min, selection.max))))
                        if (command == NotepadCommand.Cut && doc.editor.text.toString() == text && doc.editor.selection == selection) {
                            doc.editor.edit {
                                replace(selection.min, selection.max, "")
                                this.selection = TextRange(selection.min)
                            }
                        }
                    }
                    if (notepad.activeId == doc.id) focus.requestFocus()
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    notepad.reportError(R.string.error_clipboard)
                }
            }
        }
    }

    LaunchedEffect(notepad.focusRequest) {
        if (active != null) focus.requestFocus()
    }

    val receiveContent = remember(notepad, activity) {
        ReceiveContentListener { content ->
            val data = content.clipEntry.clipData
            val hasUri = (0 until data.itemCount).any { data.getItemAt(it).uri != null }
            if (content.source == TransferableContent.Source.DragAndDrop && hasUri) {
                if (data.itemCount == 1) {
                    val uri = data.getItemAt(0).uri
                    activity?.retainPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    notepad.openUri(uri.toString())
                } else notepad.reportError(R.string.error_drop)
                null
            } else content
        }
    }

    Surface(Modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier.fillMaxSize().windowInsetsPadding(layoutInsets)
                .contentReceiver(receiveContent)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed || notepad.closeConfirmation != null || notepad.conflict != null) false
                    else if (event.key == Key.Tab) {
                        notepad.cycleTab(if (event.isShiftPressed) -1 else 1)
                        true
                    } else {
                        val command = when (event.key) {
                            Key.N -> NotepadCommand.New
                            Key.O -> NotepadCommand.Open
                            Key.S -> if (event.isShiftPressed) NotepadCommand.SaveAs else NotepadCommand.Save
                            Key.W -> NotepadCommand.Close
                            Key.Z -> if (event.isShiftPressed) NotepadCommand.Redo else NotepadCommand.Undo
                            Key.Y -> NotepadCommand.Redo
                            Key.X -> NotepadCommand.Cut
                            Key.C -> NotepadCommand.Copy
                            Key.V -> NotepadCommand.Paste
                            Key.A -> NotepadCommand.SelectAll
                            Key.Equals, Key.Plus, Key.NumPadAdd -> NotepadCommand.ZoomIn
                            Key.Minus, Key.NumPadSubtract -> NotepadCommand.ZoomOut
                            Key.Zero, Key.NumPad0 -> NotepadCommand.ZoomReset
                            else -> null
                        }
                        if (command == null) false else { run(command); true }
                    }
                },
        ) {
            val density = LocalDensity.current
            var origin by remember { mutableStateOf(Offset.Zero) }
            val width = with(density) { maxWidth.toPx() }
            val height = with(density) { maxHeight.toPx() }
            var region = Rect(0f, 0f, width, height)
            adaptive.windowPosture.hingeList.filter { it.isSeparating || it.isOccluding }.forEach { hinge ->
                val bounds = hinge.bounds.translate(-origin)
                if (region.overlaps(bounds)) {
                    val candidates = if (hinge.isVertical) listOf(
                        Rect(region.left, region.top, bounds.left.coerceIn(region.left, region.right), region.bottom),
                        Rect(bounds.right.coerceIn(region.left, region.right), region.top, region.right, region.bottom),
                    ) else listOf(
                        Rect(region.left, region.top, region.right, bounds.top.coerceIn(region.top, region.bottom)),
                        Rect(region.left, bounds.bottom.coerceIn(region.top, region.bottom), region.right, region.bottom),
                    )
                    region = candidates.maxBy { it.width * it.height }
                }
            }
            Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() })
            Column(
                Modifier.absoluteOffset { IntOffset(region.left.roundToInt(), region.top.roundToInt()) }
                    .size(with(density) { region.width.toDp() }, with(density) { region.height.toDp() }),
            ) {
                if (notepad.loading) {
                    Spacer(Modifier.height(with(density) { (caption.height - origin.y - region.top).coerceAtLeast(0f).toDp() }))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.loading), Modifier.padding(NotepadSpacing.medium))
                } else {
                    DocumentHeader(notepad.documents, notepad.activeId, notepad.pickerPending,
                        notepad::select, notepad::requestClose, notepad::newDocument,
                        caption, region.translate(origin), tabs, notepad.errorDocumentId)
                    CommandBar(compact, ::enabled, ::run)
                    if (notepad.opening > 0) LinearProgressIndicator(Modifier.fillMaxWidth())
                    notepad.errorMessage?.let { message ->
                        Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = NotepadSpacing.medium)) {
                                Text(stringResource(message), Modifier.padding(top = NotepadSpacing.small), color = MaterialTheme.colorScheme.onErrorContainer)
                                Row {
                                    notepad.errorDocumentId?.let { id ->
                                        TextButton(onClick = { notepad.requestSaveAs(id) }, enabled = !notepad.pickerPending) {
                                            Text(stringResource(R.string.save_as))
                                        }
                                    }
                                    TextButton(onClick = notepad::dismissError) { Text(stringResource(R.string.dismiss)) }
                                }
                            }
                        }
                    }
                    if (active != null) {
                        DocumentEditor(active, notepad.zoom, notepad::changeZoom, focus,
                            Modifier.weight(1f)
                                .filterTextContextMenuComponents {
                                    it.key !in setOf(TextContextMenuKeys.CutKey, TextContextMenuKeys.CopyKey,
                                        TextContextMenuKeys.PasteKey, TextContextMenuKeys.SelectAllKey)
                                }
                                .appendTextContextMenuComponents {
                                    contextCommands.filter(::enabled).forEach { command ->
                                        item(command, contextLabels.getValue(command)) { close(); run(command) }
                                    }
                                },
                        )
                        HorizontalDivider()
                        StatusBar(active, notepad.zoom, compact, ::run)
                    }
                }
            }
        }
    }

    notepad.documents.find { it.id == notepad.closeConfirmation }?.let { doc ->
        AlertDialog(
            onDismissRequest = notepad::cancelClose,
            title = { Text(stringResource(R.string.save_changes, documentTitle(doc))) },
            text = { Text(stringResource(R.string.save_changes_body)) },
            confirmButton = { TextButton(onClick = notepad::saveBeforeClose) { Text(stringResource(R.string.save_document)) } },
            dismissButton = {
                Row {
                    TextButton(onClick = notepad::discardClose) { Text(stringResource(R.string.discard)) }
                    TextButton(onClick = notepad::cancelClose) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }
    notepad.conflict?.let { conflict ->
        val doc = notepad.documents.find { it.id == conflict.documentId }
        if (doc != null) AlertDialog(
            onDismissRequest = notepad::cancelConflict,
            title = { Text(stringResource(R.string.file_changed)) },
            text = { Text(stringResource(R.string.file_changed_body, documentTitle(doc))) },
            confirmButton = { TextButton(onClick = notepad::overwriteConflict) { Text(stringResource(R.string.overwrite)) } },
            dismissButton = {
                Row {
                    TextButton(onClick = notepad::saveConflictAs) { Text(stringResource(R.string.save_as)) }
                    TextButton(onClick = notepad::cancelConflict) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }
}

@Composable
internal fun DocumentHeader(
    documents: List<DocumentSession>, activeId: String, pickerPending: Boolean,
    select: (String, Boolean) -> Unit, close: (String) -> Unit, new: () -> Unit,
    caption: DesktopCaption, region: Rect, list: LazyListState = rememberLazyListState(),
    errorDocumentId: String? = null,
) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val contentHeight = with(density) { NotepadTabTypography.title.lineHeight.toDp() }
        .coerceAtLeast(TabBarDimensions.buttonSize)
    val rowHeight = contentHeight + TabBarDimensions.topPadding
    val layout = captionLayout(caption, region, density.density, with(density) { rowHeight.toPx() })
    val controls = if (layout.integrated) layout.controls(density.density, direction == LayoutDirection.Rtl) else null
    val input = rememberCaptionInput(controls)
    val captionHeight = with(density) { if (layout.integrated) (caption.height - region.top).toDp() else 0.dp }
    val tabHeight = (captionHeight - TabBarDimensions.topPadding).coerceAtLeast(contentHeight)
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
                        if (compact) {
                            DocumentSwitcher(documents, activeId, pickerPending, select, close, new, tabHeight, errorDocumentId)
                        } else {
                            DocumentTabs(documents, activeId, pickerPending, select, close, new, list,
                                if (layout.integrated) input else null, tabHeight, errorDocumentId)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun documentState(doc: DocumentSession, hasError: Boolean): String = stringResource(when {
    hasError -> R.string.document_error
    doc.saving -> R.string.saving
    doc.dirty -> R.string.unsaved
    doc.uri == null -> R.string.not_saved
    else -> R.string.saved
})

@Composable
private fun DocumentTabStatus(doc: DocumentSession, hasError: Boolean) {
    Box(Modifier.width(12.dp), contentAlignment = Alignment.Center) {
        when {
            hasError -> Icon(painterResource(R.drawable.ic_tab_error), null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.error)
            doc.saving -> CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
            doc.dirty -> Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
        }
    }
    Spacer(Modifier.width(4.dp))
}

@Composable
private fun DocumentSwitcher(
    documents: List<DocumentSession>, activeId: String, pickerPending: Boolean,
    select: (String, Boolean) -> Unit, close: (String) -> Unit, new: () -> Unit,
    minTabHeight: Dp, errorDocumentId: String?,
) {
    var expanded by remember { mutableStateOf(false) }
    val active = documents.find { it.id == activeId }
    val title = active?.let { documentTitle(it) }.orEmpty()
    val accessibleTitle = if (active?.dirty == true) stringResource(R.string.unsaved_tab, title) else title
    val switcherLabel = stringResource(R.string.switch_document, accessibleTitle)
    val state = active?.let { documentState(it, it.id == errorDocumentId) }.orEmpty()
    Row(Modifier.fillMaxWidth().padding(top = TabBarDimensions.topPadding), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            DocumentTooltip(title) {
                TextButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = minTabHeight)
                        .testTag("document-switcher")
                        .semantics { contentDescription = switcherLabel; stateDescription = state },
                    shape = DocumentTabShape,
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    contentPadding = PaddingValues(start = TabBarDimensions.contentStartPadding, end = TabBarDimensions.contentEndPadding),
                ) {
                    active?.let { DocumentTabStatus(it, it.id == errorDocumentId) }
                    Text(title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = NotepadTabTypography.title)
                    Icon(painterResource(R.drawable.ic_tab_expand), null, Modifier.size(16.dp))
                }
            }
            NotepadDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                documents.forEach { doc ->
                    val docTitle = documentTitle(doc)
                    val label = if (doc.dirty) stringResource(R.string.unsaved_tab, docTitle) else docTitle
                    val docState = documentState(doc, doc.id == errorDocumentId)
                    Row(Modifier.widthIn(min = 240.dp), verticalAlignment = Alignment.CenterVertically) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(docTitle, style = NotepadTabTypography.title)
                                    Text(docState, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            modifier = Modifier.weight(1f).semantics {
                                selected = doc.id == activeId
                                contentDescription = label
                                stateDescription = docState
                            },
                            trailingIcon = if (doc.id == activeId) ({ Text("✓") }) else null,
                            onClick = { expanded = false; select(doc.id, true) },
                        )
                        TooltipAction(stringResource(R.string.close_tab, docTitle), R.drawable.ic_tab_close,
                            enabled = !doc.saving && !pickerPending) {
                            expanded = false
                            close(doc.id)
                        }
                    }
                }
            }
        }
        TooltipAction(stringResource(R.string.new_tab), R.drawable.ic_tab_new, iconSize = 20.dp, onClick = new)
    }
}

@Composable
private fun DocumentTabs(
    documents: List<DocumentSession>, activeId: String, pickerPending: Boolean,
    select: (String, Boolean) -> Unit, close: (String) -> Unit, new: () -> Unit,
    list: LazyListState, input: CaptionInput?, minTabHeight: Dp, errorDocumentId: String?,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()
    var contextId by remember { mutableStateOf<String?>(null) }
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val separatorColor = MaterialTheme.colorScheme.outlineVariant
    BoxWithConstraints(
        Modifier.fillMaxWidth().heightIn(min = TabBarDimensions.minBarHeight).padding(top = TabBarDimensions.topPadding),
    ) {
        val availableWidth = (maxWidth - TabBarDimensions.buttonSize).coerceAtLeast(0.dp)
        val tabWidth = (availableWidth / documents.size.coerceAtLeast(1))
            .coerceIn(TabBarDimensions.minTabWidth, TabBarDimensions.maxTabWidth)
        val viewportWidth = (tabWidth * documents.size).coerceAtMost(availableWidth)
        val density = LocalDensity.current
        LaunchedEffect(activeId, documents.size, tabWidth, viewportWidth, density) {
            val index = documents.indexOfFirst { it.id == activeId }
            val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
            val fits = with(density) {
                item != null && item.offset >= 0 && item.offset + tabWidth.roundToPx() <= viewportWidth.roundToPx()
            }
            if (index >= 0 && !fits) list.scrollToItem(index)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LazyRow(
                state = list,
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
                items(documents, key = { it.id }) { doc ->
                    val title = documentTitle(doc)
                    val selected = doc.id == activeId
                    val shape = if (selected) DocumentTabShape else RoundedCornerShape(DocumentTabShapeDimensions.topCornerRadius)
                    val interaction = remember { MutableInteractionSource() }
                    val accessibleTitle = if (doc.dirty) stringResource(R.string.unsaved_tab, title) else title
                    val state = documentState(doc, doc.id == errorDocumentId)
                    val tabFocus = remember { FocusRequester() }
                    DisposableEffect(doc.id) {
                        focusRequesters[doc.id] = tabFocus
                        onDispose { focusRequesters.remove(doc.id) }
                    }
                    var focused by remember { mutableStateOf(false) }
                    val index = documents.indexOf(doc)
                    val separator = !selected && documents.getOrNull(index + 1)?.id != activeId
                    Box(Modifier.zIndex(if (selected) 1f else 0f).drawBehind {
                        if (separator) {
                            val width = 1.dp.toPx()
                            val height = 20.dp.toPx()
                            drawRect(separatorColor, Offset(if (rtl) 0f else size.width - width, (size.height - height) / 2), Size(width, height))
                        }
                    }) {
                        DocumentTooltip(title) {
                            Surface(
                                modifier = Modifier.width(tabWidth).heightIn(min = minTabHeight)
                                    .captionControl(input, doc.id).clip(shape)
                                    .focusRequester(tabFocus).onFocusChanged { focused = it.hasFocus }
                                    .then(if (focused) Modifier.border(TabBarDimensions.focusOutlineWidth, MaterialTheme.colorScheme.primary, shape) else Modifier)
                                    .semantics {
                                        this.selected = selected
                                        role = Role.Tab
                                        contentDescription = accessibleTitle
                                        stateDescription = state
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
                                    DocumentTabStatus(doc, doc.id == errorDocumentId)
                                    Text(title, Modifier.weight(1f).testTag("tab-title-${doc.id}"), maxLines = 1,
                                        overflow = TextOverflow.Ellipsis, style = NotepadTabTypography.title)
                                    TooltipAction(stringResource(R.string.close_tab, title), R.drawable.ic_tab_close,
                                        enabled = !doc.saving && !pickerPending) { close(doc.id) }
                                }
                            }
                        }
                        NotepadDropdownMenu(expanded = contextId == doc.id, onDismissRequest = { contextId = null }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.close_document)) },
                                trailingIcon = { Text("Ctrl+W", style = MaterialTheme.typography.labelMedium) },
                                enabled = !doc.saving && !pickerPending,
                                onClick = { contextId = null; close(doc.id) },
                            )
                        }
                    }
                }
            }
            TooltipAction(stringResource(R.string.new_tab), R.drawable.ic_tab_new, iconSize = 20.dp,
                modifier = Modifier.captionControl(input, "new"), onClick = new)
        }
    }
}

@Composable
private fun CommandBar(compact: Boolean, enabled: (NotepadCommand) -> Boolean, run: (NotepadCommand) -> Unit) {
    var menu by remember { mutableStateOf<String?>(null) }
    val files = NotepadCommand.entries.take(5)
    val edits = NotepadCommand.entries.drop(5)
    Surface(color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
        Row(Modifier.fillMaxWidth().testTag("command-bar"), verticalAlignment = Alignment.CenterVertically) {
            val groups = if (compact) listOf("all" to R.string.commands) else listOf("file" to R.string.file, "edit" to R.string.edit)
            groups.forEach { (id, title) ->
                Box {
                    TextButton(onClick = { menu = id }, modifier = Modifier.heightIn(min = NotepadSpacing.touchTarget)) { Text(stringResource(title)) }
                    NotepadDropdownMenu(expanded = menu == id, onDismissRequest = { menu = null }) {
                        val commands = when (id) { "file" -> files; "edit" -> edits; else -> NotepadCommand.entries }
                        commands.forEach { command ->
                            DropdownMenuItem(
                                text = { Text(stringResource(command.label)) },
                                trailingIcon = { Text(command.shortcut, style = MaterialTheme.typography.labelMedium) },
                                enabled = enabled(command),
                                onClick = { menu = null; run(command) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentEditor(doc: DocumentSession, zoom: Int, changeZoom: (Int) -> Unit, focus: FocusRequester, modifier: Modifier) {
    val editorLabel = stringResource(R.string.document_editor)
    val latestZoom by rememberUpdatedState(zoom)
    BasicTextField(
        state = doc.editor,
        scrollState = doc.scroll,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = (16f * zoom / 100f).sp,
            lineHeight = (24f * zoom / 100f).sp,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier.fillMaxWidth().testTag("document-editor")
            .semantics { contentDescription = editorLabel }
            .focusRequester(focus)
            .pointerInput(doc.id) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Scroll && event.keyboardModifiers.isCtrlPressed) {
                            val delta = event.changes.sumOf { it.scrollDelta.y.toDouble() }
                            if (delta != 0.0) changeZoom(latestZoom + if (delta < 0) 10 else -10)
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .pointerInput(doc.id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var pinchZoom = latestZoom.toFloat()
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } >= 2) {
                            pinchZoom = (pinchZoom * event.calculateZoom()).coerceIn(50f, 300f)
                            changeZoom((pinchZoom / 10).roundToInt() * 10)
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .padding(NotepadSpacing.medium),
    )
}

@Composable
private fun StatusBar(doc: DocumentSession, zoom: Int, compact: Boolean, run: (NotepadCommand) -> Unit) {
    val text = doc.editor.text
    val position = doc.editor.selection.end.coerceIn(0, text.length)
    val before = text.subSequence(0, position).toString()
    val line = before.count { it == '\n' } + 1
    val column = before.codePointCount(before.lastIndexOf('\n') + 1, before.length) + 1
    val status = when {
        doc.saving -> R.string.saving
        doc.dirty -> R.string.unsaved
        doc.uri == null -> R.string.not_saved
        else -> R.string.saved
    }
    val cursor = stringResource(R.string.cursor_position, line, column)
    if (compact) {
        Column(Modifier.padding(horizontal = NotepadSpacing.medium, vertical = NotepadSpacing.small)) {
            Text("$cursor · ${stringResource(R.string.zoom_value, zoom)}", style = MaterialTheme.typography.labelMedium)
            Text(stringResource(status), style = MaterialTheme.typography.labelMedium)
        }
    } else {
        val zoomLabel = stringResource(R.string.zoom_description, zoom)
        Row(Modifier.fillMaxWidth().padding(horizontal = NotepadSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
            Text(cursor, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            Text(stringResource(status), Modifier.padding(horizontal = NotepadSpacing.small), style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = { run(NotepadCommand.ZoomReset) }, modifier = Modifier.semantics { contentDescription = zoomLabel }) {
                Text(stringResource(R.string.zoom_value, zoom))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentTooltip(label: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        content = content,
    )
}

@Composable
private fun TooltipAction(
    label: String, @DrawableRes icon: Int, modifier: Modifier = Modifier,
    enabled: Boolean = true, iconSize: Dp = 16.dp, onClick: () -> Unit,
) {
    DocumentTooltip(label) {
        IconButton(onClick = onClick, enabled = enabled,
            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = modifier.size(TabBarDimensions.buttonSize).semantics { contentDescription = label },
        ) {
            Icon(painterResource(icon), null, Modifier.size(iconSize))
        }
    }
}

@PreviewScreenSizes
@Preview(name = "Short desktop", widthDp = 1000, heightDp = 400)
@Preview(name = "Extra large dark", widthDp = 1800, heightDp = 1000, uiMode = 0x20)
@Composable
private fun NotepadPreview() {
    // Preview owns no real repository or recovery store; construct the visual editor directly.
    NotepadTheme {
        val density = LocalDensity.current
        val doc = remember { DocumentSession(DocumentSnapshot(text = "KD Notepad\n\nA little room to think.")) }
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            val compact = maxWidth < 600.dp || maxHeight < 480.dp
            val region = with(density) { Rect(0f, 0f, maxWidth.toPx(), maxHeight.toPx()) }
            Column(Modifier.fillMaxSize()) {
                DocumentHeader(listOf(doc), doc.id, false, { _, _ -> }, {}, {}, DesktopCaption(), region)
                CommandBar(compact, { true }, {})
                DocumentEditor(doc, 100, {}, remember { FocusRequester() }, Modifier.weight(1f))
                HorizontalDivider()
                StatusBar(doc, 100, compact, {})
            }
        }
    }
}

@Preview(name = "Integrated desktop header", widthDp = 1000, heightDp = 160)
@Preview(name = "Compact desktop header", widthDp = 320, heightDp = 160)
@Preview(name = "Compact document selector", widthDp = 160, heightDp = 200)
@Composable
private fun DesktopHeaderPreview() {
    NotepadTheme {
        val density = LocalDensity.current
        val doc = remember { DocumentSession(DocumentSnapshot(text = "A desktop draft")) }
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            val width = with(density) { maxWidth.toPx() }
            val height = with(density) { 56.dp.toPx() }
            val region = Rect(0f, 0f, width, with(density) { maxHeight.toPx() })
            val compact = maxWidth < 600.dp
            val caption = DesktopCaption(height, listOf(
                Rect(0f, 0f, with(density) { 48.dp.toPx() }, height),
                Rect(width - with(density) { 120.dp.toPx() }, 0f, width, height),
            ))
            Column {
                DocumentHeader(listOf(doc), doc.id, false, { _, _ -> }, {}, {}, caption, region)
                CommandBar(compact, { true }, {})
            }
        }
    }
}
