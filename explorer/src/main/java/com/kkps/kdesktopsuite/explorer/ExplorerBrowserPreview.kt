package com.kkps.kdesktopsuite.explorer

import androidx.annotation.DrawableRes
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kkps.kdesktopsuite.ui.AppDropdownMenu
import com.kkps.kdesktopsuite.ui.AppMenuButton
import com.kkps.kdesktopsuite.ui.AppMenuItem
import com.kkps.kdesktopsuite.ui.DocumentTabInteractions
import com.kkps.kdesktopsuite.ui.suiteTabDropTarget
import kotlinx.coroutines.launch

/** Illustrative fixtures only. No paths, providers, grants, or file operations are involved. */
private data class PreviewFile(val name: String, val modified: String, val type: Int, val size: String, val glyph: String = "")

private val previewFiles = listOf(
    PreviewFile("Annual reports", "23 Oct 2026, 10:24", R.string.kind_folder, "—"),
    PreviewFile("Invoices", "22 Oct 2026, 14:10", R.string.kind_folder, "—"),
    PreviewFile("Reference", "20 Oct 2026, 09:15", R.string.kind_folder, "—"),
    PreviewFile("Budget.xlsx", "24 Oct 2026, 09:42", R.string.kind_spreadsheet, "84 KB", "X"),
    PreviewFile("Meeting notes.txt", "23 Oct 2026, 16:30", R.string.kind_text, "3 KB", "TXT"),
    PreviewFile("Project brief.pdf", "22 Oct 2026, 11:05", R.string.kind_pdf, "248 KB", "PDF"),
    PreviewFile("Q4 summary.pdf", "21 Oct 2026, 15:20", R.string.kind_pdf, "512 KB", "PDF"),
    PreviewFile("Timeline.csv", "20 Oct 2026, 08:45", R.string.kind_csv, "12 KB", "CSV"),
)

@Composable
internal fun ExplorerBrowserPreview(
    hasTabs: Boolean,
    interactions: DocumentTabInteractions?,
    fileMenu: @Composable () -> Unit,
    newTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableStateOf<String?>("Budget.xlsx") }
    var icons by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var infoOpen by rememberSaveable { mutableStateOf(false) }
    var workExpanded by rememberSaveable { mutableStateOf(true) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier.fillMaxSize().testTag("explorer-workspace")) {
        val scale = LocalDensity.current.fontScale
        val short = maxHeight < 400.dp
        val persistent = maxWidth >= 840.dp && maxWidth / scale >= 640.dp && !short
        val showSearch = maxWidth / scale >= 840.dp && !short
        LaunchedEffect(persistent) { if (persistent) drawer.close() }
        val closeLocations = { scope.launch { drawer.close() }; Unit }
        ModalNavigationDrawer(
            drawerState = drawer,
            gesturesEnabled = !persistent && hasTabs,
            drawerContent = {
                ModalDrawerSheet(drawerState = drawer, modifier = Modifier.width(280.dp.coerceAtMost(maxWidth)),
                    windowInsets = WindowInsets(0)) {
                    LocationsPane(workExpanded, { workExpanded = !workExpanded }, onClose = closeLocations)
                }
            },
            modifier = Modifier.onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.key == Key.Escape && drawer.isOpen) {
                    closeLocations(); true
                } else false
            },
        ) {
            Column(Modifier.fillMaxSize()) {
                if (hasTabs) NavigationPreview(persistent, short, showSearch, query, { query = it }, searchOpen,
                    { searchOpen = !searchOpen }, { scope.launch { drawer.open() } }, interactions)
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    if (persistent && hasTabs) {
                        LocationsPane(workExpanded, { workExpanded = !workExpanded }, modifier = Modifier.width(256.dp).fillMaxHeight())
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        PreviewCommands(icons, { icons = it }, fileMenu, interactions,
                            { scope.launch { drawer.open() } }, { searchOpen = !searchOpen },
                            { infoOpen = true }, hasTabs, selected != null, short)
                        if (hasTabs) {
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                if (icons) IconFiles(selected, { selected = if (selected == it) null else it })
                                else DetailFiles(selected, { selected = if (selected == it) null else it })
                            }
                        } else {
                            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Icon(painterResource(R.drawable.ic_explorer), null, Modifier.size(64.dp), tint = Color.Unspecified)
                                Spacer(Modifier.height(16.dp))
                                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.preview_welcome), style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(16.dp))
                                FilledTonalButton(onClick = newTab) { Text(stringResource(R.string.new_preview_tab)) }
                            }
                        }
                    }
                }
                PreviewStatus(if (hasTabs) previewFiles.size else 0, hasTabs && selected != null)
            }
        }
        if (infoOpen) {
            val file = previewFiles.find { it.name == selected }
            AlertDialog(onDismissRequest = { infoOpen = false },
                title = { Text(stringResource(R.string.item_information)) },
                text = { if (file != null) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(file.name, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(file.type))
                    Text(file.modified)
                    Text(file.size)
                    Text(stringResource(R.string.sample_content), style = MaterialTheme.typography.labelMedium)
                } },
                confirmButton = { TextButton(onClick = { infoOpen = false }) { Text(stringResource(R.string.close_panel)) } })
        }
    }
}

