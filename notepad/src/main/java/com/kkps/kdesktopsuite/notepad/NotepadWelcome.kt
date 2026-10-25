package com.kkps.kdesktopsuite.notepad

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
internal fun NotepadWelcome(
    recentFiles: List<RecentFile>,
    new: () -> Unit,
    open: () -> Unit,
    openRecent: (String) -> Unit,
    openEnabled: Boolean,
    modifier: Modifier = Modifier,
    focusRequest: Int = 0,
) {
    val startFocus = remember { FocusRequester() }
    LaunchedEffect(focusRequest) { startFocus.requestFocus() }

    BoxWithConstraints(modifier.fillMaxSize().testTag("welcome-screen")) {
        val columns = maxWidth >= 600.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.widthIn(max = 800.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Image(painterResource(R.drawable.ic_notepad), null, Modifier.size(64.dp))
                    Text(stringResource(R.string.welcome), style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.semantics { heading() })
                }
                if (columns) {
                    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        WelcomeStart(new, open, openEnabled, startFocus, Modifier.weight(1f))
                        WelcomeRecent(recentFiles, openRecent, openEnabled, Modifier.weight(1f))
                    }
                } else {
                    WelcomeStart(new, open, openEnabled, startFocus, Modifier.fillMaxWidth())
                    WelcomeRecent(recentFiles, openRecent, openEnabled, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
internal fun WelcomeTab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(
        modifier = modifier.testTag("welcome-tab").clip(DocumentTabShape)
            .then(if (focused) Modifier.border(TabBarDimensions.focusOutlineWidth, MaterialTheme.colorScheme.primary, DocumentTabShape) else Modifier)
            .semantics { selected = true }
            .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick),
        shape = DocumentTabShape,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(Modifier.padding(start = TabBarDimensions.contentStartPadding, end = TabBarDimensions.contentEndPadding),
            contentAlignment = Alignment.CenterStart) {
            Text(stringResource(R.string.welcome), style = NotepadTabTypography.title,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun WelcomeStart(
    new: () -> Unit, open: () -> Unit, openEnabled: Boolean,
    focus: FocusRequester, modifier: Modifier,
) {
    Column(modifier.testTag("welcome-start"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.start), style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() })
        WelcomeAction(stringResource(R.string.new_document), new,
            modifier = Modifier.focusRequester(focus).testTag("welcome-new"))
        WelcomeAction(stringResource(R.string.open_file), open, Modifier.testTag("welcome-open"), openEnabled)
    }
}

@Composable
private fun WelcomeRecent(
    files: List<RecentFile>, open: (String) -> Unit, enabled: Boolean, modifier: Modifier,
) {
    Column(modifier.testTag("welcome-recent"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.recent), style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() })
        if (files.isEmpty()) {
            Text(stringResource(R.string.no_recent_files), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            files.take(5).forEach { file ->
                WelcomeAction(file.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.text_file),
                    { open(file.uri) }, Modifier.testTag("recent-file-${file.uri}"), enabled)
            }
        }
    }
}

@Composable
private fun WelcomeAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(
        onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text, Modifier.weight(1f))
    }
}

@Preview(name = "Welcome phone", widthDp = 400, heightDp = 800)
@Preview(name = "Welcome desktop", widthDp = 1000, heightDp = 700)
@Preview(name = "Welcome short", widthDp = 700, heightDp = 320)
@Preview(name = "Welcome large text", widthDp = 320, heightDp = 600, fontScale = 2f)
@Preview(name = "Welcome dark", widthDp = 800, heightDp = 600, uiMode = 0x20)
@Composable
private fun WelcomePreview() {
    NotepadTheme {
        NotepadWelcome(listOf(RecentFile("preview:notes", "Notes.txt")), {}, {}, {}, true)
    }
}
