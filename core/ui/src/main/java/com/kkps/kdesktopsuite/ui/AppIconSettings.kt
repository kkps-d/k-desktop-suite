@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.kkps.kdesktopsuite.ui

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

enum class AppIconStyle { Classic, Glass }
enum class AppIconTone { System, Light, Dark }

data class AppIconChoice(val style: AppIconStyle = AppIconStyle.Classic, val tone: AppIconTone = AppIconTone.System) {
    val alias: String get() = "Launcher${style.name}${tone.name}"
    fun isDark(appearanceDark: Boolean): Boolean = when (tone) {
        AppIconTone.System -> appearanceDark
        AppIconTone.Light -> false
        AppIconTone.Dark -> true
    }
}

data class AppIconAssets(
    @DrawableRes val classicLight: Int, @DrawableRes val classicDark: Int,
    @DrawableRes val glassLight: Int, @DrawableRes val glassDark: Int,
) {
    @DrawableRes
    fun resource(choice: AppIconChoice, appearanceDark: Boolean): Int = when (choice.style) {
        AppIconStyle.Classic -> if (choice.isDark(appearanceDark)) classicDark else classicLight
        AppIconStyle.Glass -> if (choice.isDark(appearanceDark)) glassDark else glassLight
    }
}

private fun iconPreferences(context: Context): SharedPreferences =
    context.applicationContext.getSharedPreferences("app-icon", Context.MODE_PRIVATE)

fun readAppIconChoice(context: Context): AppIconChoice {
    val preferences = iconPreferences(context)
    return AppIconChoice(
        AppIconStyle.entries.firstOrNull { it.name == preferences.getString("style", null) } ?: AppIconStyle.Classic,
        AppIconTone.entries.firstOrNull { it.name == preferences.getString("tone", null) } ?: AppIconTone.System,
    )
}

/** Change launcher aliases without disabling the real editor activity or killing its drafts. */
private fun applyLauncherIcon(context: Context, choice: AppIconChoice) {
    val manager = context.packageManager
    val changes = AppIconStyle.entries.flatMap { style -> AppIconTone.entries.map { AppIconChoice(style, it) } }
        .mapNotNull { candidate ->
            val component = ComponentName(context.packageName, "${context.packageName}.${candidate.alias}")
            val current = manager.getComponentEnabledSetting(component)
            val enabled = current == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (current == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && candidate == AppIconChoice())
            val selected = candidate == choice
            if (enabled == selected) null else component to if (selected) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
    if (changes.isEmpty()) return
    if (Build.VERSION.SDK_INT >= 33) {
        manager.setComponentEnabledSettings(changes.map { (component, state) ->
            PackageManager.ComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        })
    } else {
        // Enable the destination first so legacy Android always retains a launcher entry.
        changes.sortedBy { if (it.second == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) 0 else 1 }
            .forEach { (component, state) -> manager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP) }
    }
}

fun restoreAppIcon(context: Context) = applyLauncherIcon(context, readAppIconChoice(context))

fun changeAppIcon(context: Context, choice: AppIconChoice) {
    applyLauncherIcon(context, choice)
    iconPreferences(context).edit().putString("style", choice.style.name).putString("tone", choice.tone.name).apply()
}

@Composable
internal fun rememberAppIconChoice(): AppIconChoice {
    val context = LocalContext.current.applicationContext
    var choice by remember(context) { mutableStateOf(readAppIconChoice(context)) }
    DisposableEffect(context) {
        val preferences = iconPreferences(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> choice = readAppIconChoice(context) }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        choice = readAppIconChoice(context)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return choice
}

@Composable
fun AppIconSettingsSection(assets: AppIconAssets, availableWidth: Dp) {
    val context = LocalContext.current
    val choice = rememberAppIconChoice()
    val dark = MaterialTheme.colorScheme.surface.luminance() < .5f
    val largeText = LocalDensity.current.fontScale > 1.3f
    val stackTones = availableWidth < 360.dp || largeText
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.app_icon_title), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = if (availableWidth < 500.dp || largeText) 1 else 2) {
            AppIconStyle.entries.forEach { style ->
                val selected = choice.style == style
                OutlinedCard(Modifier.weight(1f).testTag("icon-style-$style")
                    .selectable(selected, role = Role.RadioButton) { changeAppIcon(context, choice.copy(style = style)) },
                    border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.outlinedCardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Image(painterResource(assets.resource(choice.copy(style = style), dark)), null,
                            Modifier.size(56.dp).clip(MaterialTheme.shapes.medium))
                        Text(stringResource(if (style == AppIconStyle.Classic) R.string.app_icon_classic else R.string.app_icon_glass),
                            Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        RadioButton(selected, onClick = null)
                    }
                }
            }
        }
        Text(stringResource(R.string.app_icon_palette), style = MaterialTheme.typography.labelLarge)
        FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = if (stackTones) 1 else 3) {
            AppIconTone.entries.forEachIndexed { index, tone ->
                ToggleButton(checked = choice.tone == tone, onCheckedChange = { changeAppIcon(context, choice.copy(tone = tone)) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("icon-tone-$tone").semantics { role = Role.RadioButton },
                    shapes = if (stackTones) ToggleButtonDefaults.shapesFor(ToggleButtonDefaults.size) else when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        2 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    }) {
                    Text(stringResource(when (tone) {
                        AppIconTone.System -> R.string.app_icon_system
                        AppIconTone.Light -> R.string.app_icon_light
                        AppIconTone.Dark -> R.string.app_icon_dark
                    }))
                }
            }
        }
        Text(stringResource(R.string.app_icon_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