@Composable
private fun NavigationPreview(
    persistent: Boolean, short: Boolean, inlineSearch: Boolean,
    query: String, queryChanged: (String) -> Unit, searchOpen: Boolean, toggleSearch: () -> Unit,
    openLocations: () -> Unit, interactions: DocumentTabInteractions?,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        BoxWithConstraints(Modifier.fillMaxWidth().suiteTabDropTarget(interactions, dropped = { false })) {
            val width = maxWidth
            val stacked = width / LocalDensity.current.fontScale < 600.dp && !short || width < 280.dp
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (stacked) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                        PreviewIcon(R.drawable.ex_locations, R.string.locations, onClick = openLocations)
                        NavigationButtons()
                        PreviewIcon(R.drawable.ex_search, R.string.search_folder, onClick = toggleSearch)
                    }
                    Breadcrumbs(Modifier.fillMaxWidth())
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!persistent) PreviewIcon(R.drawable.ex_locations, R.string.locations, onClick = openLocations)
                        PreviewIcon(R.drawable.ex_back, R.string.back)
                        if (!short && width >= 600.dp) {
                            PreviewIcon(R.drawable.ex_forward, R.string.forward, enabled = false)
                            PreviewIcon(R.drawable.ex_up, R.string.up)
                            PreviewIcon(R.drawable.ex_refresh, R.string.refresh)
                        }
                        Breadcrumbs(Modifier.weight(1f))
                        if (inlineSearch) SearchPreview(query, queryChanged, Modifier.width(256.dp))
                        else if (width >= 360.dp) PreviewIcon(R.drawable.ex_search, R.string.search_folder, onClick = toggleSearch)
                    }
                }
                if (searchOpen && !inlineSearch) SearchPreview(query, queryChanged, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun NavigationButtons() {
    PreviewIcon(R.drawable.ex_back, R.string.back)
    PreviewIcon(R.drawable.ex_forward, R.string.forward, enabled = false)
    PreviewIcon(R.drawable.ex_up, R.string.up)
    PreviewIcon(R.drawable.ex_refresh, R.string.refresh)
}

@Composable
private fun Breadcrumbs(modifier: Modifier) {
    Surface(modifier.heightIn(min = 48.dp).testTag("explorer-breadcrumbs"),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = CircleShape, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))) {
        BoxWithConstraints(Modifier.padding(horizontal = 12.dp)) {
            val effectiveWidth = maxWidth / LocalDensity.current.fontScale
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                if (effectiveWidth >= 160.dp) {
                    Icon(painterResource(R.drawable.ex_folder), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                }
                if (effectiveWidth >= 400.dp) {
                    Text(stringResource(R.string.locations), style = MaterialTheme.typography.bodyMedium)
                    PathSeparator()
                }
                if (effectiveWidth >= 240.dp) {
                    Text(stringResource(R.string.work), style = MaterialTheme.typography.bodyMedium)
                    PathSeparator()
                }
                Text(stringResource(R.string.reports), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PathSeparator() {
    Icon(painterResource(R.drawable.ex_chevron), null, Modifier.padding(horizontal = 8.dp).size(14.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SearchPreview(query: String, changed: (String) -> Unit, modifier: Modifier) {
    val label = stringResource(R.string.search_folder)
    Surface(modifier.heightIn(min = 48.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ex_search), null, Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            BasicTextField(query, changed, Modifier.weight(1f).padding(vertical = 12.dp).testTag("explorer-search")
                .semantics { contentDescription = label }, singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                decorationBox = { field ->
                    Box { if (query.isEmpty()) Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis); field() }
                })
            if (query.isNotEmpty()) PreviewIcon(R.drawable.ex_close, R.string.clear_search, onClick = { changed("") })
            else Spacer(Modifier.width(12.dp))
        }
    }
}

@Composable
private fun LocationsPane(expanded: Boolean, toggle: () -> Unit, modifier: Modifier = Modifier, onClose: (() -> Unit)? = null) {
    Surface(modifier.testTag(if (onClose == null) "explorer-locations" else "explorer-locations-drawer"), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.locations), Modifier.weight(1f).padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.titleMedium)
                if (onClose != null) PreviewIcon(R.drawable.ex_close, R.string.close_locations, onClick = onClose)
            }
            Spacer(Modifier.height(8.dp))
            LocationRow(R.string.all_locations, R.drawable.ex_locations)
            LocationRow(R.string.work, expanded = expanded, expand = toggle)
            if (expanded) {
                LocationRow(R.string.projects, indent = true, expanded = false)
                LocationRow(R.string.reports, indent = true, current = true, expanded = false, click = { onClose?.invoke() })
            }
            Spacer(Modifier.height(8.dp))
            LocationRow(R.string.pictures, expanded = false)
            LocationRow(R.string.personal, expanded = false)
            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            LocationRow(R.string.add_location, R.drawable.ex_add)
        }
    }
}

@Composable
private fun LocationRow(
    label: Int, icon: Int = R.drawable.ex_folder, indent: Boolean = false,
    current: Boolean = false, expanded: Boolean? = null, expand: () -> Unit = {}, click: () -> Unit = {},
) {
    val title = stringResource(label)
    val state = if (expanded == true) stringResource(R.string.expanded) else stringResource(R.string.collapsed)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
        .background(if (current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
        .selectable(current, onClick = click, role = Role.Button)
        .heightIn(min = 48.dp).padding(start = if (indent) 36.dp else 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(title, Modifier.weight(1f).padding(vertical = 12.dp), style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (expanded != null) {
            val action = stringResource(if (expanded) R.string.collapse_location else R.string.expand_location, title)
            IconButton(onClick = expand, modifier = Modifier.size(48.dp).semantics { stateDescription = state }) {
                Icon(painterResource(if (expanded) R.drawable.ex_expand else R.drawable.ex_chevron), action, Modifier.size(16.dp))
            }
        } else Spacer(Modifier.width(12.dp))
    }
}

@Composable
private fun PreviewCommands(
    icons: Boolean, viewChanged: (Boolean) -> Unit, fileMenu: @Composable () -> Unit,
    interactions: DocumentTabInteractions?, openLocations: () -> Unit, toggleSearch: () -> Unit,
    info: () -> Unit, hasTabs: Boolean, hasSelection: Boolean, short: Boolean,
) {
    var menu by remember { mutableStateOf<String?>(null) }
    Surface(color = MaterialTheme.colorScheme.surface) {
        BoxWithConstraints(Modifier.fillMaxWidth().testTag("command-bar").suiteTabDropTarget(interactions, dropped = { false })) {
            val effective = maxWidth / LocalDensity.current.fontScale
            Row(Modifier.fillMaxWidth().heightIn(min = if (short) 48.dp else 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    fileMenu()
                    if (hasTabs && effective >= 320.dp) {
                        Box {
                            OutlinedButton(onClick = { menu = "sort" }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                                Icon(painterResource(R.drawable.ex_sort), null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.sort))
                                Icon(painterResource(R.drawable.ex_expand), null, Modifier.padding(start = 8.dp).size(16.dp))
                            }
                            SortMenu(menu == "sort", { menu = null })
                        }
                        if (effective >= 540.dp) ViewChoices(icons, viewChanged)
                        else Box {
                            AppMenuButton(stringResource(R.string.view)) { menu = "view" }
                            ViewMenu(menu == "view", { menu = null }, icons, viewChanged)
                        }
                    }
                }
                Box {
                    PreviewIcon(R.drawable.ex_more, R.string.more_options, enabled = hasTabs, onClick = { menu = "more" })
                    AppDropdownMenu(menu == "more", { menu = null }) {
                        AppMenuItem(text = { Text(stringResource(R.string.locations)) }, onClick = { menu = null; openLocations() })
                        AppMenuItem(text = { Text(stringResource(R.string.search_folder)) }, onClick = { menu = null; toggleSearch() })
                        AppMenuItem(text = { Text(stringResource(R.string.sort)) }, onClick = { menu = "more-sort" })
                        AppMenuItem(text = { Text(stringResource(R.string.view)) }, onClick = { menu = "more-view" })
                        HorizontalDivider()
                        AppMenuItem(text = { Text(stringResource(R.string.up)) }, enabled = false, onClick = {})
                        AppMenuItem(text = { Text(stringResource(R.string.refresh)) }, enabled = false, onClick = {})
                        AppMenuItem(text = { Text(stringResource(R.string.add_location)) }, enabled = false, onClick = {})
                        AppMenuItem(text = { Text(stringResource(R.string.item_information)) }, enabled = hasSelection,
                            onClick = { menu = null; info() })
                    }
                    SortMenu(menu == "more-sort", { menu = null })
                    ViewMenu(menu == "more-view", { menu = null }, icons, viewChanged)
                }
            }
        }
    }
}

@Composable
private fun SortMenu(expanded: Boolean, dismiss: () -> Unit) {
    AppDropdownMenu(expanded, dismiss) {
        listOf(R.string.name, R.string.date_modified, R.string.type, R.string.size).forEach { label ->
            AppMenuItem(text = { Text(stringResource(label)) },
                trailingIcon = { if (label == R.string.name) Icon(painterResource(R.drawable.ex_check), null, Modifier.size(18.dp)) },
                onClick = dismiss)
        }
        HorizontalDivider()
        AppMenuItem(text = { Text(stringResource(R.string.ascending)) }, trailingIcon = {
            Icon(painterResource(R.drawable.ex_check), null, Modifier.size(18.dp)) }, onClick = dismiss)
        AppMenuItem(text = { Text(stringResource(R.string.descending)) }, onClick = dismiss)
    }
}

@Composable
private fun ViewMenu(expanded: Boolean, dismiss: () -> Unit, icons: Boolean, changed: (Boolean) -> Unit) {
    AppDropdownMenu(expanded, dismiss) {
        listOf(false to R.string.details, true to R.string.icons).forEach { (value, label) ->
            AppMenuItem(text = { Text(stringResource(label)) }, trailingIcon = {
                if (icons == value) Icon(painterResource(R.drawable.ex_check), null, Modifier.size(18.dp)) },
                onClick = { changed(value); dismiss() })
        }
    }
}

@Composable
private fun ViewChoices(icons: Boolean, changed: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.width(192.dp * LocalDensity.current.fontScale)) {
        listOf(false to R.string.details, true to R.string.icons).forEachIndexed { index, (value, label) ->
            SegmentedButton(selected = icons == value, onClick = { changed(value) },
                shape = SegmentedButtonDefaults.itemShape(index, 2),
                icon = { Icon(painterResource(if (icons == value) R.drawable.ex_check else if (value) R.drawable.ex_grid else R.drawable.ex_details), null,
                    Modifier.size(18.dp)) },
                label = { Text(stringResource(label), maxLines = 1) })
        }
    }
}

@Composable
private fun DetailFiles(selected: String?, select: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().testTag("explorer-details")) {
        val effective = maxWidth / LocalDensity.current.fontScale
        val scale = LocalDensity.current.fontScale
        val ascending = stringResource(R.string.ascending)
        val full = effective >= 600.dp
        val metadata = effective >= 440.dp
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .heightIn(min = 40.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).padding(start = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.name), Modifier.semantics { heading(); stateDescription = ascending },
                        style = MaterialTheme.typography.labelLarge)
                    Icon(painterResource(R.drawable.ex_up), stringResource(R.string.ascending), Modifier.padding(start = 6.dp).size(14.dp))
                }
                if (full) MetadataCell(stringResource(R.string.date_modified), Modifier.width(168.dp * scale))
                if (metadata) {
                    MetadataCell(stringResource(R.string.type), Modifier.width(128.dp * scale))
                    MetadataCell(stringResource(R.string.size), Modifier.width(72.dp * scale), end = true)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            LazyColumn(Modifier.fillMaxSize().testTag("explorer-file-list"), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                items(previewFiles, key = { it.name }) { file ->
                    PreviewFileRow(file, file.name == selected, full, metadata, { select(file.name) })
                }
            }
        }
    }
}

