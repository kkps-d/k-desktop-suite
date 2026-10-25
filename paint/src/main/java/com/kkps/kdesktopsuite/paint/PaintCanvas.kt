@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.kkps.kdesktopsuite.paint

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.graphics.createBitmap
import kotlin.math.*
import com.kkps.kdesktopsuite.ui.AppDropdownMenu
import com.kkps.kdesktopsuite.ui.AppMenuItem

@Composable
internal fun PaintCanvas(
    state: PaintUiState, model: PaintViewModel,
    onCursorChange: (Point?) -> Unit, onSelectionChange: (PixelRect?) -> Unit,
    onCanvasResizeChange: (PixelRect?) -> Unit,
) {
    val current by rememberUpdatedState(state)
    val density = LocalDensity.current
    val context = LocalContext.current
    val handCursor = remember(context) { PointerIcon(android.view.PointerIcon.getSystemIcon(context,android.view.PointerIcon.TYPE_GRAB)) }
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    val outline = MaterialTheme.colorScheme.primary
    val focus = remember { FocusRequester() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsState()
    val active = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    var spacePressed by remember { mutableStateOf(false) }
    var keyboardCursor by remember { mutableStateOf(Point(0f,0f)) }
    var keyboardDrawing by remember { mutableStateOf(false) }
    val stroke = remember { mutableStateListOf<Point>() }
    var dragStart by remember { mutableStateOf<Point?>(null) }
    var dragEnd by remember { mutableStateOf<Point?>(null) }
    var dragConstrained by remember { mutableStateOf(false) }
    var hovering by remember { mutableStateOf<Offset?>(null) }
    var keyboardInput by remember { mutableStateOf(false) }
    var erasing by remember { mutableStateOf(false) }
    var strokeSeed by remember { mutableIntStateOf(0) }
    var contextMenu by remember { mutableStateOf<Offset?>(null) }
    var canvasResize by remember { mutableStateOf<PixelRect?>(null) }
    val raster = state.raster
    val image = remember(raster) { raster.bitmap().asImageBitmap() }
    val strokePoints = stroke.toList()
    val brushImage by produceState<Pair<Raster,ImageBitmap>?>(null,raster,strokePoints,state.options,strokeSeed,erasing,state.busy) {
        if (state.options.tool == Tool.Brush && !erasing && strokePoints.isNotEmpty()) {
            val rendered = withContext(Dispatchers.Default) {
                raster.brushStroke(strokePoints,state.options.foreground,state.options.width.toFloat(),
                    state.options.brushStyle,strokeSeed,state.selection).bitmap().asImageBitmap()
            }
            value = raster to rendered
        } else if (!state.busy) value = null
    }
    val payload = state.preview?.payload
    val selectionImage = remember(payload) { payload?.bitmap()?.asImageBitmap() }
    val dragPreview = remember(dragStart,dragEnd,dragConstrained,state.options) {
        dragStart?.let { EditPreview.between(state.options.tool,it,dragEnd ?: it,state.options.constrain || dragConstrained) }
    }

    LaunchedEffect(hovering,keyboardCursor,keyboardInput,state.viewport,active) {
        val point = if (keyboardInput) keyboardCursor else hovering?.let { state.viewport.imagePoint(Point(it.x,it.y)) }
        onCursorChange(point?.takeIf { active && it.x >= 0 && it.x < raster.width && it.y >= 0 && it.y < raster.height }
            ?.let { Point(floor(it.x),floor(it.y)) })
    }
    LaunchedEffect(dragPreview) {
        onSelectionChange(dragPreview?.takeIf { it.tool == Tool.Select }?.bounds?.clipped(state.raster.width,state.raster.height))
    }
    LaunchedEffect(canvasResize) { onCanvasResizeChange(canvasResize) }

    fun commandKey(event: KeyEvent): Boolean {
        if (event.key == Key.Spacebar) {
            spacePressed = event.type == KeyEventType.KeyDown
            if (event.type == KeyEventType.KeyDown && !event.isCtrlPressed && !event.isShiftPressed) {
                if (state.options.tool in listOf(Tool.Pencil,Tool.Brush,Tool.Eraser)) {
                    keyboardDrawing = !keyboardDrawing
                    if (keyboardDrawing) { strokeSeed = kotlin.random.Random.nextInt(); stroke.add(keyboardCursor) }
                    else { model.stroke(stroke.toList(),seed = strokeSeed); stroke.clear() }
                } else model.tap(keyboardCursor)
            }
            return true
        }
        if (event.type != KeyEventType.KeyDown) return false
        if (event.key == Key.F10 && event.isShiftPressed) { contextMenu = Offset.Zero; return true }
        if (event.key == Key.Delete) { model.deleteSelection(); return true }
        if (event.key == Key.X && !event.isCtrlPressed) {
            model.options { it.copy(foreground = it.background,background = it.foreground) }; return true
        }
        if (event.key in listOf(Key.LeftBracket,Key.RightBracket)) {
            val delta = if (event.key == Key.LeftBracket) -1 else 1
            model.options { if (it.tool == Tool.Eraser) it.copy(eraserWidth = (it.eraserWidth+delta).coerceIn(1,100)) else it.copy(width = (it.width+delta).coerceIn(1,100)) }; return true
        }
        val step = if (event.isShiftPressed) 10 else 1
        val dx = when (event.key) { Key.DirectionLeft -> -step; Key.DirectionRight -> step; else -> 0 }
        val dy = when (event.key) { Key.DirectionUp -> -step; Key.DirectionDown -> step; else -> 0 }
        if (dx != 0 || dy != 0) {
            val rect = state.preview?.bounds ?: state.selection
            if (rect != null) model.movePreview(rect.copy(x = rect.x+dx,y = rect.y+dy))
            else {
                keyboardCursor = Point((keyboardCursor.x+dx).coerceIn(0f,state.raster.width-1f),(keyboardCursor.y+dy).coerceIn(0f,state.raster.height-1f))
                keyboardInput = true
                if (keyboardDrawing) stroke.add(keyboardCursor)
            }
            return true
        }
        return false
    }

    Box(Modifier.fillMaxSize().clipToBounds().background(surface).testTag("paint-viewport")
        .onSizeChanged { size ->
            model.resizeViewport(size.width.toFloat(),size.height.toFloat(),with(density) { 24.dp.toPx() })
        }.pointerInput(density.density,active) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    keyboardInput = false
                    hovering = if (event.type == PointerEventType.Exit) null else event.changes.firstOrNull()?.position
                    if (event.type != PointerEventType.Scroll) continue
                    val change = event.changes.firstOrNull()?.takeUnless { it.isConsumed } ?: continue
                    val s = current
                    if (event.keyboardModifiers.isCtrlPressed) {
                        model.viewport(s.viewport.zoomed(s.viewport.scale * if (change.scrollDelta.y < 0) 1.15f else 1/1.15f,Point(change.position.x,change.position.y)))
                    } else {
                        val delta = change.scrollDelta.y * -48 * density.density
                        model.viewport(s.viewport.panned(if (event.keyboardModifiers.isShiftPressed) Point(delta,0f) else Point(-change.scrollDelta.x*48*density.density,delta)))
                    }
                    change.consume()
                }
            }
        }) {
        Canvas(Modifier.fillMaxSize().testTag("paint-canvas").focusRequester(focus)
            .onFocusChanged { if (!it.hasFocus) { stroke.clear(); keyboardDrawing = false; spacePressed = false } }
            .focusable().onKeyEvent(::commandKey)
            .semantics {
                contentDescription = "Image canvas, ${state.raster.width} by ${state.raster.height} pixels. ${state.options.tool.label} tool."
                customActions = listOf(
                    CustomAccessibilityAction("Select all image pixels") { model.selectAll(); true },
                    CustomAccessibilityAction("Apply current edit") { model.applyPreview(); true },
                    CustomAccessibilityAction("Cancel current edit") { model.cancelPreview(); true },
                    CustomAccessibilityAction("Move or resize selection") { model.dialog(PaintDialog.Geometry); true },
                    CustomAccessibilityAction("Resize canvas") { model.dialog(PaintDialog.CanvasSize); true },
                    CustomAccessibilityAction("Create text in the image") { model.tool(Tool.Text); model.tap(Point(16f,16f)); true },
                    CustomAccessibilityAction("Announce cursor coordinates") { model.message("Cursor ${keyboardCursor.x.toInt()}, ${keyboardCursor.y.toInt()} pixels"); true },
                )
            }
            .pointerHoverIcon(if (state.options.tool == Tool.Pan) handCursor else PointerIcon.Crosshair)
            .pointerInput(density.density,active) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val initial = current
                    if (!active || initial.busy || initial.loading) return@awaitEachGesture
                    focus.requestFocus()
                    val startEvent = currentEvent
                    if (startEvent.buttons.isSecondaryPressed) {
                        contextMenu = down.position; down.consume(); return@awaitEachGesture
                    }
                    stroke.clear(); keyboardDrawing = false; strokeSeed = kotlin.random.Random.nextInt()
                    val imageStart = initial.viewport.imagePoint(Point(down.position.x,down.position.y))
                    erasing = down.type == PointerType.Eraser
                    val tool = if (erasing) Tool.Eraser else initial.options.tool
                    val stylus = down.type == PointerType.Stylus || erasing
                    val existing = initial.preview?.bounds ?: initial.selection
                    val hitExisting = existing?.contains(imageStart.x.toInt(),imageStart.y.toInt()) == true && tool in listOf(Tool.Select,Tool.Text,Tool.Line,Tool.Rectangle,Tool.Ellipse)
                    val bottomRight = existing?.let { initial.viewport.screenPoint(Point(it.right.toFloat(),it.bottom.toFloat())) }
                    val resize = bottomRight != null && hypot(bottomRight.x-down.position.x,bottomRight.y-down.position.y) <= 14*density.density
                    var navigation = tool == Tool.Pan || spacePressed || startEvent.buttons.isTertiaryPressed
                    if (!navigation && !hitExisting && !resize &&
                        (imageStart.x !in 0f..initial.raster.width.toFloat() || imageStart.y !in 0f..initial.raster.height.toFloat())) {
                        down.consume()
                        return@awaitEachGesture
                    }
                    var moved = false
                    if (!navigation && tool in listOf(Tool.Pencil,Tool.Brush,Tool.Eraser)) stroke.add(imageStart)
                    else if (!navigation && !hitExisting && !resize && tool in listOf(Tool.Select,Tool.Line,Tool.Rectangle,Tool.Ellipse,Tool.Text)) {
                        dragStart = imageStart; dragEnd = imageStart; dragConstrained = startEvent.keyboardModifiers.isShiftPressed
                    }
                    down.consume()
                    try {
                        var event: PointerEvent
                        do {
                            event = awaitPointerEvent()
                            dragConstrained = event.keyboardModifiers.isShiftPressed
                            val pointer = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
                            val pressed = event.changes.count { it.pressed && (!stylus || it.type == down.type) }
                            val touchNavigation = stylus && event.changes.count { it.pressed && it.type == PointerType.Touch } >= 2
                            if (pressed > 1 || touchNavigation) {
                                navigation = true; stroke.clear(); dragStart = null; dragEnd = null
                                val pan = event.calculatePan(); val zoom = event.calculateZoom()
                                val centroid = event.calculateCentroid(useCurrent = true)
                                val view = model.state.value.viewport
                                model.viewport(view.zoomed(view.scale*zoom,Point(centroid.x,centroid.y)).panned(Point(pan.x,pan.y)))
                            } else if (pointer.pressed || navigation) {
                                val delta = pointer.position - pointer.previousPosition
                                if ((pointer.position-down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                                val point = model.state.value.viewport.imagePoint(Point(pointer.position.x,pointer.position.y))
                                if (navigation) model.viewport(model.state.value.viewport.panned(Point(delta.x,delta.y)))
                                else if (resize) {
                                    requireNotNull(existing)
                                    val w = max(1,(point.x-existing.x).roundToInt()); val h = max(1,(point.y-existing.y).roundToInt())
                                    if (w <= 8192 && h <= 8192 && w.toLong()*h <= MAX_PIXELS) model.movePreview(existing.copy(width = w,height = h))
                                } else if (hitExisting && moved) {
                                    requireNotNull(existing)
                                    model.movePreview(existing.copy(x = existing.x+(point.x-imageStart.x).roundToInt(),y = existing.y+(point.y-imageStart.y).roundToInt()))
                                } else if (tool in listOf(Tool.Pencil,Tool.Brush,Tool.Eraser)) stroke.add(point)
                                else if (dragStart != null) dragEnd = point
                            }
                            event.changes.forEach { it.consume() }
                        } while (if (navigation) event.changes.any { it.pressed } else event.changes.any { it.id == down.id && it.pressed })
                        if (!navigation) {
                            when {
                                stroke.isNotEmpty() -> model.stroke(stroke.toList(),erasing,strokeSeed)
                                !hitExisting && !resize && dragStart != null && moved -> model.createPreview(dragStart!!,dragEnd ?: dragStart!!,event.keyboardModifiers.isShiftPressed)
                                !moved -> model.tap(imageStart)
                            }
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    finally { stroke.clear(); dragStart = null; dragEnd = null; dragConstrained = false; erasing = false }
                }
            }) {
            val view = state.viewport
            val origin = view.origin
            val canvasBounds = canvasResize ?: PixelRect(0,0,state.preview?.outputWidth ?: raster.width,state.preview?.outputHeight ?: raster.height)
            val canvasOrigin = view.screenPoint(Point(canvasBounds.x.toFloat(),canvasBounds.y.toFloat()))
            val canvasSize = Size(canvasBounds.width * view.scale,canvasBounds.height * view.scale)
            drawRect(Color(state.options.background),Offset(canvasOrigin.x,canvasOrigin.y),canvasSize)
            clipRect(canvasOrigin.x,canvasOrigin.y,canvasOrigin.x + canvasSize.width,canvasOrigin.y + canvasSize.height) {
                withTransform({
                    translate(origin.x,origin.y)
                    scale(view.scale,view.scale,pivot = Offset.Zero)
                }) {
                    drawImage(brushImage?.takeIf { it.first === raster }?.second ?: image,dstSize = IntSize(raster.width,raster.height),
                        filterQuality = if (view.scale >= 1) FilterQuality.None else FilterQuality.Low)
                    state.preview?.takeIf { it.tool == Tool.Select }?.let { preview ->
                        preview.source?.let { source ->
                            drawRect(Color(state.options.background or BLACK),Offset(source.x.toFloat(),source.y.toFloat()),Size(source.width.toFloat(),source.height.toFloat()))
                        }
                        selectionImage?.let { selected ->
                            drawImage(selected,dstOffset = IntOffset(preview.bounds.x,preview.bounds.y),
                                dstSize = IntSize(preview.bounds.width,preview.bounds.height),filterQuality = FilterQuality.Low)
                        }
                    }
                    // Draw geometry directly so dragging never waits for a full-image raster render.
                    drawIntoCanvas { canvas ->
                        canvas.save()
                        state.selection?.let { canvas.clipRect(it.x.toFloat(),it.y.toFloat(),it.right.toFloat(),it.bottom.toFloat()) }
                        state.preview?.let { canvas.nativeCanvas.drawShapePreview(it,state.options) }
                        dragPreview?.let { canvas.nativeCanvas.drawShapePreview(it,state.options) }
                        canvas.restore()
                    }
                }
            }
            drawRect(Color.Gray,Offset(canvasOrigin.x,canvasOrigin.y),canvasSize,style = Stroke(1f))
            if (stroke.isNotEmpty() && (state.options.tool != Tool.Brush || erasing)) {
                val path = Path()
                stroke.forEachIndexed { index, point ->
                    val p = view.screenPoint(point)
                    if (index == 0) path.moveTo(p.x,p.y) else path.lineTo(p.x,p.y)
                }
                val color = Color(if (state.options.tool == Tool.Eraser || erasing) state.options.background else state.options.foreground)
                val strokeWidth = if (erasing) state.options.eraserWidth else state.options.strokeWidth
                val first = view.screenPoint(stroke.first())
                if (stroke.size == 1) drawCircle(color,strokeWidth*view.scale/2,Offset(first.x,first.y))
                else drawPath(path,color,style = Stroke(strokeWidth*view.scale,cap = StrokeCap.Round))
            }
            val bounds = dragPreview?.bounds ?: state.preview?.bounds ?: state.selection
            bounds?.let { rect ->
                val p = view.screenPoint(Point(rect.x.toFloat(),rect.y.toFloat()))
                val size = Size(rect.width*view.scale,rect.height*view.scale)
                drawRect(outline,Offset(p.x,p.y),size,style = Stroke(1.5f,pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f,4f))))
                drawRect(Color.White,Offset(p.x+size.width-5,p.y+size.height-5),Size(10f,10f))
                drawRect(outline,Offset(p.x+size.width-5,p.y+size.height-5),Size(10f,10f),style = Stroke(1f))
            }
            hovering?.let { point ->
                val imagePoint = view.imagePoint(Point(point.x,point.y))
                if (imagePoint.x in 0f..raster.width.toFloat() && imagePoint.y in 0f..raster.height.toFloat() && state.options.tool in listOf(Tool.Pencil,Tool.Brush,Tool.Eraser)) {
                    val radius = max(2f,state.options.strokeWidth*view.scale/2)
                    drawCircle(Color.White,radius,point,style = Stroke(3f))
                    drawCircle(Color.Black,radius,point,style = Stroke(1f))
                }
            }
        }
        state.preview?.takeIf { it.tool == Tool.Text }?.let { preview -> key(preview.id) { TextOverlay(state,preview,model) } }
        if (state.options.tool != Tool.Pan) CanvasResizeHandles(state,model,canvasResize,active,onBoundsChange = { canvasResize = it })
        contextMenu?.let { position ->
            AppDropdownMenu(true,{ contextMenu = null },offset = with(density) { DpOffset(position.x.toDp(),position.y.toDp()) }) {
                AppMenuItem({ Text(stringResource(R.string.ui_paste)) },{ contextMenu = null; model.paste() })
                AppMenuItem({ Text(stringResource(R.string.ui_select_all)) },{ contextMenu = null; model.selectAll() })
                AppMenuItem({ Text(stringResource(R.string.ui_copy_selection)) },{ contextMenu = null; model.copy() },enabled = state.selection != null || state.preview?.payload != null)
                AppMenuItem({ Text(stringResource(R.string.ui_cut_selection)) },{ contextMenu = null; model.copy(true) },enabled = state.selection != null || state.preview?.payload != null)
                AppMenuItem({ Text(stringResource(R.string.ui_delete_selected_pixels)) },{ contextMenu = null; model.deleteSelection() },enabled = state.selection != null)
                AppMenuItem({ Text(stringResource(R.string.ui_pan)) },{ contextMenu = null; model.tool(Tool.Pan) })
            }
        }
    }
}

