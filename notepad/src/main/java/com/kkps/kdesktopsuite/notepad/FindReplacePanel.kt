package com.kkps.kdesktopsuite.notepad

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

@Composable
internal fun FindReplacePanel(
    search: NotepadSearch, readOnly: Boolean, close: () -> Unit,
    focusChanged: (Boolean) -> Unit, modifier: Modifier = Modifier,
) {
    DisposableEffect(Unit) { onDispose { focusChanged(false) } }
    val queryFocus = remember { FocusRequester() }
    LaunchedEffect(search.focusRequest) {
        queryFocus.requestFocus()
        search.query.edit { selection = TextRange(0, length) }
    }
    val matches = search.results?.matches
    val hasQuery = search.query.text.isNotEmpty()
    val canNavigate = hasQuery && !matches.isNullOrEmpty()
    val canReplace = canNavigate && !readOnly
    val current = search.currentIndex
    val status = when {
        search.replacementCount != null -> stringResource(R.string.search_replaced, search.replacementCount!!)
        !hasQuery -> stringResource(R.string.search_empty)
        matches == null -> stringResource(R.string.search_scanning)
        matches.isEmpty() -> stringResource(R.string.search_no_results)
        current >= 0 -> stringResource(R.string.search_position, current + 1, matches.size)
        else -> stringResource(R.string.search_total, matches.size)
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.testTag("find-replace-panel")) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .focusGroup().padding(horizontal = 8.dp, vertical = 4.dp)) {
            SearchQueryRow(query = {
                val label = stringResource(R.string.search_query)
                Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                    OutlinedTextField(
                        state = search.query,
                        placeholder = { Text(label, style = MaterialTheme.typography.bodyMedium) },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                        onKeyboardAction = { if (search.query.composition == null) search.navigate() },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minWidth = 1.dp, minHeight = 40.dp)
                            .testTag("search-query").semantics { contentDescription = label }.focusRequester(queryFocus)
                            .onFocusChanged { focusChanged(it.isFocused) }
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key in listOf(Key.Enter, Key.NumPadEnter) &&
                                    search.query.composition == null) {
                                    search.navigate(event.isShiftPressed)
                                    true
                                } else false
                            },
                    )
                }
            }, status = {
                Text(status, Modifier.testTag("search-status").semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodySmall)
            }, matchCase = {
                SearchButton(stringResource(R.string.search_match_case), icon = R.drawable.ic_match_case,
                    checked = search.matchCase, action = { search.matchCase = !search.matchCase })
            }, previous = {
                SearchButton(stringResource(R.string.find_previous), "Shift+F3", R.drawable.ic_search_previous,
                    canNavigate, action = { search.navigate(true) })
            }, next = {
                SearchButton(stringResource(R.string.find_next), "F3", R.drawable.ic_tab_expand,
                    canNavigate, action = { search.navigate() })
            }, close = {
                SearchButton(stringResource(R.string.search_close), "Esc", R.drawable.ic_tab_close, action = close)
            })
            if (search.expanded) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val label = stringResource(R.string.search_replacement)
                    OutlinedTextField(
                        state = search.replacement,
                        placeholder = { Text(label, style = MaterialTheme.typography.bodyMedium) },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                        onKeyboardAction = { if (search.replacement.composition == null && canReplace) search.replace() },
                        modifier = Modifier.weight(1f).defaultMinSize(minWidth = 1.dp, minHeight = 40.dp)
                            .testTag("search-replacement").semantics { contentDescription = label }
                            .onFocusChanged { focusChanged(it.isFocused) }.onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key in listOf(Key.Enter, Key.NumPadEnter) &&
                                search.replacement.composition == null) {
                                if (canReplace) search.replace()
                                true
                            } else false
                        },
                    )
                    SearchButton(stringResource(R.string.replace), stringResource(R.string.search_replace_hint),
                        R.drawable.ic_replace, canReplace, action = search::replace)
                    SearchButton(stringResource(R.string.replace_all), stringResource(R.string.search_replace_all_hint),
                        R.drawable.ic_replace_all, canReplace, action = search::replaceAll)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchButton(
    label: String, hint: String? = null, icon: Int, enabled: Boolean = true,
    checked: Boolean? = null, action: () -> Unit,
) {
    TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(if (hint == null) label else "$label ($hint)") } }, state = rememberTooltipState()) {
        val modifier = Modifier.size(48.dp).semantics { contentDescription = label }
        if (checked == null) {
            IconButton(onClick = action, enabled = enabled, modifier = modifier) {
                Icon(painterResource(icon), null, Modifier.size(18.dp))
            }
        } else {
            IconToggleButton(checked = checked, onCheckedChange = { action() }, enabled = enabled, modifier = modifier) {
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    Icon(painterResource(icon), null, Modifier.size(18.dp))
                    if (checked) HorizontalDivider(Modifier.align(Alignment.BottomCenter).width(16.dp),
                        thickness = 2.dp, color = LocalContentColor.current)
                }
            }
        }
    }
}

@Composable
private fun SearchQueryRow(
    query: @Composable () -> Unit, status: @Composable () -> Unit, matchCase: @Composable () -> Unit,
    previous: @Composable () -> Unit, next: @Composable () -> Unit, close: @Composable () -> Unit,
) {
    Layout(content = {
        Box { query() }
        Box { status() }
        Box { matchCase() }
        Box { previous() }
        Box { next() }
        Box { close() }
    }, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val width = constraints.maxWidth
        val buttonSize = 48.dp.roundToPx()
        val gap = 8.dp.roundToPx()
        val statusWidth = measurables[1].maxIntrinsicWidth(Constraints.Infinity)
        val singleLine = width >= (160.dp.toPx() * fontScale).toInt() + statusWidth + gap * 2 + buttonSize * 4
        val fieldWidth = if (singleLine) width - statusWidth - gap * 2 - buttonSize * 4 else width - buttonSize * 2
        val queryPlaceable = measurables[0].measure(Constraints.fixedWidth(fieldWidth.coerceAtLeast(0)))
        val statusPlaceable = measurables[1].measure(Constraints(
            maxWidth = if (singleLine) statusWidth else (width - buttonSize * 2 - gap).coerceAtLeast(0)))
        val buttons = measurables.drop(2).map { it.measure(Constraints.fixed(buttonSize, buttonSize)) }
        val firstHeight = maxOf(queryPlaceable.height, buttonSize, if (singleLine) statusPlaceable.height else 0)
        val secondHeight = if (singleLine) 0 else maxOf(statusPlaceable.height, buttonSize)
        layout(width, firstHeight + secondHeight) {
            queryPlaceable.placeRelative(0, (firstHeight - queryPlaceable.height) / 2)
            if (singleLine) {
                statusPlaceable.placeRelative(fieldWidth + gap, (firstHeight - statusPlaceable.height) / 2)
                buttons.forEachIndexed { index, button ->
                    button.placeRelative(width - buttonSize * (4 - index), (firstHeight - buttonSize) / 2)
                }
            } else {
                buttons[1].placeRelative(width - buttonSize * 2, (firstHeight - buttonSize) / 2)
                buttons[2].placeRelative(width - buttonSize, (firstHeight - buttonSize) / 2)
                statusPlaceable.placeRelative(0, firstHeight + (secondHeight - statusPlaceable.height) / 2)
                buttons[0].placeRelative(width - buttonSize * 2, firstHeight + (secondHeight - buttonSize) / 2)
                buttons[3].placeRelative(width - buttonSize, firstHeight + (secondHeight - buttonSize) / 2)
            }
        }
    }
}
