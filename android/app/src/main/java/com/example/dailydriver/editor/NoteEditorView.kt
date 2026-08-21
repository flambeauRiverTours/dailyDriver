package com.example.dailydriver.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailydriver.notes.ChecklistItem
import com.example.dailydriver.notes.NoteType

/**
 * Header action glyphs.
 *
 * `×` (U+00D7 MULTIPLICATION SIGN), **not** `✗` (U+2717 BALLOT X): the ballot X is drawn with
 * deliberately irregular, hand-inked strokes in most fonts, which reads as sketchy next to a
 * monospace terminal. The multiplication sign is two straight strokes at a right angle — the
 * geometric, minimal form, and the one every close affordance already uses.
 *
 * `✓` (U+2713) is kept: it's already two straight strokes, and the obvious "straighter"
 * substitutes are worse here. An arrow like `↵` reads as *insert a newline* when it's sitting in
 * the header of a text editor, which is actively misleading rather than merely plain.
 *
 * Sized modestly above body text because both are small glyphs — `×` in particular is a math
 * operator and renders noticeably smaller than surrounding letters at the same point size. Held
 * to 18sp rather than larger: the check's stroke weight scales with it, and at 22sp it read
 * heavier and rounder than the crisp `×` beside it, which was the opposite of the intent. The
 * touch target is set independently by [EditorAction], so this size is purely how the glyph
 * looks — shrinking it costs nothing in tappability.
 */
private const val DISCARD_GLYPH = "×"
private const val KEEP_GLYPH = "✓"
private val GLYPH_SIZE = 18.sp

