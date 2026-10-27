@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.kkps.kdesktopsuite.notepad

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kkps.kdesktopsuite.ui.AppSettingsPage

internal enum class AppTheme(@StringRes val label: Int) {
    Light(R.string.theme_light), Dark(R.string.theme_dark), Auto(R.string.theme_auto),
}

internal enum class NoteFontStyle(@StringRes val label: Int, val fontFamily: FontFamily) {
    System(R.string.font_system, FontFamily.Default),
    Monospace(R.string.font_monospace, FontFamily(Font(com.kkps.kdesktopsuite.ui.R.font.noto_sans_mono))),
    Serif(R.string.font_serif, FontFamily(Font(com.kkps.kdesktopsuite.ui.R.font.noto_serif))),
    SansSerif(R.string.font_sans_serif, FontFamily(Font(com.kkps.kdesktopsuite.ui.R.font.noto_sans))),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotepadSettingsScreen(
    appTheme: AppTheme,
    fontStyle: NoteFontStyle,
    changeAppTheme: (AppTheme) -> Unit,
    changeFontStyle: (NoteFontStyle) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppSettingsPage(stringResource(R.string.settings),stringResource(R.string.back),onBack,modifier) { availableWidth ->
        val fontScale = LocalDensity.current.fontScale
        // Stack choices when three readable, accessible controls won't fit comfortably.
        val stackChoices = availableWidth < 360.dp || fontScale > 1.3f
        val fontColumns = when {
            stackChoices -> 1
            availableWidth < 640.dp -> 2
            else -> 4
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.app_theme), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            FlowRow(
                Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = if (stackChoices) 1 else 3,
            ) {
                AppTheme.entries.forEachIndexed { index, option ->
                    ToggleButton(
                        checked = appTheme == option,
                        onCheckedChange = { changeAppTheme(option) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            .testTag("theme-${option.name}").semantics { role = Role.RadioButton },
                        shapes = if (stackChoices) ToggleButtonDefaults.shapesFor(ToggleButtonDefaults.size) else when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            AppTheme.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        icon = if (appTheme == option) ({ Icon(painterResource(R.drawable.ic_check), null) }) else null,
                    ) { Text(stringResource(option.label)) }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.font_style), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            FlowRow(
                Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = fontColumns,
            ) {
                NoteFontStyle.entries.forEach { option ->
                    val selected = fontStyle == option
                    OutlinedCard(
                        modifier = Modifier.weight(1f).testTag("font-${option.name}")
                            .selectable(selected = selected, role = Role.RadioButton) { changeFontStyle(option) },
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                            contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                        ),
                        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.font_sample), style = MaterialTheme.typography.headlineMedium.copy(fontFamily = option.fontFamily))
                                RadioButton(selected = selected, onClick = null)
                            }
                            Text(stringResource(option.label), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.font_preview), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.font_preview_text), Modifier.testTag("font-preview"),
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = fontStyle.fontFamily))
                }
            }
        }
    }
}

@Preview(name = "Settings phone", widthDp = 400, heightDp = 800)
@Preview(name = "Settings desktop", widthDp = 1000, heightDp = 700)
@Preview(name = "Settings short", widthDp = 700, heightDp = 320)
@Preview(name = "Settings large text", widthDp = 320, heightDp = 800, fontScale = 2f)
@Preview(name = "Settings dark", widthDp = 400, heightDp = 800, uiMode = 0x20)
@Composable
private fun NotepadSettingsPreview() {
    NotepadTheme {
        Surface {
            NotepadSettingsScreen(AppTheme.Auto, NoteFontStyle.Monospace, {}, {}, {})
        }
    }
}