@Composable
private fun PreviewFileRow(file: PreviewFile, selected: Boolean, full: Boolean, metadata: Boolean, click: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hover by interaction.collectIsHoveredAsState()
    var focus by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    val type = stringResource(file.type)
    val scale = LocalDensity.current.fontScale
    val colors = MaterialTheme.colorScheme
    val background = when { selected -> colors.primaryContainer; hover -> colors.surfaceContainer; else -> Color.Transparent }
    Row(Modifier.fillMaxWidth().testTag("preview-file-${file.name}").clip(shape).background(background)
        .border(if (focus) 2.dp else 1.dp, if (selected || focus) colors.primary else Color.Transparent, shape)
        .onFocusChanged { focus = it.isFocused }
        .selectable(selected, interactionSource = interaction, indication = LocalIndication.current, role = Role.Checkbox, onClick = click)
        .semantics(mergeDescendants = true) { contentDescription = "${file.name}, $type, ${file.modified}, ${file.size}" }
        .heightIn(min = if (metadata) 48.dp else 64.dp).padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            if (selected) Icon(painterResource(R.drawable.ex_check), stringResource(R.string.selected_item), Modifier.size(18.dp), tint = colors.primary)
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            FileGlyph(file)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) colors.onPrimaryContainer else colors.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!metadata) Text(if (file.glyph.isEmpty()) type else "$type · ${file.size}",
                    color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val metadataColor = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
        if (full) MetadataCell(file.modified, Modifier.width(168.dp * scale), color = metadataColor)
        if (metadata) {
            MetadataCell(type, Modifier.width(128.dp * scale), color = metadataColor)
            MetadataCell(file.size, Modifier.width(72.dp * scale), end = true, color = metadataColor)
        }
    }
}