/** Test anchors, following the convention in `ShellTestTags`. */
internal object EditorTestTags {
    const val TITLE_FIELD = "editor_title_field"
    const val BODY_FIELD = "editor_body_field"
    const val KEEP_BUTTON = "editor_keep"
    const val DISCARD_BUTTON = "editor_discard"
    const val OVERFLOW_BUTTON = "editor_overflow"
    const val CONVERT_ACTION = "editor_convert"
    const val HIDE_CHECKED_ACTION = "editor_hide_checked"
    const val UNDO_ACTION = "editor_undo"
    const val TAG_CHIPS = "editor_tag_chips"
    const val ADD_TAG = "editor_add_tag"
    fun tagChip(tag: String) = "editor_tag_chip_$tag"
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
    /** The note's tags, parsed from the body (T139). */
    tags: List<String>,
    onRemoveTag: (String) -> Unit,
    onAddTag: () -> Unit,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onKeep: () -> Unit,
    onDiscard: () -> Unit,
    /** The checklist surface's state and callbacks (T023/T024) — see [ChecklistBinding]. */
    checklist: ChecklistBinding,
    textStyle: TextStyle,
    // `modifier` stays the first optional parameter, per Compose convention (and lint's
    // ModifierParameter check) — `expanded` goes after it, not before.
    modifier: Modifier = Modifier,
    /**
     * Full height instead of growing from a few lines — set when an existing note was opened
     * (T025). See [NoteEditorViewModel.expanded].
     */
    expanded: Boolean = false,
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
        // Header: discard hard left, title centred between, keep hard right.
        //
        // The two actions started out adjacent at the right edge, which put a destructive control
        // one thumb-width from the one you press constantly — a mis-tap waiting to happen, and
        // visually ambiguous about which belonged to what. Opposite edges make the pairing
        // obvious (leave / commit, the way a dialog reads) and puts the full width of the title
        // between them, which is as far apart as this row can physically get them.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EditorAction(
                label = DISCARD_GLYPH,
                tag = EditorTestTags.DISCARD_BUTTON,
                // Dimmed: destructive, and should never be the thing your eye lands on first.
                style = dim.copy(fontSize = GLYPH_SIZE),
                onClick = onDiscard,
            )

            BasicTextField(
                value = draft.title,
                onValueChange = onTitleChange,
                // Centred to sit as a header between the two actions. Editable centred text does
                // grow outward from the middle as you type, which is slightly unusual — accepted
                // deliberately, since a title is glanced at far more often than it's edited.
                textStyle = textStyle.copy(textAlign = TextAlign.Center),
                singleLine = true,
                cursorBrush = SolidColor(textStyle.color),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next,
                    // Same reasoning as the shell's input line: a title can be a command-ish
                    // string or a tag, and autocorrect mangling it silently is worse than typos.
                    autoCorrectEnabled = false,
                ),
                decorationBox = { field ->
                    // The placeholder is centred by a wrapping Box rather than by giving it
                    // fillMaxWidth(). A singleLine field measures its inner content with
                    // UNBOUNDED width so it can scroll horizontally, and fillMaxWidth against an
                    // infinite constraint is how you hang the measure pass — which is exactly
                    // what happened here, as an ANR. The Box sizes to its children instead, and
                    // the inner field is the wide one, so the placeholder still lands centred.
                    Box(contentAlignment = Alignment.Center) {
                        // Shown so the blank-title fallback is visible up front rather than a
                        // surprise on save.
                        if (draft.title.isEmpty()) Text(text = "untitled", style = dim)
                        field()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag(EditorTestTags.TITLE_FIELD),
            )

            EditorAction(
                label = KEEP_GLYPH,
                tag = EditorTestTags.KEEP_BUTTON,
                style = textStyle.copy(fontSize = GLYPH_SIZE),
                onClick = onKeep,
            )
        }

        // Prose or checkboxes — the type picks the surface, and the body underneath is the same
        // Markdown either way (T023). A checklist doesn't get the freeform field at all: mixing
        // them would mean two ways to edit the same lines that could disagree mid-keystroke.
        val bodyModifier = Modifier
            .fillMaxWidth()
            // Growing from a couple of lines up to a ceiling is the quick-capture shape;
            // past the ceiling the body scrolls internally rather than pushing the transcript
            // off the top. Opening an existing note skips straight to full height instead —
            // re-reading a journal entry through a six-line window is miserable.
            //
            // `weight(1f)`, not `fillMaxHeight()`: the latter takes the whole column and leaves
            // nothing for the overflow row below, which on-device meant convert and hide-checked
            // simply weren't on screen for any note opened from `:notes`. Weight lets the row
            // claim its height first and gives the body the rest.
            .then(
                if (expanded) Modifier.weight(1f) else Modifier.heightIn(min = 48.dp, max = 420.dp),
            )

        if (draft.type == NoteType.CHECKLIST) {
            ChecklistView(
                items = checklist.items,
                onItemTextChange = checklist.onItemTextChange,
                onItemCheckedChange = checklist.onItemCheckedChange,
                onDeleteItem = checklist.onDeleteItem,
                onMoveItem = checklist.onMoveItem,
                onAddItem = checklist.onAddItem,
                onFinish = checklist.onFinish,
                onCommitItem = checklist.onCommitItem,
                onClearDue = checklist.onClearDue,
                focusIndex = checklist.focusIndex,
                onFocusConsumed = checklist.onFocusConsumed,
                textStyle = textStyle,
                modifier = bodyModifier.padding(top = 8.dp),
            )
        } else {
            BasicTextField(
                value = draft.body,
                onValueChange = onBodyChange,
                textStyle = textStyle,
                cursorBrush = SolidColor(textStyle.color),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = true),
                modifier = bodyModifier
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp)
                    .focusRequester(bodyFocus)
                    .testTag(EditorTestTags.BODY_FIELD),
            )
        }

        // The overflow row (T024, inherited from T022). §4.6 puts it along the bottom next to the
        // tag chips, which is also where T139's chips will land — so this row is their home too,
        // built once rather than added and then rearranged.
        TagChipsRow(
            tags = tags,
            onRemoveTag = onRemoveTag,
            onAddTag = onAddTag,
            textStyle = textStyle,
        )

        OverflowRow(
            isChecklist = draft.type == NoteType.CHECKLIST,
            hideChecked = checklist.hideChecked,
            onToggleType = checklist.onToggleType,
            onToggleHideChecked = checklist.onToggleHideChecked,
            textStyle = dim,
        )
    }
}

/**
 * Everything the checklist surface and its overflow actions need, bundled rather than passed as
 * eight more parameters to [NoteEditorView].
 *
 * Exists because the editor's parameter list was already at the point where adding a feature
 * meant adding six arguments to a call site that has no opinion about any of them — `ShellScreen`
 * builds one of these from the ViewModel and hands it straight through.
 */
