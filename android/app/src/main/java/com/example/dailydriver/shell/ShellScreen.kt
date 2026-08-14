package com.example.dailydriver.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.dailydriver.apps.AppEntry
import com.example.dailydriver.apps.AppsCommand
import com.example.dailydriver.apps.filteredBy
import com.example.dailydriver.apps.soleAutoLaunchTarget
import kotlinx.coroutines.delay

/** The prompt glyph, shown on the input line and on every recorded command in the scrollback. */
private const val PROMPT = "❯"

/** How long typing must be idle before a uniquely-matched app opens on its own. */
private const val AUTO_LAUNCH_DELAY_MS = 1_000L

/**
 * Stable anchors for Compose UI tests (androidx.compose.ui.test), per the "semantics/test tags
 * rather than brittle text matching" convention in android/CLAUDE.md. Nothing consumes these yet
 * — they exist so the first UI test doesn't also have to invent them.
 */
internal object ShellTestTags {
    const val TRANSCRIPT_LIST = "shell_transcript_list"
    const val INPUT_FIELD = "shell_input_field"
    const val APP_LIST = "shell_app_list"
}

/**
 * The launcher surface: scrollback transcript above, input line pinned at the bottom — or, while
 * [appsCommand] is non-null, the `:apps` browse list in place of the transcript. Same input line
 * either way; it drives the live filter in apps mode instead of submitting commands.
 *
 * Stateless — all state is hoisted to [ShellViewModel], per the convention in android/CLAUDE.md.
 */
@Composable
fun ShellScreen(
    transcript: Transcript,
    input: String,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    appsCommand: AppsCommand?,
    apps: List<AppEntry>,
    onAppClick: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val monospace = LocalTextStyle.current.copy(
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onBackground,
    )

    // IME open by default (design doc §4.1) — focusing the field is what raises it.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Keep the newest entry visible as the transcript grows.
    LaunchedEffect(transcript.entries.size) {
        if (transcript.entries.isNotEmpty()) {
            listState.scrollToItem(transcript.entries.lastIndex)
        }
    }

    val filteredApps = remember(apps, appsCommand) {
        appsCommand?.let { apps.filteredBy(it.query) }.orEmpty()
    }
    // Hoisted out of the apps-mode branch below because Enter needs it too, not just the timer.
    val autoLaunchTarget = remember(filteredApps, appsCommand) {
        appsCommand?.let { filteredApps.soleAutoLaunchTarget(it.query) }
    }

    // Auto-launch after a pause in typing. Keyed on the query and not just the target: "chr",
    // "chro" and "chrom" all resolve to the same app, so keying on the target alone would leave
    // the original timer running and fire mid-word. Re-keying per keystroke cancels and restarts
    // it, which is what "a one second pause" actually means.
    LaunchedEffect(autoLaunchTarget, appsCommand?.query) {
        val target = autoLaunchTarget ?: return@LaunchedEffect
        delay(AUTO_LAUNCH_DELAY_MS)
        onAppClick(target)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 12.dp),
    ) {
        if (appsCommand != null) {
            AppListView(
                apps = filteredApps,
                onAppClick = onAppClick,
                textStyle = monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag(ShellTestTags.TRANSCRIPT_LIST),
                verticalArrangement = Arrangement.Bottom,
            ) {
                items(transcript.entries) { entry ->
                    TranscriptLine(entry = entry, textStyle = monospace)
                }
            }
        }

        InputLine(
            value = input,
            onValueChange = onInputChange,
            // Enter opens a uniquely-matched app immediately rather than waiting out the pause;
            // with no unique match it falls through to the normal submit, which no-ops in apps
            // mode rather than recording ":apps ca" as a command.
            onSubmit = { autoLaunchTarget?.let(onAppClick) ?: onSubmit() },
            textStyle = monospace,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .focusRequester(focusRequester),
        )
    }
}

@Composable
private fun TranscriptLine(entry: TranscriptEntry, textStyle: TextStyle) {
    when (entry) {
        is TranscriptEntry.Input -> Row {
            Text(text = "$PROMPT ", style = textStyle)
            Text(text = entry.text, style = textStyle)
        }

        is TranscriptEntry.Output -> Text(
            text = entry.text,
            style = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
        )
    }
}

@Composable
private fun InputLine(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text = "$PROMPT ", style = textStyle)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = textStyle,
            singleLine = true,
            cursorBrush = SolidColor(textStyle.color),
            // autoCorrectEnabled = false is load-bearing, not cosmetic: T0 is exact-match against
            // aliases and reserved commands (:apps, :note, ...) — IME autocorrect silently mangling
            // a command mid-type would make T0 miss and fall through to T1/T2 for no visible reason.
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onSend = { onSubmit() }),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ShellTestTags.INPUT_FIELD),
        )
    }
}
