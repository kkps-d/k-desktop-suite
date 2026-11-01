package com.kkps.kdesktopsuite.notepad

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
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
            .focusGroup().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                state = search.query,
                label = { Text(stringResource(R.string.search_query)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                onKeyboardAction = { if (search.query.composition == null) search.navigate() },
                modifier = Modifier.fillMaxWidth().testTag("search-query").focusRequester(queryFocus)
                    .onFocusChanged { focusChanged(it.isFocused) }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key in listOf(Key.Enter, Key.NumPadEnter) &&
                            search.query.composition == null) {
                            search.navigate(event.isShiftPressed)
                            true
                        } else false
                    },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.align(Alignment.CenterVertically).heightIn(min = 48.dp)
                    .toggleable(search.matchCase, role = Role.Checkbox) { search.matchCase = it },
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = search.matchCase, onCheckedChange = null)
                    Text(stringResource(R.string.search_match_case))
                }
                SearchButton(stringResource(R.string.find_previous), "Shift+F3", canNavigate, { search.navigate(true) })
                SearchButton(stringResource(R.string.find_next), "F3", canNavigate, { search.navigate() })
                SearchButton(stringResource(R.string.search_close), "Esc", true, close)
            }
            if (search.expanded) {
                OutlinedTextField(
                    state = search.replacement,
                    label = { Text(stringResource(R.string.search_replacement)) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    onKeyboardAction = { if (search.replacement.composition == null && canReplace) search.replace() },
                    modifier = Modifier.fillMaxWidth().testTag("search-replacement")
                        .onFocusChanged { focusChanged(it.isFocused) }.onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key in listOf(Key.Enter, Key.NumPadEnter) &&
                            search.replacement.composition == null) {
                            if (canReplace) search.replace()
                            true
                        } else false
                    },
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchButton(stringResource(R.string.replace), stringResource(R.string.search_replace_hint), canReplace, search::replace)
                    SearchButton(stringResource(R.string.replace_all), stringResource(R.string.search_replace_all_hint), canReplace, search::replaceAll)
                }
            }
            Text(status, Modifier.testTag("search-status").semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchButton(label: String, hint: String, enabled: Boolean, action: () -> Unit) {
    TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text("$label ($hint)") } }, state = rememberTooltipState()) {
        TextButton(onClick = action, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
    }
}