data class ChecklistBinding(
    val items: List<ChecklistItem>,
    val hideChecked: Boolean,
    val onItemTextChange: (index: Int, text: String) -> Unit,
    val onItemCheckedChange: (index: Int, checked: Boolean) -> Unit,
    val onDeleteItem: (index: Int) -> Unit,
    val onMoveItem: (from: Int, to: Int) -> Unit,
    val onAddItem: () -> Unit,
    val onCommitItem: (index: Int) -> Unit,
    val onClearDue: (index: Int) -> Unit,
    val onFinish: () -> Unit,
    val focusIndex: Int?,
    val onFocusConsumed: () -> Unit,
    val onToggleType: () -> Unit,
    val onToggleHideChecked: () -> Unit,
)

/**
 * The bottom action row: convert, and — for a checklist — hide/show checked items.
 *
 * **Not a popup menu.** `⋯` opening a floating list would be the conventional Android answer, but
 * this surface is a terminal: everything else in it is a labelled line of text you tap. Two
 * actions also don't earn the indirection of hiding them behind a tap that reveals them, and
 * `DiscardConfirmation` already set the precedent of an inline row over a dialog. If this ever
 * grows past three or four, revisit.
 */
@Composable
private fun OverflowRow(
    isChecklist: Boolean,
    hideChecked: Boolean,
    onToggleType: () -> Unit,
    onToggleHideChecked: () -> Unit,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(EditorTestTags.OVERFLOW_BUTTON),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditorAction(
            // Names the destination, not the operation: "→ checklist" says what you'll get, where
            // "convert" would leave you guessing which direction it goes.
            label = if (isChecklist) "→ note" else "→ checklist",
            tag = EditorTestTags.CONVERT_ACTION,
            style = textStyle,
            onClick = onToggleType,
        )

        if (isChecklist) {
            EditorAction(
                label = if (hideChecked) "show checked" else "hide checked",
                tag = EditorTestTags.HIDE_CHECKED_ACTION,
                style = textStyle,
                onClick = onToggleHideChecked,
            )
        }
    }
}

/**
 * Text, not an icon: this project ships no icon set and the whole surface is monospace.
 *
 * Sized to a real touch target rather than hugging the glyph. A bare `Text` with a few dp of
 * padding is a ~20dp tap area, which is fine to *look* at and unpleasant to *hit* — and one of
 * these deletes your note.
 */
@Composable
internal fun EditorAction(label: String, tag: String, style: TextStyle, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .testTag(tag)
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            // Plain clickable, ripple and all — matching AppListView's rows rather than
            // introducing a second tap treatment for no reason.
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = style)
    }
}

/**
 * Confirmation shown when ✗ would destroy actual writing. Not a dialog — a line above the editor,
 * consistent with everything else in this shell being text.
 *
 * **An inline row rather than a Material `Snackbar`** (design check-in, 2026-08-21). The task
 * called for a snackbar, but there is no `Scaffold`, `Snackbar` or dialog anywhere in this app —
 * it would be the first floating Material chrome over a monospace terminal, and it would look like
 * it came from a different program. `DiscardConfirmation` set the inline precedent before it was
 * retired, and this takes its place in the same slot.
 */
@Composable
fun UndoRow(
    label: String,
    onUndo: () -> Unit,
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
        Text(text = label, style = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
        EditorAction(label = "undo", tag = EditorTestTags.UNDO_ACTION, style = textStyle, onClick = onUndo)
    }
}

/**
 * The tag chips row (T139).
 *
 * **A view over the body's hashtags, not a second place tags live.** Tapping a chip removes that
 * tag from the body; `+ tag` appends a `#` and hands the cursor back to the body so you type the
 * name in the one field that already exists. `NoteDao` still derives tags from the body and still
 * ignores anything passed in, so nothing downstream can tell a chip from typing.
 *
 * Hidden entirely when a note has no tags and you haven't asked to add one — an empty row of
 * nothing is worse than no row on a surface this small.
 */
@Composable
private fun TagChipsRow(
    tags: List<String>,
    onRemoveTag: (String) -> Unit,
    onAddTag: () -> Unit,
    textStyle: TextStyle,
) {
    val dim = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .testTag(EditorTestTags.TAG_CHIPS),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tags.forEach { tag ->
            EditorAction(
                label = "#$tag",
                tag = EditorTestTags.tagChip(tag),
                style = dim,
                onClick = { onRemoveTag(tag) },
            )
        }
        EditorAction(label = "+ tag", tag = EditorTestTags.ADD_TAG, style = dim, onClick = onAddTag)
    }
}
