package com.kkps.kdesktopsuite.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class LicenseEntry(val title: String, val summary: String, val files: List<String>)

@Composable
fun AppLicensesPage(selected: Int?, onSelect: (Int) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val entries = listOf(
        LicenseEntry(stringResource(R.string.license_app_source), "MIT", listOf("KDesktopSuite-MIT.txt")),
        LicenseEntry(stringResource(R.string.license_dependencies), stringResource(R.string.license_dependencies_summary),
            listOf("Runtime-NOTICES.txt")),
        LicenseEntry("Material Symbols", "Apache License 2.0",
            listOf("MaterialSymbols-NOTICE.txt", "MaterialSymbols-LICENSE.txt")),
        LicenseEntry("Noto Sans", "SIL Open Font License 1.1", listOf("noto_sans_OFL.txt")),
        LicenseEntry("Noto Serif", "SIL Open Font License 1.1", listOf("noto_serif_OFL.txt")),
        LicenseEntry("Noto Sans Mono", "SIL Open Font License 1.1", listOf("noto_sans_mono_OFL.txt")),
    )
    // Recreate the reading surface so every license opens at the top.
    key(selected) {
        AppSettingsPage(title = selected?.let { entries[it].title } ?: stringResource(R.string.open_source_licenses),
            backLabel = stringResource(R.string.licenses_back), onBack = onBack,
            modifier = modifier.testTag("licenses-page")) {
            val entry = selected?.let { entries[it] }
            if (entry == null) {
                Text(stringResource(R.string.licenses_description), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column {
                    entries.forEachIndexed { index, item ->
                        ListItem(headlineContent = { Text(item.title) },
                            supportingContent = { Text(item.summary) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                                .clickable(role = Role.Button) { onSelect(index) })
                        if (index < entries.lastIndex) HorizontalDivider()
                    }
                }
            } else {
                val assets = LocalContext.current.assets
                var failed by remember { mutableStateOf(false) }
                val text by produceState<String?>(null, entry.files) {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            entry.files.joinToString("\n\n") { file ->
                                assets.open("licenses/$file").bufferedReader().use { it.readText() }
                            }
                        }
                    }
                    failed = result.isFailure
                    value = result.getOrNull()
                }
                when {
                    failed -> Text(stringResource(R.string.license_load_error))
                    text == null -> CircularProgressIndicator()
                    else -> SelectionContainer {
                        Text(text.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
