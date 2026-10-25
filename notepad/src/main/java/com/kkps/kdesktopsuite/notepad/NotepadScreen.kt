@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.kkps.kdesktopsuite.notepad

import android.content.ClipData
import android.content.Intent
import android.os.Build
import android.view.WindowInsetsController
import androidx.annotation.StringRes
import androidx.annotation.DrawableRes
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
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
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

internal object TabBarDimensions {
    val topPadding = 8.dp
    val minTabWidth = 120.dp
    val maxTabWidth = 240.dp
    val contentStartPadding = 22.dp
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
    WordWrap(R.string.word_wrap, ""), StatusBar(R.string.status_bar, ""),
}

@Composable
internal fun documentTitle(doc: DocumentSession): String = doc.name ?: stringResource(R.string.untitled)

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
    val focusManager = LocalFocusManager.current
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    var moveDocumentId by rememberSaveable { mutableStateOf<String?>(null) }
    val view = LocalView.current
    DisposableEffect(notepad.window, focusManager) {
        notepad.window.prepareTransfer = { focusManager.clearFocus(force = true) }
        onDispose { notepad.window.prepareTransfer = {} }
    }
    SideEffect { notepad.window.modalOpen = aboutOpen }
    BackHandler(enabled = notepad.workspace.dragging != null) {
        notepad.workspace.dragging?.let { notepad.workspace.cancelTransfer(it.token) }
        view.cancelDragAndDrop()
    }
    BackHandler(enabled = notepad.settingsOpen) { notepad.showSettings(false) }
    val tabs = rememberLazyListState()
    val headerColor = if (notepad.settingsOpen) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerHigh
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
        NotepadCommand.New -> true
        NotepadCommand.Open -> !notepad.pickerPending
        NotepadCommand.Save, NotepadCommand.SaveAs, NotepadCommand.Close -> active != null && !active.saving && !notepad.pickerPending
        NotepadCommand.Undo -> active?.editor?.undoState?.canUndo == true
        NotepadCommand.Redo -> active?.editor?.undoState?.canRedo == true
        NotepadCommand.Cut, NotepadCommand.Copy -> active?.editor?.selection?.collapsed == false
        NotepadCommand.ZoomIn -> active != null && notepad.zoom < 300
        NotepadCommand.ZoomOut -> active != null && notepad.zoom > 50
        else -> active != null
    }

    fun run(command: NotepadCommand) {
        if (!enabled(command)) return
        when (command) {
            NotepadCommand.New -> { notepad.newDocument(); return }
            NotepadCommand.Open -> { notepad.requestOpen(); return }
            else -> Unit
        }
        val doc = notepad.active ?: return
        when (command) {
            NotepadCommand.New, NotepadCommand.Open -> Unit
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
            NotepadCommand.WordWrap -> notepad.toggleWordWrap()
            NotepadCommand.StatusBar -> notepad.toggleStatusBar()
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

    LaunchedEffect(notepad.focusRequest, notepad.settingsOpen, aboutOpen) {
        if (active != null && !notepad.settingsOpen && !aboutOpen) focus.requestFocus()
    }

    val receiveContent = remember(notepad, activity) {
        ReceiveContentListener { content ->
            val data = content.clipEntry.clipData
            val hasUri = (0 until data.itemCount).any { data.getItemAt(it).uri != null }
            if (data.description.hasMimeType(TabWindows.mimeType)) {
                val token = data.takeIf { it.itemCount == 1 }?.getItemAt(0)?.text?.toString()
                if (token != null && (content.source != TransferableContent.Source.DragAndDrop ||
                    !TabWindows.launchTransfer(activity, notepad, token))) {
                    notepad.workspace.cancelTransfer(token)
                }
                null
            } else if (content.source == TransferableContent.Source.DragAndDrop && hasUri) {
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
                .tabDropTarget(notepad, dropped = { token ->
                    TabWindows.launchTransfer(activity, notepad, token)
                })
                .contentReceiver(receiveContent)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape && notepad.workspace.dragging != null) {
                        notepad.workspace.dragging?.let { notepad.workspace.cancelTransfer(it.token) }
                        view.cancelDragAndDrop()
                        true
                    } else if (notepad.settingsOpen) {
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                            notepad.showSettings(false)
                            true
                        } else false
                    } else if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed || notepad.closeConfirmation != null || notepad.conflict != null) false
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
                } else if (notepad.settingsOpen) {
                    // Keep settings below desktop caption controls and retain the system drag area.
                    Spacer(Modifier.height(with(density) { (caption.height - origin.y - region.top).coerceAtLeast(0f).toDp() }))
                    NotepadSettingsScreen(
                        appTheme = notepad.appTheme, fontStyle = notepad.fontStyle,
                        changeAppTheme = notepad::changeAppTheme, changeFontStyle = notepad::changeFontStyle,
                        onBack = { notepad.showSettings(false) }, modifier = Modifier.weight(1f),
                    )
                } else {
                    Column(Modifier.tabDropTarget(notepad, dropped = { false })) {
                        DocumentHeader(notepad.documents, notepad.activeId, notepad.pickerPending,
                            notepad::select, notepad::requestClose, notepad::newDocument,
                            caption, region.translate(origin), tabs, notepad.errorDocumentId, notepad, activity,
                            moveToWindow = { moveDocumentId = it })
                        CommandBar(compact, ::enabled, ::run, notepad.wordWrap, notepad.showStatusBar,
                            openSettings = {
                                focusManager.clearFocus()
                                notepad.showSettings(true)
                            },
                            openAbout = {
                                aboutOpen = true
                            },
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
                                }, wordWrap = notepad.wordWrap, fontFamily = notepad.fontStyle.fontFamily,
                        )
                        if (notepad.showStatusBar) StatusBar(active, notepad.zoom, compact, ::run)
                    } else {
                        NotepadWelcome(
                            recentFiles = notepad.recentFiles,
                            new = { run(NotepadCommand.New) },
                            open = { run(NotepadCommand.Open) },
                            openRecent = { notepad.openUri(it) },
                            openEnabled = !notepad.pickerPending && notepad.opening == 0,
                            modifier = Modifier.weight(1f),
                            focusRequest = notepad.focusRequest,
                        )
                    }
                }
            }
        }
    }

    if (aboutOpen) NotepadAboutDialog(onDismiss = { aboutOpen = false })

    moveDocumentId?.let { id ->
        AlertDialog(
            onDismissRequest = { moveDocumentId = null },
            title = { Text(stringResource(R.string.move_to_window)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    notepad.workspace.windows.filter { it !== notepad.window && it.attached && !it.closed }.forEachIndexed { index, window ->
                        val doc = window.documents.find { it.id == window.activeId }
                        val title = doc?.let { documentTitle(it) }.orEmpty()
                        TextButton(
                            enabled = notepad.canTransfer(id) && !window.blocked(),
                            onClick = { moveDocumentId = null; notepad.moveToWindow(id, window) },
                        ) { Text(stringResource(R.string.window_title, index + 1, title)) }
                    }
                    TextButton(
                        enabled = activity != null && TabWindows.supported(activity) && notepad.canTransfer(id) && notepad.documents.size > 1,
                        onClick = { moveDocumentId = null; activity?.let { TabWindows.launch(it, notepad, id) } },
                    ) { Text(stringResource(R.string.new_window)) }
                }
            },
            confirmButton = { TextButton(onClick = { moveDocumentId = null }) { Text(stringResource(R.string.cancel)) } },
        )
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
    notepad: NotepadViewModel? = null, activity: NotepadActivity? = null,
    moveToWindow: (String) -> Unit = {},
) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val lineHeight = with(density) { NotepadTabTypography.title.lineHeight.toDp() }
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
                                .captionControl(if (layout.integrated && notepad?.workspace?.dragging != null) input else null, "welcome-drop-area")
                                .testTag("tab-drop-area")
                                .tabDropTarget(notepad, dropped = { token ->
                                    notepad?.let { it.workspace.move(token, it.window, 0) } == true
                                }), verticalAlignment = Alignment.CenterVertically) {
                                WelcomeTab(
                                    onClick = { notepad?.window?.let { it.focusRequest++ } },
                                    modifier = Modifier.weight(1f, fill = false).width(TabBarDimensions.maxTabWidth)
                                        .heightIn(min = tabHeight).captionControl(if (layout.integrated) input else null, "welcome"),
                                )
                                TooltipAction(stringResource(R.string.new_tab), R.drawable.ic_tab_new, iconSize = 20.dp,
                                    modifier = Modifier.captionControl(if (layout.integrated) input else null, "new"),
                                    targetHeight = tabHeight.coerceAtMost(TabBarDimensions.buttonSize), onClick = new)
                            }
                        } else if (compact) {
                            DocumentSwitcher(documents, activeId, pickerPending, select, close, new, tabHeight, errorDocumentId,
                                notepad, activity, moveToWindow)
                        } else {
                            DocumentTabs(documents, activeId, pickerPending, select, close, new, list,
                                if (layout.integrated) input else null, tabHeight, errorDocumentId, topPadding,
                                notepad, activity, moveToWindow)
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
    if (!hasError && !doc.saving && !doc.dirty) return
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
    notepad: NotepadViewModel?, activity: NotepadActivity?, moveToWindow: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val moveLeftLabel = stringResource(R.string.move_tab_left)
    val moveRightLabel = stringResource(R.string.move_tab_right)
    val moveWindowLabel = stringResource(R.string.move_to_window)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var actionId by remember { mutableStateOf<String?>(null) }
    val active = documents.find { it.id == activeId }
    val title = active?.let { documentTitle(it) }.orEmpty()
    val accessibleTitle = if (active?.dirty == true) stringResource(R.string.unsaved_tab, title) else title
    val switcherLabel = stringResource(R.string.switch_document, accessibleTitle)
    val state = active?.let { documentState(it, it.id == errorDocumentId) }.orEmpty()
    Row(Modifier.fillMaxWidth().padding(top = TabBarDimensions.topPadding), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            TextButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = minTabHeight)
                    .testTag("document-switcher")
                    .tabDragSource(notepad, activity, activeId)
                    .tabDropTarget(notepad, dropped = { token ->
                        notepad?.let { it.workspace.move(token, it.window, it.documents.size) } == true
                    })
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
            NotepadDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                documents.forEach { doc ->
                    val docTitle = documentTitle(doc)
                    val label = if (doc.dirty) stringResource(R.string.unsaved_tab, docTitle) else docTitle
                    val docState = documentState(doc, doc.id == errorDocumentId)
                    Row(Modifier.widthIn(min = 240.dp).testTag("document-entry-${doc.id}"), verticalAlignment = Alignment.CenterVertically) {
                        NotepadMenuItem(
                            text = {
                                Column {
                                    Text(docTitle, style = NotepadTabTypography.title)
                                    Text(docState, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("document-tab-${doc.id}").semantics {
                                selected = doc.id == activeId
                                contentDescription = label
                                stateDescription = docState
                                if (notepad != null) customActions = tabMoveActions(notepad, doc.id,
                                    moveLeftLabel, moveRightLabel, moveWindowLabel,
                                    { expanded = false; moveToWindow(it) }, rtl)
                            },
                            trailingIcon = if (doc.id == activeId) ({ Text("✓") }) else null,
                            onClick = { expanded = false; select(doc.id, true) },
                        )
                        if (notepad != null) Box {
                            TextButton(onClick = { actionId = doc.id }) {
                                Text("⋮", Modifier.semantics { contentDescription = moveWindowLabel })
                            }
                            NotepadDropdownMenu(expanded = actionId == doc.id, onDismissRequest = { actionId = null }) {
                                TabMoveMenu(notepad, doc.id, moveToWindow = {
                                    actionId = null; expanded = false; moveToWindow(it)
                                }, dismiss = { actionId = null })
                            }
                        }
                        TooltipAction(stringResource(R.string.close_tab, docTitle), R.drawable.ic_tab_close,
                            enabled = !doc.saving && !pickerPending, targetHeight = DropdownMenuDimensions.itemHeight) {
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
    list: LazyListState, input: CaptionInput?, minTabHeight: Dp, errorDocumentId: String?, topPadding: Dp,
    notepad: NotepadViewModel?, activity: NotepadActivity?, moveToWindow: (String) -> Unit,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()
    var contextId by remember { mutableStateOf<String?>(null) }
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val separatorColor = MaterialTheme.colorScheme.outlineVariant
    val moveLeftLabel = stringResource(R.string.move_tab_left)
    val moveRightLabel = stringResource(R.string.move_tab_right)
    val moveWindowLabel = stringResource(R.string.move_to_window)
    var insertion by remember { mutableStateOf<Int?>(null) }
    var dragPosition by remember { mutableStateOf<Offset?>(null) }
    var stripBounds by remember { mutableStateOf(Rect.Zero) }
    val transfer = notepad?.workspace?.dragging
    val dragged = transfer?.let { notepad.workspace.document(it) }
    val displayed = previewTabOrder(documents, dragged, insertion)
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
            .tabDropTarget(notepad,
                moved = { updateInsertion(it) },
                exited = { insertion = null; dragPosition = null },
                dropped = { token ->
                    notepad?.let { it.workspace.move(token, it.window, insertion ?: it.documents.size) } == true
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
                    val title = documentTitle(doc)
                    val selected = doc.id == activeId
                    val shape = if (selected) DocumentTabShape else DocumentTabHoverShape
                    val interaction = remember { MutableInteractionSource() }
                    val accessibleTitle = if (doc.dirty) stringResource(R.string.unsaved_tab, title) else title
                    val state = documentState(doc, doc.id == errorDocumentId)
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
                                .tabDragSource(notepad, activity, doc.id)
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
                                    if (notepad != null) customActions = tabMoveActions(notepad, doc.id,
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
                                DocumentTabStatus(doc, doc.id == errorDocumentId)
                                Text(title, Modifier.weight(1f).testTag("tab-title-${doc.id}"), maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, style = NotepadTabTypography.title)
                                TooltipAction(stringResource(R.string.close_tab, title), R.drawable.ic_tab_close,
                                    enabled = !doc.saving && !pickerPending, targetHeight = minTabHeight.coerceAtMost(TabBarDimensions.buttonSize)) { close(doc.id) }
                            }
                        }
                        NotepadDropdownMenu(expanded = contextId == doc.id, onDismissRequest = { contextId = null }) {
                            TabMoveMenu(notepad, doc.id, moveToWindow, dismiss = { contextId = null })
                            NotepadMenuItem(
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
                modifier = Modifier.captionControl(input, "new"), targetHeight = minTabHeight.coerceAtMost(TabBarDimensions.buttonSize), onClick = new)
        }
    }
}

@Composable
private fun CommandBar(
    compact: Boolean, enabled: (NotepadCommand) -> Boolean, run: (NotepadCommand) -> Unit,
    wordWrap: Boolean = true, showStatusBar: Boolean = true,
    openSettings: () -> Unit = {},
    openAbout: () -> Unit = {},
) {
    var menu by remember { mutableStateOf<String?>(null) }
    val files = NotepadCommand.entries.take(5)
    val edits = NotepadCommand.entries.filter { it in NotepadCommand.Undo..NotepadCommand.SelectAll }
    val views = NotepadCommand.entries.filter { it in NotepadCommand.ZoomIn..NotepadCommand.StatusBar }
    Surface(color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("command-bar").padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val groups = if (compact) listOf("all" to R.string.commands)
                else listOf("file" to R.string.file, "edit" to R.string.edit, "view" to R.string.view, "help" to R.string.help)
            groups.forEach { (id, title) ->
                Box {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        TextButton(onClick = { menu = id }, modifier = Modifier.heightIn(min = 32.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        ) { Text(stringResource(title)) }
                    }
                    NotepadDropdownMenu(expanded = menu == id, onDismissRequest = { menu = null }) {
                        val commands = when (id) {
                            "file" -> files; "edit" -> edits; "view" -> views; "help" -> emptyList(); else -> NotepadCommand.entries
                        }
                        commands.forEach { command ->
                            val checked = when (command) {
                                NotepadCommand.WordWrap -> wordWrap
                                NotepadCommand.StatusBar -> showStatusBar
                                else -> null
                            }
                            NotepadMenuItem(
                                text = { Text(stringResource(command.label)) },
                                trailingIcon = if (checked != null) ({ Text(if (checked) "✓" else "", Modifier.width(24.dp)) })
                                    else ({ Text(command.shortcut, style = MaterialTheme.typography.labelMedium) }),
                                checked = checked,
                                enabled = enabled(command),
                                onClick = { menu = null; run(command) },
                            )
                        }
                        if (id == "all" || id == "help") {
                            if (id == "all") HorizontalDivider()
                            NotepadMenuItem(
                                text = { Text(stringResource(R.string.about_notepad)) },
                                onClick = { menu = null; openAbout() },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            TooltipAction(stringResource(R.string.settings), R.drawable.ic_settings, iconSize = 20.dp, onClick = openSettings)
        }
    }
}

@Composable
private fun DocumentEditor(
    doc: DocumentSession, zoom: Int, changeZoom: (Int) -> Unit, focus: FocusRequester, modifier: Modifier,
    wordWrap: Boolean = true,
    fontFamily: FontFamily = FontFamily.Monospace,
) {
    val editorLabel = stringResource(R.string.document_editor)
    val latestZoom by rememberUpdatedState(zoom)
    BasicTextField(
        state = doc.editor,
        scrollState = doc.scroll,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = fontFamily,
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
            .then(if (wordWrap) Modifier else Modifier.horizontalScroll(doc.horizontalScroll))
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
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().testTag("status-bar")) {
        if (compact) {
            Column(Modifier.heightIn(min = 40.dp).padding(horizontal = NotepadSpacing.medium, vertical = 4.dp)) {
                Text("$cursor · ${stringResource(R.string.zoom_value, zoom)}", style = MaterialTheme.typography.labelMedium)
                Text(stringResource(status), style = MaterialTheme.typography.labelMedium)
            }
        } else {
            val zoomLabel = stringResource(R.string.zoom_description, zoom)
            Row(Modifier.fillMaxWidth().heightIn(min = 28.dp).padding(horizontal = NotepadSpacing.medium, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(cursor, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text(stringResource(status), Modifier.padding(horizontal = NotepadSpacing.small), style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.zoom_value, zoom),
                    Modifier.semantics { contentDescription = zoomLabel }
                        .clip(MaterialTheme.shapes.small).clickable(role = Role.Button) { run(NotepadCommand.ZoomReset) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TooltipAction(
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