@Composable
private fun MetadataCell(text: String, modifier: Modifier = Modifier, end: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Box(modifier.padding(start = 8.dp, end = 4.dp), contentAlignment = if (end) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(text, color = color, style = MaterialTheme.typography.bodySmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FileGlyph(file: PreviewFile, large: Boolean = false) {
    val size = if (large) 48.dp else 24.dp
    val color = when (file.glyph) { "PDF" -> MaterialTheme.colorScheme.error; "TXT" -> MaterialTheme.colorScheme.onSurfaceVariant; else -> MaterialTheme.colorScheme.primary }
    val icon = when (file.glyph) {
        "PDF" -> R.drawable.ex_file_pdf
        "X" -> R.drawable.ex_file_spreadsheet
        "CSV" -> R.drawable.ex_file_csv
        "TXT" -> R.drawable.ex_file
        else -> R.drawable.ex_folder_solid
    }
    Icon(painterResource(icon), null, Modifier.size(size), tint = color)
}

@Composable
private fun IconFiles(selected: String?, select: (String) -> Unit) {
    LazyVerticalGrid(GridCells.Adaptive(144.dp * LocalDensity.current.fontScale), Modifier.fillMaxSize().testTag("explorer-icons"),
        contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(previewFiles, key = { it.name }) { file ->
            val active = selected == file.name
            Column(Modifier.clip(RoundedCornerShape(12.dp))
                .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .border(1.dp, if (active) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp))
                .selectable(active, role = Role.Checkbox, onClick = { select(file.name) }).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    FileGlyph(file, large = true)
                    if (active) Icon(painterResource(R.drawable.ex_check), stringResource(R.string.selected_item),
                        Modifier.align(Alignment.TopEnd).size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(12.dp))
                Text(file.name, style = MaterialTheme.typography.bodyMedium,
                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(if (file.glyph.isEmpty()) stringResource(file.type) else file.size,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PreviewStatus(count: Int, selection: Boolean) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                val items = pluralStringResource(R.plurals.item_count, count, count)
                Text(if (selection) stringResource(R.string.selection_count, items) else items,
                    Modifier.weight(1f).testTag("explorer-status"), style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.sample_content), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewIcon(@DrawableRes icon: Int, label: Int, enabled: Boolean = true, onClick: () -> Unit = {}) {
    val title = stringResource(label)
    TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(title) } }, state = rememberTooltipState()) {
        if (icon in listOf(R.drawable.ex_back, R.drawable.ex_forward, R.drawable.ex_up, R.drawable.ex_refresh)) {
            FilledTonalIconButton(onClick, Modifier.size(48.dp), enabled = enabled) {
                Icon(painterResource(icon), title, Modifier.size(20.dp))
            }
        } else IconButton(onClick, Modifier.size(48.dp), enabled = enabled) {
            Icon(painterResource(icon), title, Modifier.size(20.dp))
        }
    }
}
