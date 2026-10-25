package com.kkps.kdesktopsuite.paint

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.UUID
import kotlin.math.*

internal enum class PaintDialog { Unsaved, SaveFormat, JpegWarning, Conflict, Oversized, Colors, Geometry, CanvasSize, Settings, About, Properties }
internal enum class PickerKind { Open, Insert, Save }
internal data class PickerRequest(val id: String = UUID.randomUUID().toString(), val kind: PickerKind, val mime: String = "image/png", val launched: Boolean = false)
internal data class PlatformRequest(val id: String = UUID.randomUUID().toString(), val action: String, val file: File? = null)
internal data class PaintUiState(
    val raster: Raster = Raster.blank(), val rendered: Raster? = null, val options: ToolOptions = ToolOptions(),
    val selection: PixelRect? = null, val preview: EditPreview? = null, val viewport: Viewport = Viewport(),
    val title: String = "Untitled", val uri: String? = null, val mime: String = "image/png",
    val dirty: Boolean = false, val canUndo: Boolean = false, val canRedo: Boolean = false,
    val busy: Boolean = false, val saving: Boolean = false, val loading: Boolean = true, val dialog: PaintDialog? = null,
    val message: String? = null, val picker: PickerRequest? = null, val platform: PlatformRequest? = null,
    val preferences: PaintPreferences = PaintPreferences(), val focusMode: Boolean = false,
    val ribbonCollapsed: Boolean = false, val showInformation: Boolean = true, val colorBackground: Boolean = false,
)

