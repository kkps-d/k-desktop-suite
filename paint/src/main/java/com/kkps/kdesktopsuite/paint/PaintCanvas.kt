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
    var spaceUsedForPan by remember { mutableStateOf(false) }
    var canvasFocused by remember { mutableStateOf(false) }
    var keyboardCursor by remember { mutableStateOf(Point(0f,0f)) }
    var keyboardDrawing by remember { mutableStateOf(false) }
    val stroke = remember { mutableStateListOf<Point>() }
    var dragStart by remember { mutableStateOf<Point?>(null) }
    var dragEnd by remember { mutableStateOf<Point?>(null) }
    var dragConstrained by remember { mutableStateOf(false) }
    var constructionPoint by remember { mutableStateOf<Point?>(null) }
    var hovering by remember { mutableStateOf<Offset?>(null) }
    var eyedropperPosition by remember { mutableStateOf<Offset?>(null) }
    var eyedropperPreviewSize by remember { mutableStateOf(IntSize.Zero) }
    var keyboardInput by remember { mutableStateOf(false) }
    var erasing by remember { mutableStateOf(false) }
    var strokeSeed by remember { mutableIntStateOf(0) }
    var strokePending by remember { mutableStateOf(false) }
    var brushSession by remember { mutableStateOf<BrushStrokeSession?>(null) }
    var contextMenu by remember { mutableStateOf<Offset?>(null) }
    var canvasResize by remember { mutableStateOf<PixelRect?>(null) }
    var rotatingSelection by remember { mutableStateOf(false) }
    var movingSelection by remember { mutableStateOf(false) }
    var rotationCancelGeneration by remember { mutableIntStateOf(0) }
    var suppressRotationPointers by remember { mutableStateOf(false) }
    val raster = state.raster
    val image = rememberRasterImage(raster,model::message)
    var retainedState by remember { mutableStateOf(state) }
    // Keep the last complete image/preview together while the next committed bitmap is prepared.
    val displayedState = if (image != null && image.raster !== raster) retainedState else state
    SideEffect { if (image?.raster === raster) retainedState = state }
    var brushImage by remember(brushSession) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(brushSession) {
        val session = brushSession ?: return@LaunchedEffect
        // Collect sequentially: newer samples are conflated while the worker finishes a batch.
        snapshotFlow { stroke.toList() }.collect { points ->
            if ((erasing || state.options.tool in listOf(Tool.Pencil,Tool.Brush,Tool.Eraser)) && points.isNotEmpty()) {
                brushImage = withContext(Dispatchers.Default) {
                    session.render(points).bitmap().asImageBitmap()
                }
            } else {
                brushImage = null
            }
        }
    }
    LaunchedEffect(state.raster,state.busy,strokePending,image?.raster) {
        val latest = model.state.value
        // Keep released points until composition has the committed image (or cancellation).
        if (strokePending && !latest.busy && state.raster === latest.raster && image?.raster === raster) {
            stroke.clear(); strokePending = false; brushSession = null; erasing = false
        }
    }
    val selectionPreview = displayedState.preview?.takeIf { it.payload != null }
    val payloadImage = rememberRasterImage(selectionPreview?.payload,model::message)
    val selectionRequest = remember(selectionPreview) { selectionPreview?.selectionImageRequest() }
    val selectionImage = rememberSelectionImage(selectionRequest,movingSelection || rotatingSelection,model::message)
    val selectionMatrix = remember(selectionPreview) {
        selectionPreview?.selectionTransform?.let { transform -> android.graphics.Matrix().apply {
            setValues(floatArrayOf(transform.a.toFloat(),transform.c.toFloat(),transform.tx.toFloat(),
                transform.b.toFloat(),transform.d.toFloat(),transform.ty.toFloat(),0f,0f,1f))
        } }
    }
    val selectionPaint = remember { android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG) }
    val dragPreview = remember(dragStart,dragEnd,dragConstrained,constructionPoint,state.preview,state.options) {
        if (state.preview?.constructing == true && constructionPoint != null) state.preview.withShapePoint(constructionPoint!!)
        else dragStart?.let { EditPreview.between(state.options.tool,it,dragEnd ?: it,state.options.constrain || dragConstrained) }
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
    LaunchedEffect(canvasFocused,keyboardInput,keyboardCursor,state.viewport) {
        if (!canvasFocused || !keyboardInput || state.options.tool == Tool.Pan) return@LaunchedEffect
        val view = state.viewport
        val point = view.screenPoint(keyboardCursor)
        val margin = min(with(density) { 24.dp.toPx() },min(view.width,view.height)/2)
        val delta = Point(point.x.coerceIn(margin,view.width-margin)-point.x,
            point.y.coerceIn(margin,view.height-margin)-point.y)
        if (abs(delta.x) > 0.5f || abs(delta.y) > 0.5f) {
            val visible = view.panned(delta)
            if (visible != view) model.viewport(visible)
        }
    }

    fun commandKey(event: KeyEvent): Boolean {
        if (event.key == Key.Spacebar) {
            if (event.type == KeyEventType.KeyDown) {
                if (!spacePressed) spaceUsedForPan = false
                spacePressed = true
                return true
            }
            val activate = spacePressed && !spaceUsedForPan && !event.isCtrlPressed && !event.isShiftPressed && !event.isAltPressed
            spacePressed = false
            // Release activates once; holding Space is reserved for pointer panning.
            if (event.type == KeyEventType.KeyUp && activate && !state.busy && !state.loading) {
                keyboardInput = true
                if (state.options.tool in listOf(Tool.Pencil,Tool.Brush,Tool.Eraser)) {
                    keyboardDrawing = !keyboardDrawing
                    if (keyboardDrawing) {
                        stroke.clear(); strokePending = false; erasing = false
                        strokeSeed = kotlin.random.Random.nextInt()
                        brushSession = BrushStrokeSession(state.raster,state.options,strokeSeed,state.selection)
                        stroke.add(keyboardCursor)
                    } else {
                        strokePending = true
                        model.stroke(stroke.toList(),seed = strokeSeed,session = brushSession)
                    }
                } else if ((state.options.tool.isShape || state.options.tool == Tool.Select) && state.preview == null) {
                    keyboardDrawing = !keyboardDrawing
                    if (keyboardDrawing) { dragStart = keyboardCursor; dragEnd = keyboardCursor }
                    else {
                        model.createPreview(dragStart ?: keyboardCursor,keyboardCursor)
                        dragStart = null; dragEnd = null
                    }
                } else model.tap(keyboardCursor)
            }
            return true
        }
        if (event.type != KeyEventType.KeyDown) return false
        if (event.key == Key.Escape && keyboardDrawing) {
            keyboardDrawing = false; dragStart = null; dragEnd = null
            stroke.clear(); brushSession = null
            return true
        }
        if (event.key == Key.F10 && event.isShiftPressed) { contextMenu = Offset.Zero; return true }
        if (event.key == Key.Delete) { model.deleteSelection(); return true }
        if (event.key == Key.X && !event.isCtrlPressed) {
            model.options { it.copy(foreground = it.background,background = it.foreground) }; return true
        }
        if (event.key in listOf(Key.LeftBracket,Key.RightBracket)) {
            if (state.options.tool == Tool.Pencil) return false
            val delta = if (event.key == Key.LeftBracket) -1 else 1
            model.options { if (it.tool == Tool.Eraser) it.copy(eraserWidth = (it.eraserWidth+delta).coerceIn(1,100)) else it.copy(width = (it.width+delta).coerceIn(1,100)) }; return true
        }
        val step = if (event.isShiftPressed) 10 else 1
        val dx = when (event.key) { Key.DirectionLeft -> -step; Key.DirectionRight -> step; else -> 0 }
        val dy = when (event.key) { Key.DirectionUp -> -step; Key.DirectionDown -> step; else -> 0 }
        if (dx != 0 || dy != 0) {
            val rect = state.preview?.bounds ?: state.selection
            if (rect != null && !keyboardDrawing && state.preview?.constructing != true) model.movePreview(rect.copy(x = rect.x+dx,y = rect.y+dy))
            else {
                keyboardCursor = Point((keyboardCursor.x+dx).coerceIn(0f,state.raster.width-1f),(keyboardCursor.y+dy).coerceIn(0f,state.raster.height-1f))
                keyboardInput = true
                if (keyboardDrawing) {
                    if (state.options.tool.isShape || state.options.tool == Tool.Select) dragEnd = keyboardCursor else stroke.add(keyboardCursor)
                }
            }
            return true
        }
        return false
    }

    Box(Modifier.fillMaxSize().clipToBounds().background(surface).testTag("paint-viewport")
        .pointerInput(active) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (rotatingSelection && event.changes.count { it.pressed } > 1) {
                        suppressRotationPointers = true
                        rotationCancelGeneration++
                    }
                    if (suppressRotationPointers) {
                        event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) suppressRotationPointers = false
                    }
                }
            }
        }
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
            .onFocusChanged {
                canvasFocused = it.hasFocus
                if (it.hasFocus) keyboardInput = true else {
                    if (!strokePending) stroke.clear()
                    spacePressed = false
                    if (keyboardDrawing) { keyboardDrawing = false; dragStart = null; dragEnd = null }
                }
            }
            .focusable().onKeyEvent(::commandKey)
            .semantics {
                contentDescription = "Image canvas, ${state.raster.width} by ${state.raster.height} pixels. ${state.options.tool.label} tool." +
                    if (state.options.tool != Tool.Pan) " Use arrow keys to position, Space to place points, Enter to apply, Escape to cancel. Hold Space and drag to pan." else ""
                customActions = listOf(
                    CustomAccessibilityAction("Select all image pixels") { model.selectAll(); true },
                    CustomAccessibilityAction("Apply current edit") { model.applyPreview(); true },
                    CustomAccessibilityAction("Cancel current edit") { model.cancelPreview(); true },
                    CustomAccessibilityAction("Finish and commit shape") { model.applyPreview(); true },
                    CustomAccessibilityAction("Move or resize selection") { model.dialog(PaintDialog.Transform); true },
                    CustomAccessibilityAction("Resize canvas") { model.dialog(PaintDialog.CanvasSize); true },
                    CustomAccessibilityAction("Create text in the image") { model.tool(Tool.Text); model.tap(Point(16f,16f)); true },
                    CustomAccessibilityAction("Announce cursor coordinates") { model.message("Cursor ${keyboardCursor.x.toInt()}, ${keyboardCursor.y.toInt()} pixels"); true },
                )
            }
            .pointerHoverIcon(if (state.options.tool == Tool.Pan) handCursor else PointerIcon.Crosshair)
            .pointerInput(density.density,active,state.options.tool) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (rotatingSelection || suppressRotationPointers || down.isConsumed) { down.consume(); return@awaitEachGesture }
                    val initial = model.state.value
                    if (!active || initial.busy || initial.loading) return@awaitEachGesture
                    focus.requestFocus()
                    keyboardInput = false
                    val startEvent = currentEvent
                    if (startEvent.buttons.isSecondaryPressed) {
                        contextMenu = down.position; down.consume(); return@awaitEachGesture
                    }
                    stroke.clear(); strokePending = false; keyboardDrawing = false; strokeSeed = kotlin.random.Random.nextInt()
                    erasing = down.type == PointerType.Eraser
                    brushSession = BrushStrokeSession(initial.raster,initial.options,strokeSeed,initial.selection,erasing)
                    val imageStart = initial.viewport.imagePoint(Point(down.position.x,down.position.y))
                    val tool = if (erasing) Tool.Eraser else initial.options.tool
                    val stylus = down.type == PointerType.Stylus || erasing
                    val existing = initial.preview?.bounds ?: initial.selection
                    val constructing = initial.preview?.constructing == true
                    val hitExisting = !constructing && (initial.preview?.takeIf { it.canTransform }?.containsSelection(imageStart)
                        ?: (existing?.contains(imageStart.x.toInt(),imageStart.y.toInt()) == true)) &&
                        (tool.isShape || tool in listOf(Tool.Select,Tool.Text))
                    val bottomRight = existing?.let { initial.viewport.screenPoint(Point(it.right.toFloat(),it.bottom.toFloat())) }
                    val resizeRadius = (if (down.type == PointerType.Touch) 24 else 14)*density.density
                    val resize = !constructing && bottomRight != null &&
                        abs(bottomRight.x-down.position.x) <= resizeRadius && abs(bottomRight.y-down.position.y) <= resizeRadius
                    var navigation = tool == Tool.Pan || spacePressed || startEvent.buttons.isTertiaryPressed
                    if (spacePressed) spaceUsedForPan = true
                    // Resize from the gesture's original pixels and transform, even after crossing an axis.
                    val resizingSelection = if (!navigation && resize && (initial.preview == null || initial.preview.canTransform))
                        model.floatingSelection() else null
                    // An outside touch can still become two-finger navigation; it must not draw or select.
                    val canEdit = hitExisting || resize ||
                        (imageStart.x in 0f..initial.raster.width.toFloat() && imageStart.y in 0f..initial.raster.height.toFloat())
                    var moved = false
                    if (!navigation && canEdit && tool == Tool.Eyedropper) eyedropperPosition = down.position
                    if (!navigation && canEdit && tool in listOf(Tool.Pencil,Tool.Brush,Tool.Eraser)) stroke.add(imageStart)
                    else if (!navigation && canEdit && constructing) constructionPoint = imageStart
                    else if (!navigation && canEdit && !hitExisting && !resize && (tool.isShape || tool in listOf(Tool.Select,Tool.Text))) {
                        dragStart = imageStart; dragEnd = imageStart; dragConstrained = startEvent.keyboardModifiers.isShiftPressed
                    }
                    down.consume()
                    try {
                        movingSelection = !navigation && (hitExisting || resize) &&
                            (initial.preview?.payload != null || initial.selection != null)
                        var event: PointerEvent
                        do {
                            event = awaitPointerEvent()
                            dragConstrained = event.keyboardModifiers.isShiftPressed
                            val pointer = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
                            val pressed = event.changes.count { it.pressed && (!stylus || it.type == down.type) }
                            val touchNavigation = stylus && event.changes.count { it.pressed && it.type == PointerType.Touch } >= 2
                            if (pressed > 1 || touchNavigation) {
                                if (!navigation && (hitExisting || resize)) model.setPreview(initial.preview)
                                navigation = true; stroke.clear(); dragStart = null; dragEnd = null; constructionPoint = null
                                eyedropperPosition = null
                                movingSelection = false
                                val pan = event.calculatePan(); val zoom = event.calculateZoom()
                                val centroid = event.calculateCentroid(useCurrent = true)
                                val view = model.state.value.viewport
                                model.viewport(view.zoomed(view.scale*zoom,Point(centroid.x,centroid.y)).panned(Point(pan.x,pan.y)))
                            } else if (!navigation && canEdit && tool == Tool.Eyedropper) {
                                // Android sends a consumed release when a touch is cancelled.
                                eyedropperPosition = pointer.position.takeUnless { !pointer.pressed && !pointer.changedToUp() }
                            } else if (pointer.pressed || navigation) {
                                val delta = pointer.position - pointer.previousPosition
                                if ((pointer.position-down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                                val point = model.state.value.viewport.imagePoint(Point(pointer.position.x,pointer.position.y))
                                if (navigation) model.viewport(model.state.value.viewport.panned(Point(delta.x,delta.y)))
                                else if (!canEdit) Unit
                                else if (constructing) constructionPoint = point
                                else if (resize) {
                                    requireNotNull(existing)
                                    val dx = (existing.width+point.x-imageStart.x).roundToInt()
                                    val dy = (existing.height+point.y-imageStart.y).roundToInt()
                                    val w = max(1,if (resizingSelection != null) abs(dx) else dx)
                                    val h = max(1,if (resizingSelection != null) abs(dy) else dy)
                                    if (w <= 8192 && h <= 8192 && w.toLong()*h <= MAX_PIXELS) {
                                        if (resizingSelection != null) {
                                            val area = PixelRect(existing.x+min(0,dx),existing.y+min(0,dy),w,h)
                                            model.setPreview(resizingSelection.resizedSelection(area,dx < 0,dy < 0))
                                        } else model.movePreview(existing.copy(width = w,height = h))
                                    }
                                } else if (hitExisting && moved) {
                                    requireNotNull(existing)
                                    model.movePreview(existing.copy(x = existing.x+(point.x-imageStart.x).roundToInt(),y = existing.y+(point.y-imageStart.y).roundToInt()))
                                } else if (tool in listOf(Tool.Pencil,Tool.Brush,Tool.Eraser)) stroke.add(point)
                                else if (dragStart != null) dragEnd = point
                            }
                            event.changes.forEach { it.consume() }
                        } while (if (navigation) event.changes.any { it.pressed } else event.changes.any { it.id == down.id && it.pressed })
                        if (!navigation && canEdit) {
                            when {
                                tool == Tool.Eyedropper -> eyedropperPosition?.let { position ->
                                    model.tap(model.state.value.viewport.imagePoint(Point(position.x,position.y)))
                                }
                                constructing -> model.tap(constructionPoint ?: imageStart)
                                stroke.isNotEmpty() -> {
                                    strokePending = true
                                    model.stroke(stroke.toList(),erasing,strokeSeed,brushSession)
                                }
                                !hitExisting && !resize && dragStart != null && moved -> model.createPreview(dragStart!!,dragEnd ?: dragStart!!,event.keyboardModifiers.isShiftPressed)
                                !moved -> model.tap(imageStart)
                            }
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    finally {
                        eyedropperPosition = null
                        movingSelection = false
                        if (!strokePending) { stroke.clear(); brushSession = null; erasing = false }
                        dragStart = null; dragEnd = null; constructionPoint = null; dragConstrained = false
                    }
                }
            }) {
            val view = state.viewport
            val origin = view.origin
            val shownRaster = displayedState.raster
            val canvasBounds = canvasResize ?: PixelRect(0,0,displayedState.preview?.outputWidth ?: shownRaster.width,displayedState.preview?.outputHeight ?: shownRaster.height)
            val canvasOrigin = view.screenPoint(Point(canvasBounds.x.toFloat(),canvasBounds.y.toFloat()))
            val canvasSize = Size(canvasBounds.width * view.scale,canvasBounds.height * view.scale)
            drawRect(Color(displayedState.options.background),Offset(canvasOrigin.x,canvasOrigin.y),canvasSize)
            clipRect(canvasOrigin.x,canvasOrigin.y,canvasOrigin.x + canvasSize.width,canvasOrigin.y + canvasSize.height) {
                withTransform({
                    translate(origin.x,origin.y)
                    scale(view.scale,view.scale,pivot = Offset.Zero)
                }) {
                    (brushImage?.takeIf { brushSession?.source === shownRaster } ?: image?.bitmap)?.let { bitmap ->
                        drawImage(bitmap,dstSize = IntSize(shownRaster.width,shownRaster.height),
                            filterQuality = if (view.scale >= 1) FilterQuality.None else FilterQuality.Low)
                    }
                    selectionPreview?.let { preview ->
                        if (selectionImage == null && payloadImage?.raster !== preview.payload) return@let
                        preview.source?.let { source ->
                            drawRect(Color(displayedState.options.background or BLACK),Offset(source.x.toFloat(),source.y.toFloat()),Size(source.width.toFloat(),source.height.toFloat()))
                        }
                        if (selectionImage != null) {
                            drawImage(selectionImage.bitmap,dstOffset = IntOffset(preview.bounds.x,preview.bounds.y),
                                filterQuality = if (view.scale >= 1) FilterQuality.None else FilterQuality.Low)
                        } else payloadImage?.takeIf { it.raster === preview.payload }?.let { payload ->
                            // During interaction, move the cached texture instead of rebuilding its pixels.
                            drawIntoCanvas { canvas ->
                                canvas.save()
                                canvas.nativeCanvas.concat(selectionMatrix)
                                canvas.nativeCanvas.drawBitmap(payload.bitmap.asAndroidBitmap(),0f,0f,selectionPaint)
                                canvas.restore()
                            }
                        }
                    }
                    // Draw geometry directly so dragging never waits for a full-image raster render.
                    drawIntoCanvas { canvas ->
                        canvas.save()
                        displayedState.selection?.let { canvas.clipRect(it.x.toFloat(),it.y.toFloat(),it.right.toFloat(),it.bottom.toFloat()) }
                        displayedState.preview?.takeUnless { it.constructing && dragPreview != null }?.let { canvas.nativeCanvas.drawShapePreview(it,displayedState.options) }
                        dragPreview?.let { canvas.nativeCanvas.drawShapePreview(it,state.options) }
                        canvas.restore()
                    }
                }
            }
            drawRect(Color.Gray,Offset(canvasOrigin.x,canvasOrigin.y),canvasSize,style = Stroke(1f))
            val bounds = dragPreview?.bounds ?: state.preview?.bounds ?: state.selection
            bounds?.let { rect ->
                val p = view.screenPoint(Point(rect.x.toFloat(),rect.y.toFloat()))
                val size = Size(rect.width*view.scale,rect.height*view.scale)
                val selected = state.preview?.takeIf { it.canTransform && dragPreview == null }
                val corners = selected?.selectionCorners
                val border = Path().apply {
                    if (corners != null) corners.forEachIndexed { index, point ->
                        val screen = view.screenPoint(point)
                        if (index == 0) moveTo(screen.x,screen.y) else lineTo(screen.x,screen.y)
                    } else {
                        moveTo(p.x,p.y); lineTo(p.x+size.width,p.y); lineTo(p.x+size.width,p.y+size.height); lineTo(p.x,p.y+size.height)
                    }
                    close()
                }
                drawPath(border,outline,style = Stroke(2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(),4.dp.toPx()))))
                val handle = Offset(p.x+size.width,p.y+size.height)
                drawCircle(Color.White,8.dp.toPx(),handle)
                drawCircle(outline,8.dp.toPx(),handle,style = Stroke(2.dp.toPx()))
            }
            hovering?.takeUnless { keyboardInput }?.let { point ->
                val imagePoint = view.imagePoint(Point(point.x,point.y))
                if (imagePoint.x in 0f..raster.width.toFloat() && imagePoint.y in 0f..raster.height.toFloat() && state.options.tool in listOf(Tool.Pencil,Tool.Brush,Tool.Eraser)) {
                    if (erasing || state.options.tool == Tool.Eraser) {
                        val side = max(4f,state.options.eraserWidth*view.scale)
                        val topLeft = Offset(point.x-side/2,point.y-side/2)
                        drawRect(Color.White,topLeft,Size(side,side),style = Stroke(3f))
                        drawRect(Color.Black,topLeft,Size(side,side),style = Stroke(1f))
                    } else if (state.options.tool == Tool.Pencil) {
                        val crosshair = Path().apply {
                            moveTo(point.x-12f,point.y); lineTo(point.x-5f,point.y)
                            moveTo(point.x+5f,point.y); lineTo(point.x+12f,point.y)
                            moveTo(point.x,point.y-12f); lineTo(point.x,point.y-5f)
                            moveTo(point.x,point.y+5f); lineTo(point.x,point.y+12f)
                        }
                        drawPath(crosshair,Color.White,style = Stroke(3f))
                        drawPath(crosshair,Color.Black,style = Stroke(1f))
                        drawCircle(Color.White,3f,point)
                        drawCircle(Color.Black,1.5f,point)
                    } else {
                        val radius = max(2f,state.options.strokeWidth*view.scale/2)
                        drawCircle(Color.White,radius,point,style = Stroke(3f))
                        drawCircle(Color.Black,radius,point,style = Stroke(1f))
                    }
                }
            }
            if (canvasFocused && keyboardInput && state.options.tool != Tool.Pan) {
                val p = view.screenPoint(keyboardCursor)
                val arm = 10.dp.toPx()
                val cursor = Path().apply {
                    moveTo(p.x-arm,p.y); lineTo(p.x+arm,p.y)
                    moveTo(p.x,p.y-arm); lineTo(p.x,p.y+arm)
                }
                drawPath(cursor,Color.White,style = Stroke(4.dp.toPx()))
                drawPath(cursor,outline,style = Stroke(2.dp.toPx()))
                drawRect(outline,style = Stroke(2.dp.toPx()))
            }
        }
        eyedropperPosition?.takeIf { active && state.options.tool == Tool.Eyedropper }?.let { position ->
            val point = state.viewport.imagePoint(Point(position.x,position.y))
            val x = floor(point.x).toInt()
            val y = floor(point.y).toInt()
            if (x in 0 until raster.width && y in 0 until raster.height) {
                val color = raster.pixel(x,y)
                val hex = "#%06X".format(color and 0xFFFFFF)
                Surface(shape = MaterialTheme.shapes.small,shadowElevation = 4.dp,
                    modifier = Modifier.offset {
                        val gap = with(density) { 24.dp.roundToPx() }
                        val left = (position.x+gap).roundToInt().coerceIn(0,max(0,state.viewport.width.roundToInt()-eyedropperPreviewSize.width))
                        val above = position.y.roundToInt()-gap-eyedropperPreviewSize.height
                        val top = (if (above >= 0) above else position.y.roundToInt()+gap)
                            .coerceIn(0,max(0,state.viewport.height.roundToInt()-eyedropperPreviewSize.height))
                        IntOffset(left,top)
                    }.onSizeChanged { eyedropperPreviewSize = it }.testTag("paint-eyedropper-preview")
                        .semantics(mergeDescendants = true) { contentDescription = "Picked color $hex" }) {
                    Row(Modifier.padding(8.dp),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(40.dp).background(Color(color)).border(1.dp,MaterialTheme.colorScheme.outline))
                        Text(hex,style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        displayedState.preview?.takeIf { it.tool == Tool.Text }?.let { preview -> key(preview.id) {
            val textState = displayedState.copy(viewport = state.viewport,busy = state.busy || image?.raster !== raster)
            TextOverlay(textState,preview,model)
            TextMoveFrame(textState,preview,model,active)
        } }
        if (state.options.tool != Tool.Pan) {
            if (state.selection == null && state.preview?.payload == null)
                CanvasResizeHandles(state,model,canvasResize,active,onBoundsChange = { canvasResize = it })
            if (state.preview?.constructing != true && (state.selection != null || state.preview?.canTransform == true))
                SelectionRotationHandle(state,model,active,rotationCancelGeneration,onDraggingChange = { rotatingSelection = it })
        }
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
                fontFamily = PaintFonts.family(o.font),
                fontWeight = if (o.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (o.italic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = TextDecoration.combine(buildList { if (o.underline) add(TextDecoration.Underline); if (o.strike) add(TextDecoration.LineThrough) }),
                platformStyle = PlatformTextStyle(includeFontPadding = false)), cursorBrush = SolidColor(Color(o.foreground)))
    }
}
