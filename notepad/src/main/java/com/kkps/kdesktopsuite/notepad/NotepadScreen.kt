@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.kkps.kdesktopsuite.notepad

import android.content.ClipData
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.TransferableContent
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val active = notepad.active
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
            Modifier.fillMaxSize().windowInsetsPadding(contentInsets)
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
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.loading), Modifier.padding(NotepadSpacing.medium))
                } else {
                    DocumentTabs(notepad.documents, notepad.activeId, notepad.pickerPending,
                        notepad::select, notepad::requestClose, notepad::newDocument)
                    HorizontalDivider()
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
private fun DocumentTabs(
    documents: List<DocumentSession>, activeId: String, pickerPending: Boolean,
    select: (String, Boolean) -> Unit, close: (String) -> Unit, new: () -> Unit,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val list = rememberLazyListState()
    var contextId by remember { mutableStateOf<String?>(null) }
    var keyboardTargetId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(activeId, documents.size) {
        val index = documents.indexOfFirst { it.id == activeId }
        if (index >= 0) list.scrollToItem(index)
    }
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(state = list, modifier = Modifier.weight(1f).testTag("document-tabs")) {
            items(documents, key = { it.id }) { doc ->
                val title = documentTitle(doc)
                val selected = doc.id == activeId
                val accessibleTitle = if (doc.dirty) stringResource(R.string.unsaved_tab, title) else title
                val tabFocus = remember { FocusRequester() }
                LaunchedEffect(keyboardTargetId) { if (keyboardTargetId == doc.id) tabFocus.requestFocus() }
                var focused by remember { mutableStateOf(false) }
                Box {
                    Surface(
                        onClick = { select(doc.id, true) },
                        modifier = Modifier.widthIn(min = 120.dp, max = 240.dp)
                            .focusRequester(tabFocus)
                            .onFocusChanged { focused = it.hasFocus }
                            .then(if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary) else Modifier)
                            .semantics { this.selected = selected; role = Role.Tab; contentDescription = accessibleTitle }
                            .onPreviewKeyEvent {
                                if (it.type != KeyEventType.KeyDown || it.isCtrlPressed) false
                                else when (it.key) {
                                    Key.DirectionLeft, Key.DirectionRight -> {
                                        val direction = (if (it.key == Key.DirectionLeft) -1 else 1) * (if (rtl) -1 else 1)
                                        val index = documents.indexOf(doc)
                                        val target = documents[Math.floorMod(index + direction, documents.size)]
                                        keyboardTargetId = target.id
                                        select(target.id, false)
                                        true
                                    }
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
                            },
                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(Modifier.padding(start = NotepadSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
                            Text((if (doc.dirty) "● " else "") + title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TooltipAction(stringResource(R.string.close_tab, title), "×", !doc.saving && !pickerPending) { close(doc.id) }
                        }
                    }
                    DropdownMenu(expanded = contextId == doc.id, onDismissRequest = { contextId = null }) {
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
        TooltipAction(stringResource(R.string.new_tab), "+", onClick = new)
    }
}

@Composable
private fun CommandBar(compact: Boolean, enabled: (NotepadCommand) -> Boolean, run: (NotepadCommand) -> Unit) {
    var menu by remember { mutableStateOf<String?>(null) }
    val files = NotepadCommand.entries.take(5)
    val edits = NotepadCommand.entries.drop(5)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val groups = if (compact) listOf("all" to R.string.commands) else listOf("file" to R.string.file, "edit" to R.string.edit)
        groups.forEach { (id, title) ->
            Box {
                TextButton(onClick = { menu = id }, modifier = Modifier.heightIn(min = NotepadSpacing.touchTarget)) { Text(stringResource(title)) }
                DropdownMenu(expanded = menu == id, onDismissRequest = { menu = null }) {
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
private fun TooltipAction(label: String, glyph: String, enabled: Boolean = true, onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(NotepadSpacing.touchTarget).semantics { contentDescription = label }) {
            Text(glyph, style = MaterialTheme.typography.titleLarge)
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
        val doc = remember { DocumentSession(DocumentSnapshot(text = "KD Notepad\n\nA little room to think.")) }
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            val compact = maxWidth < 600.dp || maxHeight < 480.dp
            Column(Modifier.fillMaxSize()) {
                DocumentTabs(listOf(doc), doc.id, false, { _, _ -> }, {}, {})
                HorizontalDivider()
                CommandBar(compact, { true }, {})
                DocumentEditor(doc, 100, {}, remember { FocusRequester() }, Modifier.weight(1f))
                HorizontalDivider()
                StatusBar(doc, 100, compact, {})
            }
        }
    }
}
