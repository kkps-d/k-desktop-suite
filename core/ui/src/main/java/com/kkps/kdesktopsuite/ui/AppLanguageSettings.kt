package com.kkps.kdesktopsuite.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.Annotation
import android.text.Spanned
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Locale

internal fun supportedAppLanguages(context: Context): List<Locale> =
    context.resources.getXml(R.xml.core_ui_supported_locales).use { parser ->
        buildList {
            while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "locale") {
                    add(Locale.forLanguageTag(parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name")))
                }
                parser.next()
            }
        }
    }

internal fun List<Locale>.matchingAppLanguage(tags: String): Locale? {
    val requested = Locale.forLanguageTag(tags.substringBefore(','))
    return firstOrNull { it == requested } ?: firstOrNull { it.language == requested.language }
}

internal fun openAppLanguageSettings(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 33) return false
    return try {
        context.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null)))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

/** Settings navigation stays inside the host's caption-safe surface and saved state. */
@Composable
fun AppLanguageSettingsPage(
    title: String, backLabel: String, onBack: () -> Unit, modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(availableWidth: Dp) -> Unit,
) {
    var languageOpen by rememberSaveable { mutableStateOf(false) }
    if (languageOpen) {
        BackHandler { languageOpen = false }
        AppLanguagePage(onBack = { languageOpen = false }, modifier = modifier)
    } else {
        AppSettingsPage(title, backLabel, onBack, modifier) { availableWidth ->
            content(availableWidth)
            AppLanguageSettingsSection(onOpenPicker = { languageOpen = true })
        }
    }
}

@Composable
private fun currentAppLanguage(): MutableState<String> {
    // Read again after a configuration change or a return from Android Settings.
    val configuration = LocalConfiguration.current
    val owner = LocalLifecycleOwner.current
    val state = remember(configuration) {
        mutableStateOf(AppCompatDelegate.getApplicationLocales().toLanguageTags())
    }
    DisposableEffect(owner, state) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.value = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return state
}

@Composable
fun AppLanguageSettingsSection(onOpenPicker: () -> Unit) {
    val context = LocalContext.current
    val languages = remember(context) { supportedAppLanguages(context) }
    val tag by currentAppLanguage()
    var supportedOpen by rememberSaveable { mutableStateOf(false) }
    val language = languages.matchingAppLanguage(tag)
    val summary = if (tag.isEmpty()) stringResource(R.string.core_ui_language_follow_system)
        else language?.getDisplayName(language) ?: Locale.forLanguageTag(tag.substringBefore(',')).displayName

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.core_ui_language_title), Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium)
        Text(summary, Modifier.testTag("language-summary"), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = { if (!openAppLanguageSettings(context)) onOpenPicker() },
            modifier = Modifier.testTag("language-settings").heightIn(min = 48.dp)) {
            Text(stringResource(if (Build.VERSION.SDK_INT >= 33) R.string.core_ui_language_open_system else R.string.core_ui_language_change_in_app))
        }
        if (Build.VERSION.SDK_INT >= 33) {
            val source = LocalResources.current.getText(R.string.core_ui_language_links) as Spanned
            val linkStyle = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
            val links = buildAnnotatedString {
                append(source.toString())
                source.getSpans(0, source.length, Annotation::class.java).filter { it.key == "action" }.forEach { annotation ->
                    addLink(LinkAnnotation.Clickable(annotation.value, linkStyle) {
                        if (annotation.value == "supported") supportedOpen = true else onOpenPicker()
                    }, source.getSpanStart(annotation), source.getSpanEnd(annotation))
                }
            }
            Text(links, Modifier.testTag("language-help"), style = MaterialTheme.typography.bodySmall)
        }
    }
    if (supportedOpen) {
        AlertDialog(onDismissRequest = { supportedOpen = false },
            title = { Text(stringResource(R.string.core_ui_language_supported)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                languages.forEach { Text(it.getDisplayName(it)) }
            } },
            confirmButton = { TextButton(onClick = { supportedOpen = false }) {
                Text(stringResource(R.string.core_ui_language_close))
            } })
    }
}

@Composable
fun AppLanguagePage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val languages = remember(context) { supportedAppLanguages(context) }
    var selected by currentAppLanguage()
    val selectedLanguage = languages.matchingAppLanguage(selected)
    val options = listOf("" to stringResource(R.string.core_ui_language_follow_system)) +
        languages.map { it.toLanguageTag() to it.getDisplayName(it) }
    AppSettingsPage(stringResource(R.string.core_ui_language_title), stringResource(R.string.core_ui_language_back),
        onBack, modifier.testTag("language-page")) {
        Text(stringResource(R.string.core_ui_language_explanation), style = MaterialTheme.typography.bodyMedium)
        Column(Modifier.fillMaxWidth().selectableGroup()) {
            options.forEach { (tag, label) ->
                val checked = if (tag.isEmpty()) selected.isEmpty() else selectedLanguage?.toLanguageTag() == tag
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("language-${tag.ifEmpty { "system" }}")
                    .selectable(selected = checked, role = Role.RadioButton) {
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                        // An override matching the device language may not change configuration.
                        selected = tag
                    }.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    RadioButton(selected = checked, onClick = null)
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
