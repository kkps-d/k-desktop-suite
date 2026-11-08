@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kkps.kdesktopsuite.paint

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import com.kkps.kdesktopsuite.ui.AppDropdownMenu
import com.kkps.kdesktopsuite.ui.AppMenuItem
import kotlinx.coroutines.delay

/** Material's mouse and keyboard handling stays intact; add non-consuming pen hover. */
@Composable
internal fun PaintTooltipBox(
    positionProvider: PopupPositionProvider,
    tooltip: @Composable TooltipScope.() -> Unit,
    state: TooltipState,
    content: @Composable () -> Unit,
) {
    var penHover by remember { mutableStateOf(false) }
    LaunchedEffect(penHover) {
        if (penHover) {
            delay(500)
            state.show(androidx.compose.foundation.MutatePriority.UserInput)
        } else state.dismiss()
    }
    TooltipBox(positionProvider,tooltip,state) {
        Box(Modifier.pointerInput(state) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.none { it.type == PointerType.Stylus || it.type == PointerType.Eraser }) continue
                    penHover = event.type in listOf(PointerEventType.Enter,PointerEventType.Move) &&
                        event.changes.none { it.pressed }
                }
            }
        }) { content() }
    }
}

@Composable
internal fun PaintStylusMenu(state: PaintUiState, model: PaintViewModel) {
    val pen = rememberPaintPenStatus()
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(pen.supported) { if (!pen.supported) expanded = false }
    val label = stringResource(if (pen.supported) R.string.ui_stylus_options else R.string.ui_no_stylus)
    val tint = if (pen.supported) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Box {
        PaintTooltipBox(TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text(label) } },state = rememberTooltipState()) {
            Box(Modifier.focusable(!pen.supported).semantics {
                if (!pen.supported) { disabled(); contentDescription = label }
            }) {
                IconButton(onClick = { expanded = true },enabled = pen.supported,
                    modifier = Modifier.size(28.dp).testTag("paint-stylus-menu")) {
                    Icon(PaintIcon.Pencil.vector,label,Modifier.size(18.dp).drawWithContent {
                        drawContent()
                        if (!pen.supported) drawLine(tint,Offset.Zero,Offset(size.width,size.height),2.dp.toPx())
                    },tint = tint)
                }
            }
        }
        AppDropdownMenu(expanded,{ expanded = false },anchorPosition = MenuAnchorPosition.Above) {
            Text(stringResource(R.string.ui_pen_attachment,stringResource(when (pen.attached) {
                true -> R.string.ui_pen_attached
                false -> R.string.ui_pen_detached
                null -> R.string.ui_pen_unknown
            })),Modifier.padding(12.dp),style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text(stringResource(R.string.ui_finger_input),Modifier.padding(12.dp),style = MaterialTheme.typography.labelMedium)
            FingerInput.entries.forEach { mode ->
                AppMenuItem({ Text(stringResource(when (mode) {
                    FingerInput.Automatic -> R.string.ui_finger_automatic
                    FingerInput.Draw -> R.string.ui_finger_draw
                    FingerInput.Navigate -> R.string.ui_finger_navigate
                })) },{
                    model.preferences { it.copy(fingerInput = mode) }; expanded = false
                },modifier = Modifier.testTag("stylus-finger-$mode").semantics { selected = state.preferences.fingerInput == mode },
                    trailingIcon = { if (state.preferences.fingerInput == mode) Icon(PaintIcon.Apply.vector,null) })
            }
            HorizontalDivider()
            AppMenuItem({ Text(stringResource(R.string.ui_stylus_hover_cursor)) },{
                model.preferences { it.copy(stylusHoverCursor = !it.stylusHoverCursor) }
            },checked = state.preferences.stylusHoverCursor,modifier = Modifier.testTag("stylus-hover-cursor"),
                trailingIcon = { if (state.preferences.stylusHoverCursor) Icon(PaintIcon.Apply.vector,null) })
            AppMenuItem({ Text(stringResource(R.string.ui_stylus_button_eraser)) },{
                model.preferences { it.copy(stylusButtonEraser = !it.stylusButtonEraser) }
            },checked = state.preferences.stylusButtonEraser,modifier = Modifier.testTag("stylus-button-eraser"),
                trailingIcon = { if (state.preferences.stylusButtonEraser) Icon(PaintIcon.Apply.vector,null) })
        }
    }
}
