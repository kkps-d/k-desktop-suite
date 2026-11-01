@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kkps.kdesktopsuite.paint

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kkps.kdesktopsuite.ui.AppDropdownMenu
import com.kkps.kdesktopsuite.ui.AppMenuItem

@Composable
internal fun PaintRecentMenu(s: PaintUiState, model: PaintViewModel, onOpen: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(open,s.preferences.recent) { if (open) model.refreshRecentNames() }
    Box {
        AppMenuItem({ Text(stringResource(R.string.ui_recent)) },{ open = true },enabled = !s.busy,
            trailingIcon = { Text(if (LocalLayoutDirection.current == LayoutDirection.Rtl) "‹" else "›") })
        AppDropdownMenu(open,{ open = false },anchorPosition = MenuAnchorPosition.End) {
            Column(Modifier.onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { open = false; true } else false
            }) {
                s.preferences.recent.forEachIndexed { index,uri ->
                    key(uri) {
                        val name = s.preferences.recentNames[uri] ?: stringResource(R.string.ui_recent_image,index + 1)
                        Row(Modifier.width(320.dp),verticalAlignment = Alignment.CenterVertically) {
                            AppMenuItem({ Text(name,maxLines = 2,overflow = TextOverflow.Ellipsis) },
                                { open = false; onOpen(uri) },enabled = !s.busy,
                                modifier = Modifier.weight(1f).testTag("paint-recent-$index"))
                            PaintButton(PaintIcon.Cancel.vector,stringResource(R.string.ui_remove_recent,name),
                                modifier = Modifier.testTag("paint-remove-recent-$index"),enabled = !s.busy) {
                                if (s.preferences.recent.size == 1) open = false
                                model.removeRecent(uri)
                            }
                        }
                    }
                }
                HorizontalDivider()
                AppMenuItem({ Text(stringResource(R.string.ui_clear_recent)) },{ open = false; model.clearRecent() },enabled = !s.busy)
            }
        }
    }
}
