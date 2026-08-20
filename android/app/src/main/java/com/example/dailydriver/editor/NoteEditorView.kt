package com.example.dailydriver.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** Test anchors, following the convention in `ShellTestTags`. */
internal object EditorTestTags {
    const val TITLE_FIELD = "editor_title_field"
    const val BODY_FIELD = "editor_body_field"
    const val KEEP_BUTTON = "editor_keep"
    const val DISCARD_BUTTON = "editor_discard"
}

/**
 * The note editor (T020/T021), rendered inline in the shell rather than as a separate Activity —
 * no `startActivity`, no window transition, no extra Recents entry (§4.6).
 *
 * **Bottom-anchored and grows with what you write** (design check-in, 2026-08-19). A one-line
 * grocery note barely disturbs the screen; a journal entry expands upward until it's using most
 * of it, with the transcript still visible above. That was the answer to serving quick capture
 * and long-form writing from one surface, and it also matches the bottom-anchored convention every
 * other view here already follows — `AppListView`, `NotifListView`, and the transcript all grow
 * from the prompt upward.
 *
 * **Implemented as a layout swap rather than a floating `Box`.** §4.6's requirements are about
 * Activities and windows, both of which this satisfies; a scrim-and-float would put the editor
 * *over* the transcript, whereas growing in place keeps the transcript legible above it and gets
 * the "grows with content" behaviour for free from the parent `Column`'s weights.
 *
 * **Body-first**: focus lands in the body, because `:note` should mean "start writing", not
 * "start naming". The title is optional and falls back to a date-time stamp
 * ([NoteDraft.resolvedTitle]).
 */
@Composable
fun NoteEditorView(
    draft: NoteDraft,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onKeep: () -> Unit,
    onDiscard: () -> Unit,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val bodyFocus = remember { FocusRequester() }

    // Straight into the body on open — a quick note should be one command and then typing.
    LaunchedEffect(draft.id) { bodyFocus.requestFocus() }

    val dim = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = draft.title,
                onValueChange = onTitleChange,
                textStyle = textStyle,
                singleLine = true,
                cursorBrush = SolidColor(textStyle.color),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next,
                    // Same reasoning as the shell's input line: a title can be a command-ish
                    // string or a tag, and autocorrect mangling it silently is worse than typos.
                    autoCorrectEnabled = false,
                ),
                decorationBox = { field ->
                    // The stamp that would be stored if this is left blank, shown as a
                    // placeholder so the fallback is visible rather than a surprise on save.
                    if (draft.title.isEmpty()) Text(text = "untitled", style = dim)
                    field()
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag(EditorTestTags.TITLE_FIELD),
            )

            // Text buttons, not icons: this app ships no icon set and the whole surface is
            // monospace text. ✗ before ✓ so the destructive one isn't under the thumb's
            // resting position at the right edge.
            EditorAction(label = "✗", tag = EditorTestTags.DISCARD_BUTTON, style = dim, onClick = onDiscard)
            EditorAction(label = "✓", tag = EditorTestTags.KEEP_BUTTON, style = textStyle, onClick = onKeep)
        }

        BasicTextField(
            value = draft.body,
            onValueChange = onBodyChange,
            textStyle = textStyle,
            cursorBrush = SolidColor(textStyle.color),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = true),
            modifier = Modifier
                .fillMaxWidth()
                // Grows with content from a couple of lines up to a ceiling, past which the body
                // scrolls internally rather than pushing the transcript off the top entirely.
                .heightIn(min = 48.dp, max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp)
                .focusRequester(bodyFocus)
                .testTag(EditorTestTags.BODY_FIELD),
        )
    }
}

/** Text, not an icon: this project ships no icon set and the whole surface is monospace. */
@Composable
private fun EditorAction(label: String, tag: String, style: TextStyle, onClick: () -> Unit) {
    Text(
        text = label,
        style = style,
        modifier = Modifier
            .testTag(tag)
            // Plain clickable, ripple and all — matching AppListView's rows rather than
            // introducing a second tap treatment for no reason.
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/**
 * Confirmation shown when ✗ would destroy actual writing. Not a dialog — a line above the editor,
 * consistent with everything else in this shell being text.
 *
 * This exists only because T138's undo snackbar doesn't yet, and discard has no way back from the
 * UI. Once undo lands, this should go: confirmations on reversible actions are pure friction.
 */
@Composable
fun DiscardConfirmation(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "discard this note?", style = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
        EditorAction(label = "discard", tag = "editor_confirm_discard", style = textStyle, onClick = onConfirm)
        EditorAction(label = "keep", tag = "editor_cancel_discard", style = textStyle, onClick = onCancel)
    }
}