internal class PaintViewModel(
    private val images: ImageRepository, private val recovery: PaintRecovery,
    private val settings: PaintSettings, private val savedState: SavedStateHandle,
    private val applicationScope: CoroutineScope, private val compute: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PaintUiState())
    val state = mutableState.asStateFlow()
    private var history = RasterHistory(mutableState.value.raster)
    private var fingerprint: String? = null
    private var recoveryWritable = true
    private var checkpointJob: Job? = null
    private var previewJob: Job? = null
    private var editJob: Job? = null
    private var nextAction: (() -> Unit)? = null
    private var saveContinuation: (() -> Unit)? = null
    private data class SaveJob(val raster: Raster, val revision: Long, val mime: String, val uri: String?, val expected: String?, val staged: StagedImage? = null)
    private var pendingSave: SaveJob? = null
    private var oversized: Raster? = null
    private data class PendingCut(val bounds: PixelRect, val revision: Long, val documentId: String)
    private var pendingCut: PendingCut? = null
    private var documentId = UUID.randomUUID().toString()
    private var operationId = 0L
    private var previousDrawingTool = Tool.Brush
    private var layoutInitialized = false
    private var textEditor: Pair<String, TextFieldState>? = null
    fun textEditor(preview: EditPreview): TextFieldState {
        textEditor?.takeIf { it.first == preview.id }?.let { return it.second }
        return TextFieldState(preview.text,TextRange(preview.textSelectionStart.coerceIn(0,preview.text.length),
            preview.textSelectionEnd.coerceIn(0,preview.text.length))).also { textEditor = preview.id to it }
    }
    private fun currentPreview(): EditPreview? {
        val preview = state.value.preview ?: return null
        val editor = textEditor?.takeIf { it.first == preview.id && preview.tool == Tool.Text }?.second ?: return preview
        return preview.copy(text = editor.text.toString())
    }
    fun textLayout(key: TextLayoutKey, raster: Raster, height: Int) {
        val preview = currentPreview()?.takeIf { it.tool == Tool.Text } ?: return
        if (key != textLayoutKey(preview.text,preview.bounds.width,state.value.options)) return
        setPreview(preview.copy(textRaster = raster,textLayoutKey = key,
            bounds = preview.bounds.copy(height = max(preview.bounds.height,height).coerceAtMost(8192))))
    }
    fun textChanged(value: String, start: Int, end: Int, focused: Boolean) {
        val preview = state.value.preview?.takeIf { it.tool == Tool.Text } ?: return
        setPreview(preview.copy(text = value,
            textSelectionStart = if (focused) start else preview.textSelectionStart.coerceAtMost(value.length),
            textSelectionEnd = if (focused) end else preview.textSelectionEnd.coerceAtMost(value.length)))
    }

    init {
        viewModelScope.launch {
            settings.preferences.catch { message("Settings could not be read: ${it.message}") }.collect { preferences ->
                mutableState.update { it.copy(preferences = preferences) }
            }
        }
        viewModelScope.launch {
            try {
                recovery.read()?.let { snapshot ->
                    layoutInitialized = true
                    history = RasterHistory(snapshot.raster).apply { if (snapshot.dirty) savedRevision = null }
                    fingerprint = snapshot.fingerprint
                    mutableState.update { it.copy(raster = snapshot.raster, options = snapshot.options,
                        preview = snapshot.preview, selection = snapshot.selection,
                        viewport = snapshot.viewport.resized(it.viewport.width,it.viewport.height,snapshot.raster.width,snapshot.raster.height,it.viewport.margin),
                        uri = snapshot.uri, title = snapshot.name, mime = snapshot.mime, dirty = snapshot.dirty) }
                    refreshPreview()
                    if (snapshot.dirty) message("Recovered draft. Undo history starts again after relaunch.")
                }
            } catch (error: Exception) { recoveryWritable = false; message(error.message ?: "Recovery could not be read.") }
            mutableState.update { it.copy(loading = false) }
            savedState.get<String>("pickerKind")?.let { kind ->
                mutableState.update { it.copy(picker = PickerRequest(kind = PickerKind.valueOf(kind), launched = true)) }
            }
        }
        viewModelScope.launch { while (isActive) { delay(5000); if (checkpointJob?.isActive == true) flush() } }
    }
    fun message(text: String?) { mutableState.update { it.copy(message = text) } }
    fun dialog(value: PaintDialog?) { mutableState.update { it.copy(dialog = value) } }
    fun colors(background: Boolean = false) { mutableState.update { it.copy(dialog = PaintDialog.Colors,colorBackground = background) } }
    private fun sync() {
        mutableState.update { it.copy(raster = history.raster, rendered = null, dirty = history.dirty,
            viewport = it.viewport.resized(it.viewport.width,it.viewport.height,history.raster.width,history.raster.height),
            canUndo = history.canUndo, canRedo = history.canRedo) }
        checkpoint()
    }
    private fun snapshot(): RecoverySnapshot {
        val s = state.value
        return RecoverySnapshot(history.raster, s.options, currentPreview(), s.selection, s.viewport,
            s.uri, s.title, s.mime, fingerprint, history.dirty)
    }
    private fun checkpoint() {
        checkpointJob?.cancel()
        checkpointJob = viewModelScope.launch { delay(500); flush() }
    }
    fun flush() {
        if (!recoveryWritable || state.value.loading) return
        val snapshot = snapshot().copy(sequence = recovery.nextSequence())
        applicationScope.launch {
            try { recovery.write(snapshot) } catch (error: Exception) { message("Draft recovery failed: ${error.message}") }
        }
    }
    private fun operation(work: suspend (Raster) -> Raster, valid: () -> Boolean = { true }, after: () -> Unit = {}) {
        if (state.value.busy || state.value.loading) return
        val source = history.raster; val revision = history.revision; val id = ++operationId
        mutableState.update { it.copy(busy = true) }
        editJob = viewModelScope.launch {
            try {
                val result = withContext(compute) { history.prepare(work(source)) }
                if (history.revision == revision && history.raster === source && valid()) {
                    result?.let { history.commit(it) }; sync(); after()
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: OutOfMemoryError) { message("Not enough memory. The original image is unchanged.") }
            catch (error: Exception) { message(error.message ?: "The operation could not be completed.") }
            finally { if (id == operationId) mutableState.update { it.copy(busy = false) } }
        }
    }
    fun cancelOperation() {
        editJob?.cancel()
        ++operationId
        mutableState.update { it.copy(busy = false) }
    }
    fun options(change: (ToolOptions) -> ToolOptions) {
        mutableState.update { it.copy(options = change(it.options)) }; refreshPreview(); checkpoint()
    }
    fun tool(tool: Tool) {
        if (state.value.busy) return
        applyPreview {
            if (tool == Tool.Eyedropper && state.value.options.tool != Tool.Eyedropper) previousDrawingTool = state.value.options.tool
            mutableState.update { it.copy(options = it.options.copy(tool = tool)) }; checkpoint()
        }
    }
    fun stroke(points: List<Point>, erasing: Boolean = false) {
        val s = state.value; val o = s.options
        if (s.preview != null) { applyPreview { stroke(points,erasing) }; return }
        operation({ it.stroke(points, if (o.tool == Tool.Eraser || erasing) o.background else o.foreground,
            (if (erasing) o.eraserWidth else o.strokeWidth).toFloat(), o.tool != Tool.Brush || erasing, s.selection) })
    }
    fun tap(point: Point) {
        val s = state.value; val x = floor(point.x).toInt(); val y = floor(point.y).toInt()
        if (x !in 0 until s.raster.width || y !in 0 until s.raster.height) return
        when (s.options.tool) {
            Tool.Fill -> operation({ it.fill(x, y, s.options.foreground, s.selection) })
            Tool.Eyedropper -> { options { it.copy(foreground = s.raster.pixel(x, y)) }; tool(previousDrawingTool) }
            Tool.Zoom -> viewport(s.viewport.zoomed(s.viewport.scale * 2, s.viewport.screenPoint(point)))
            Tool.Text -> if (s.preview == null) setPreview(EditPreview(Tool.Text, PixelRect(x, y, min(240, s.raster.width - x).coerceAtLeast(1), 80)))
            else -> Unit
        }
    }
    fun createPreview(start: Point, end: Point, constrain: Boolean = false) {
        val s = state.value
        if (s.busy) return
        if (s.preview?.hasContent == true) { applyPreview { createPreview(start,end,constrain) }; return }
        val tool = s.options.tool
        var target = end
        if (s.options.constrain || constrain) {
            if (tool == Tool.Line) {
                val angle = (atan2(end.y - start.y, end.x - start.x) / (PI / 4)).roundToInt() * PI / 4
                val length = hypot(end.x - start.x, end.y - start.y)
                target = Point(start.x + (cos(angle) * length).toFloat(), start.y + (sin(angle) * length).toFloat())
            } else {
                val size = max(abs(end.x - start.x), abs(end.y - start.y))
                target = Point(start.x + size * if (end.x < start.x) -1 else 1, start.y + size * if (end.y < start.y) -1 else 1)
            }
        }
        val area = PixelRect.between(start, target)
        if (tool == Tool.Select) {
            val clipped = area.clipped(s.raster.width, s.raster.height)
            if (clipped.width > 0 && clipped.height > 0) {
                mutableState.update { it.copy(selection = clipped, preview = null, rendered = null) }; checkpoint()
            }
        } else setPreview(EditPreview(tool, area, start, target))
    }
    fun setPreview(preview: EditPreview?) {
        mutableState.update { it.copy(preview = preview) }; refreshPreview(); checkpoint()
    }
    private fun refreshPreview() {
        previewJob?.cancel()
        val s = state.value; val preview = s.preview
        // Native text draws its own live overlay; rendering a full raster per keystroke is unnecessary.
        if (preview == null || !preview.hasContent || preview.tool == Tool.Text) { mutableState.update { it.copy(rendered = null) }; return }
        previewJob = viewModelScope.launch {
            try {
                val rendered = withContext(compute) { s.raster.render(preview, s.options, if (preview.tool == Tool.Select) null else s.selection) }
                if (state.value.preview == preview && state.value.options == s.options && state.value.raster === s.raster) {
                    mutableState.update { it.copy(rendered = rendered) }
                }
            } catch (_: OutOfMemoryError) { message("The preview needs more memory. Reduce its dimensions.") }
            catch (error: Exception) { if (error !is CancellationException) message(error.message ?: "Preview failed.") }
        }
    }
    fun applyPreview(after: () -> Unit = {}) {
        val s = state.value; val preview = currentPreview()
        if (preview == null || !preview.hasContent) {
            mutableState.update { it.copy(preview = null, rendered = null) }; after(); return
        }
        if (preview.tool in setOf(Tool.Rectangle, Tool.Ellipse) && !s.options.outline && !s.options.fill) {
            message("Choose an outline or fill before applying this shape."); return
        }
        operation({ it.render(preview, s.options, if (preview.tool == Tool.Select) null else s.selection) },
            valid = { state.value.preview?.id == preview.id && state.value.options == s.options }) {
            textEditor = null
            mutableState.update { it.copy(preview = null, selection = null, rendered = null) }
            checkpoint()
            // The following action may itself start work, so release the operation gate first.
            mutableState.update { it.copy(busy = false) }; after()
        }
    }
    fun cancelPreview() { previewJob?.cancel(); textEditor = null; mutableState.update { it.copy(preview = null, rendered = null) }; checkpoint() }
    fun selectAll() { applyPreview { mutableState.update { it.copy(selection = PixelRect(0,0,it.raster.width,it.raster.height), options = it.options.copy(tool = Tool.Select)) }; checkpoint() } }
    fun clearSelection() { cancelPreview(); mutableState.update { it.copy(selection = null) }; checkpoint() }
    fun movePreview(bounds: PixelRect) {
        val s = state.value
        if (s.busy) return
        checkImageSize(bounds.width, bounds.height)
        val preview = s.preview ?: s.selection?.let { EditPreview(Tool.Select, it, source = it, payload = s.raster.extract(it)) } ?: return
        if (preview.tool == Tool.Line) {
            fun transform(point: Point) = Point(bounds.x + (point.x-preview.bounds.x)*bounds.width/preview.bounds.width,
                bounds.y + (point.y-preview.bounds.y)*bounds.height/preview.bounds.height)
            setPreview(preview.copy(bounds = bounds, start = transform(preview.start), end = transform(preview.end)))
        } else setPreview(preview.copy(bounds = bounds))
    }
    fun deleteSelection() {
        val s = state.value
        if (s.preview?.payload != null) {
            val source = s.preview.source
            if (source == null) cancelPreview() else operation({ it.clear(source,s.options.background) }) { clearSelection() }
            return
        }
        val rect = s.selection ?: return
        operation({ it.clear(rect, s.options.background) }) { clearSelection() }
    }
    fun crop() {
        val s = state.value
        val rect = (s.preview?.takeIf { it.tool == Tool.Select }?.bounds ?: s.selection)
            ?.clipped(s.raster.width,s.raster.height) ?: return
        if (rect.width == 0 || rect.height == 0) { message("The selection is outside the image."); return }
        operation({ source ->
            val composed = s.preview?.let { source.render(it,s.options) } ?: source
            composed.extract(rect)
        }) { clearSelection(); viewport(state.value.viewport.copy(fit = true).resized(s.viewport.width,s.viewport.height,rect.width,rect.height)) }
    }
    fun undo() { if (state.value.busy) return; if (state.value.preview != null) cancelPreview() else { history.undo(); clearSelection(); sync(); updateImageBounds() } }
    fun resizeCanvas(width: Int, height: Int) = resizeCanvas(PixelRect(0,0,width,height))
    fun resizeCanvas(bounds: PixelRect) {
        if (state.value.busy || state.value.loading) return
        try { checkImageSize(bounds.width,bounds.height) }
        catch (invalid: IllegalArgumentException) { message(invalid.message); return }
        val background = state.value.options.background
        applyPreview {
            operation({ it.resizeCanvas(bounds,background) }) { clearSelection() }
        }
    }
    fun redo() { if (!state.value.busy) { cancelPreview(); history.redo(); sync(); updateImageBounds() } }
    private fun updateImageBounds() { val s = state.value; viewport(s.viewport.resized(s.viewport.width,s.viewport.height,s.raster.width,s.raster.height)) }
    fun viewport(value: Viewport) { mutableState.update { it.copy(viewport = value) }; checkpoint() }
    fun resizeViewport(width: Float, height: Float, margin: Float) {
        val s = state.value
        val view = if (!layoutInitialized) {
            layoutInitialized = true
            s.viewport.copy(fit = s.raster.width > width-margin*2 || s.raster.height > height-margin*2)
        } else s.viewport
        viewport(view.resized(width,height,s.raster.width,s.raster.height,margin))
    }
    fun toggleRibbon() { mutableState.update { it.copy(ribbonCollapsed = !it.ribbonCollapsed) } }
    fun toggleInformation() { mutableState.update { it.copy(showInformation = !it.showInformation) } }
    fun toggleFocus() { mutableState.update { it.copy(focusMode = !it.focusMode) } }
    fun back(): Boolean = when {
        state.value.dialog in setOf(PaintDialog.SaveFormat,PaintDialog.JpegWarning,PaintDialog.Conflict) -> { cancelSave(); true }
        state.value.dialog == PaintDialog.Unsaved -> { cancelReplace(); true }
        state.value.dialog != null -> { dialog(null); true }
        state.value.focusMode -> { toggleFocus(); true }
        state.value.preview != null -> { cancelPreview(); true }
        state.value.selection != null -> { clearSelection(); true }
        else -> false
    }
    private fun unsaved() = history.dirty || state.value.preview?.hasContent == true
    private fun replace(action: () -> Unit) {
        if (state.value.busy || state.value.saving) return
        if (unsaved()) { nextAction = action; dialog(PaintDialog.Unsaved) } else action()
    }
    fun newImage() = replace { install(Raster.blank(), "Untitled", null, "image/png", null) }
    fun open() = replace { launchPicker(PickerKind.Open) }
    fun openRecent(uri: String) = replace { load(uri) }
    fun discard() { val action = nextAction; nextAction = null; dialog(null); action?.invoke() }
    fun cancelReplace() { nextAction = null; saveContinuation = null; dialog(null) }
    fun saveThenContinue() { saveContinuation = nextAction; nextAction = null; dialog(null); save() }
    private fun install(raster: Raster, title: String, uri: String?, mime: String, fingerprint: String?) {
        documentId = UUID.randomUUID().toString()
        textEditor = null
        previewJob?.cancel(); history = RasterHistory(raster); this.fingerprint = fingerprint
        mutableState.update { it.copy(raster = raster, rendered = null, selection = null, preview = null,
            title = title, uri = uri, mime = mime, dirty = false, canUndo = false, canRedo = false,
            viewport = it.viewport.copy(fit = raster.width > it.viewport.width-it.viewport.margin*2 || raster.height > it.viewport.height-it.viewport.margin*2,
                scale = 1f,center = Point(raster.width/2f,raster.height/2f)).resized(it.viewport.width,it.viewport.height,raster.width,raster.height)) }
        checkpoint()
    }
    private fun launchPicker(kind: PickerKind, mime: String = "image/png") {
        savedState["pickerKind"] = kind.name
        mutableState.update { it.copy(picker = PickerRequest(kind = kind, mime = mime)) }
    }
    fun pickerLaunched(id: String) { mutableState.update { if (it.picker?.id == id) it.copy(picker = it.picker.copy(launched = true)) else it } }
    fun pickerResult(uri: String?) {
        val request = state.value.picker ?: return
        savedState.remove<String>("pickerKind"); mutableState.update { it.copy(picker = null) }
        if (uri == null) { cancelSave(); return }
        when (request.kind) {
            PickerKind.Open -> load(uri)
            PickerKind.Insert -> importSelection(uri)
            PickerKind.Save -> {
                if (pendingSave == null) { message("Save was interrupted by a process restart. Choose Save as again."); return }
                pendingSave = pendingSave?.copy(uri = uri, expected = null); writeSave()
            }
        }
    }
    private fun load(uri: String) {
        if (state.value.busy) return
        mutableState.update { it.copy(busy = true) }
        editJob = viewModelScope.launch {
            try { val result = images.read(uri); install(result.raster,result.name,uri,result.mime,result.fingerprint); recent(uri) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: OutOfMemoryError) { message("This image needs more memory than is available.") }
            catch (error: Exception) { message(error.message ?: "Could not open image.") }
            finally { mutableState.update { it.copy(busy = false) } }
        }
    }
    fun save(asNew: Boolean = false) {
        if (state.value.busy || state.value.saving) return
        applyPreview {
            if (asNew || state.value.uri == null) dialog(PaintDialog.SaveFormat)
            else prepareSave(state.value.mime, false)
        }
    }
    fun saveFormat(mime: String) { dialog(null); prepareSave(mime, true) }
    private fun prepareSave(mime: String, newFile: Boolean) {
        val s = state.value
        pendingSave = SaveJob(history.raster, history.revision, mime, if (newFile) null else s.uri,
            if (newFile) null else fingerprint)
        mutableState.update { it.copy(saving = true) }
        if (mime == "image/jpeg") dialog(PaintDialog.JpegWarning) else continueSave()
    }
    fun cancelSave() { pendingSave = null; saveContinuation = null; mutableState.update { it.copy(saving = false) }; dialog(null) }
    fun continueSave() {
        dialog(null)
        if (pendingSave?.uri == null) launchPicker(PickerKind.Save, pendingSave?.mime ?: "image/png") else writeSave()
    }
    fun overwrite() { pendingSave = pendingSave?.copy(expected = null); dialog(null); writeSave() }
    fun conflictSaveAs() { pendingSave = pendingSave?.copy(uri = null, expected = null); dialog(null); continueSave() }
    private fun writeSave() {
        val job = pendingSave ?: return; val uri = job.uri ?: return
        val savedDocument = documentId
        viewModelScope.launch {
            try {
                val staged = job.staged ?: images.stage(job.raster,job.mime,state.value.preferences.jpegQuality)
                pendingSave = job.copy(staged = staged)
                images.write(uri,staged,job.expected)
                if (documentId != savedDocument) return@launch
                fingerprint = staged.fingerprint; history.savedRevision = job.revision
                mutableState.update { it.copy(uri = uri, title = it.title, mime = job.mime) }
                images.name(uri)?.let { name -> mutableState.update { it.copy(title = name) } }
                sync(); recent(uri); pendingSave = null
                mutableState.update { it.copy(saving = false) }
                val continuation = saveContinuation; saveContinuation = null
                if (continuation != null && !unsaved()) continuation() else if (continuation != null) message("Saved an earlier revision. Newer edits remain open.")
                else message("Image saved.")
            } catch (_: ImageChangedException) { dialog(PaintDialog.Conflict) }
            catch (error: Exception) { pendingSave = null; saveContinuation = null; mutableState.update { it.copy(saving = false) }; message("Save failed. The private draft is safe. ${error.message}") }
        }
    }
    private fun recent(uri: String) = preferences { it.copy(recent = (listOf(uri) + it.recent.filter { old -> old != uri }).take(10)) }
    fun preferences(change: (PaintPreferences) -> PaintPreferences) {
        val value = change(state.value.preferences); mutableState.update { it.copy(preferences = value) }
        viewModelScope.launch { try { settings.save(value) } catch (error: Exception) { message("Settings could not be saved: ${error.message}") } }
    }
    fun insertPicker() { applyPreview { launchPicker(PickerKind.Insert) } }
    private fun importSelection(uri: String) {
        val document = documentId
        val revision = history.revision
        viewModelScope.launch {
            try {
                val raster = images.clipboardImage(uri)
                if (documentId == document && history.revision == revision) insert(raster)
                else message("The document changed during image import. Insert the image again when ready.")
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: OutOfMemoryError) { message("This image needs more memory than is available.") }
            catch (error: Exception) { message(error.message ?: "The image could not be inserted.") }
        }
    }
    fun insert(raster: Raster) {
        applyPreview {
            val s = state.value
            if (raster.width > s.raster.width || raster.height > s.raster.height) { oversized = raster; dialog(PaintDialog.Oversized) }
            else insertNow(raster, false)
        }
    }
    fun oversized(expand: Boolean) { val raster = oversized ?: return; oversized = null; dialog(null); insertNow(raster, expand) }
    private fun insertNow(raster: Raster, expand: Boolean) {
        val s = state.value
        val imagePoint = s.viewport.imagePoint(Point(0f,0f))
        val x = imagePoint.x.toInt().coerceIn(0,max(0,s.raster.width-raster.width))
        val y = imagePoint.y.toInt().coerceIn(0,max(0,s.raster.height-raster.height))
        val outputWidth = if (expand) max(s.raster.width,x+raster.width) else s.raster.width
        val outputHeight = if (expand) max(s.raster.height,y+raster.height) else s.raster.height
        try { checkImageSize(outputWidth,outputHeight) } catch (error: Exception) { message(error.message); return }
        options { it.copy(tool = Tool.Select) }
        setPreview(EditPreview(Tool.Select,PixelRect(x,y,raster.width,raster.height),payload = raster,
            outputWidth = outputWidth, outputHeight = outputHeight))
    }
    fun copy(cut: Boolean = false) {
        val s = state.value; val rect = s.preview?.bounds ?: s.selection ?: return
        val content = s.preview?.payload ?: s.raster.extract(rect)
        val source = s.preview?.let { if (it.payload != null) it.source else s.selection } ?: s.selection
        pendingCut = if (cut && source != null) PendingCut(source,history.revision,documentId) else null
        viewModelScope.launch {
            try { val staged = images.stage(content,"image/png",100)
                mutableState.update { it.copy(platform = PlatformRequest(action = if (cut) "cut" else "copy", file = staged.file)) }
            } catch (error: Exception) { message(error.message) }
        }
    }
    fun share() { applyPreview { viewModelScope.launch {
        try { val file = images.stage(history.raster,"image/png",100).file; mutableState.update { it.copy(platform = PlatformRequest(action = "share", file = file)) } }
        catch (error: Exception) { message(error.message) }
    } } }
    fun paste() { mutableState.update { it.copy(platform = PlatformRequest(action = "paste")) } }
    fun platformDone(id: String, success: Boolean, uri: String? = null) {
        val request = state.value.platform?.takeIf { it.id == id } ?: return
        mutableState.update { it.copy(platform = null) }
        if (!success) { pendingCut = null; return }
        if (request.action == "cut") {
            val cut = pendingCut; pendingCut = null
            if (cut != null && cut.revision == history.revision && cut.documentId == documentId)
                operation({ it.clear(cut.bounds,state.value.options.background) }) { clearSelection() }
            else if (cut == null && state.value.preview?.payload != null) cancelPreview()
        }
        if (request.action == "paste" && uri != null) importSelection(uri)
    }
}