@Composable
private fun TextOverlay(state: PaintUiState, preview: EditPreview, model: PaintViewModel) {
    val text = remember(preview.id) { model.textEditor(preview) }
    val focus = remember { FocusRequester() }
    val currentPreview by rememberUpdatedState(preview)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(text,lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        snapshotFlow { text.text.toString() to text.selection }.collect { (value,selection) ->
            if (value != currentPreview.text || focused && (selection.start != currentPreview.textSelectionStart || selection.end != currentPreview.textSelectionEnd))
                model.textChanged(value,selection.start,selection.end,focused)
        }
    } }
    LaunchedEffect(Unit) {
        val selection = TextRange(preview.textSelectionStart,preview.textSelectionEnd)
        focus.requestFocus()
        withFrameNanos { }
        text.edit { this.selection = TextRange(selection.start.coerceIn(0,length),selection.end.coerceIn(0,length)) }
    }
    val p = state.viewport.screenPoint(Point(preview.bounds.x.toFloat(),preview.bounds.y.toFloat()))
    val o = state.options
    var lastLayout by remember { mutableStateOf<TextLayoutKey?>(null) }
    // The artwork uses image pixels. Controls elsewhere still follow accessibility font scale.
    CompositionLocalProvider(LocalDensity provides Density(1f,1f)) {
        BasicTextField(state = text,readOnly = state.busy,modifier = Modifier.offset { IntOffset(p.x.roundToInt(),p.y.roundToInt()) }
            .graphicsLayer { scaleX = state.viewport.scale; scaleY = state.viewport.scale; transformOrigin = TransformOrigin(0f,0f) }
            .wrapContentSize(Alignment.TopStart,unbounded = true).width(preview.bounds.width.dp).heightIn(min = preview.bounds.height.dp)
            .drawWithContent {
                val area = state.selection ?: PixelRect(0,0,state.raster.width,state.raster.height)
                val left = max(0f,(area.x-preview.bounds.x).toFloat())
                val top = max(0f,(area.y-preview.bounds.y).toFloat())
                val right = min(size.width,(area.right-preview.bounds.x).toFloat())
                val bottom = min(size.height,(area.bottom-preview.bounds.y).toFloat())
                if (left < right && top < bottom) clipRect(left,top,right,bottom) { this@drawWithContent.drawContent() }
            }
            .background(if (o.opaqueText) Color(o.background) else Color.Transparent).focusRequester(focus)
            .onFocusChanged { focused = it.hasFocus }.testTag("paint-text-overlay")
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed) false
                else when (event.key) {
                    Key.B -> { model.options { it.copy(bold = !it.bold) }; true }
                    Key.I -> { model.options { it.copy(italic = !it.italic) }; true }
                    Key.U -> { model.options { it.copy(underline = !it.underline) }; true }
                    else -> false
                }
            }, onTextLayout = { layoutResult ->
                val layout = layoutResult()
                if (layout != null) {
                    val key = textLayoutKey(layout.layoutInput.text.text,preview.bounds.width,o)
                    if (lastLayout != key) {
                        val w = layout.size.width.coerceIn(1,8192)
                        val h = layout.size.height.coerceIn(1,8192)
                        if (w.toLong()*h <= MAX_PIXELS) {
                            val layer = createBitmap(w,h)
                            try {
                                CanvasDrawScope().draw(Density(1f,1f),layout.layoutInput.layoutDirection,Canvas(layer.asImageBitmap()),Size(w.toFloat(),h.toFloat())) {
                                    drawText(layout,color = Color.Black)
                                }
                                lastLayout = key
                                model.textLayout(key,layer.raster(),layout.size.height)
                            } finally { layer.recycle() }
                        }
                    }
                }
            }, textStyle = TextStyle(color = Color(o.foreground),fontSize = o.fontSize.sp,
                fontFamily = when (o.font) { "Serif" -> FontFamily.Serif; "Monospace" -> FontFamily.Monospace; else -> FontFamily.SansSerif },
                fontWeight = if (o.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (o.italic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = TextDecoration.combine(buildList { if (o.underline) add(TextDecoration.Underline); if (o.strike) add(TextDecoration.LineThrough) }),
                platformStyle = PlatformTextStyle(includeFontPadding = false)), cursorBrush = SolidColor(Color(o.foreground)))
    }
}
