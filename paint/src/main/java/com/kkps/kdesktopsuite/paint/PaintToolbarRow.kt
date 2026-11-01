package com.kkps.kdesktopsuite.paint

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
internal fun PaintToolbarRow(tag: String, color: Color, scroll: ScrollState = rememberScrollState(),
    content: @Composable RowScope.() -> Unit) {
    var viewportWidth by remember { mutableIntStateOf(0) }
    var contentWidth by remember { mutableIntStateOf(0) }
    val overflow = contentWidth > viewportWidth && viewportWidth > 0
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(Modifier.fillMaxWidth().background(color).onSizeChanged { viewportWidth = it.width },
        verticalAlignment = Alignment.CenterVertically) {
        if (overflow) ToolbarScrollButton(tag,false,scroll.canScrollBackward,rtl) {
            scope.launch { scroll.animateScrollTo((scroll.value - scroll.viewportSize * 3 / 4).coerceAtLeast(0)) }
        }
        Row(Modifier.weight(1f).horizontalScroll(scroll).onSizeChanged { contentWidth = it.width }
            .heightIn(min = 48.dp).testTag(tag),verticalAlignment = Alignment.CenterVertically,content = content)
        if (overflow) ToolbarScrollButton(tag,true,scroll.canScrollForward,rtl) {
            scope.launch { scroll.animateScrollTo((scroll.value + scroll.viewportSize * 3 / 4).coerceAtMost(scroll.maxValue)) }
        }
    }
}

@Composable
private fun ToolbarScrollButton(tag: String, forward: Boolean, enabled: Boolean, rtl: Boolean, action: () -> Unit) {
    val label = stringResource(if (forward) R.string.ui_toolbar_scroll_forward else R.string.ui_toolbar_scroll_backward)
    ToolbarTooltip(label) {
        IconButton(onClick = action,enabled = enabled,
            modifier = Modifier.size(48.dp).testTag("$tag-${if (forward) "forward" else "backward"}")) {
            Icon(PaintIcon.Chevron.vector,label,Modifier.rotate(if (forward != rtl) -90f else 90f))
        }
    }
}
