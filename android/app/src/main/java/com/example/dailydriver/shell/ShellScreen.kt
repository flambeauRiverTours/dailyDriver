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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** The prompt glyph, shown on the input line and on every recorded command in the scrollback. */
private const val PROMPT = "❯"

/**
 * The launcher surface: scrollback transcript above, input line pinned at the bottom.
 *
 * Stateless — all state is hoisted to [ShellViewModel], per the convention in android/CLAUDE.md.
 */
@Composable
fun ShellScreen(
    transcript: Transcript,
    input: String,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 12.dp),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.Bottom,
        ) {
            items(transcript.entries) { entry ->
                TranscriptLine(entry = entry, textStyle = monospace)
            }
        }

        InputLine(
            value = input,
            onValueChange = onInputChange,
            onSubmit = onSubmit,
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
            cursorBrush = androidx.compose.ui.graphics.SolidColor(textStyle.color),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onSubmit() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
