package com.flambeauriver.dailydriver.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.flambeauriver.dailydriver.notes.ChecklistItem
import com.flambeauriver.dailydriver.notes.dueLabel
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Checkbox glyphs, in the same spirit as the header's `×`/`✓` — text, not icons, because this
 * project ships no icon set and the whole surface is monospace.
 *
 * `[ ]` and `[x]` are the Markdown they represent, which makes the rendered list and the stored
 * body legible as the same thing. A drawn checkbox would look tidier and would also be the first
 * place in this app where what you see stops resembling what's on disk.
 */
private const val UNCHECKED = "[ ]"
private const val CHECKED = "[x]"

/**
 * The drag handle.
 *
 * **A dedicated handle, not long-press-anywhere-on-the-row** — which is what this was first built
 * as, and it does not work. The row is mostly a `BasicTextField`, and a text field claims the
 * long-press for its own selection handles before a parent gesture detector ever sees it: on
 * device, long-pressing an item raised the text cursor instead of picking the row up. A handle
 * outside the field sidesteps the contest entirely rather than trying to win it.
 *
 * `≡` over the conventional `⠿`: braille-pattern glyphs aren't reliably present in a monospace
 * font, and a missing-glyph box is a worse handle than a slightly less conventional one.
 */
private const val DRAG_HANDLE = "≡"

internal object ChecklistTestTags {
    const val LIST = "checklist_list"
    const val ADD = "checklist_add"
    fun item(index: Int) = "checklist_item_$index"
    fun checkbox(index: Int) = "checklist_checkbox_$index"
    fun delete(index: Int) = "checklist_delete_$index"
    fun due(index: Int) = "checklist_due_$index"
    fun handle(index: Int) = "checklist_handle_$index"
}

/**
 * The checkbox-list surface (T023) — what a [com.flambeauriver.dailydriver.notes.NoteType.CHECKLIST]
 * draft shows in place of the freeform body field.
 *
 * Every row is an editable text field, so the list is typed into rather than assembled through a
 * dialog. Each row carries its own delete, and a `≡` handle it can be dragged by.
 *
 * **Indices here are into the *displayed* list**, which is not the stored one while hide-checked
 * is on — the ViewModel resolves them, so nothing in this file needs to know that.
 */
@Composable
fun ChecklistView(
    items: List<ChecklistItem>,
    onItemTextChange: (index: Int, text: String) -> Unit,
    onItemCheckedChange: (index: Int, checked: Boolean) -> Unit,
    onDeleteItem: (index: Int) -> Unit,
    onMoveItem: (from: Int, to: Int) -> Unit,
    onAddItem: () -> Unit,
    /** Enter on the trailing empty row finishes the note (T027). */
    onFinish: () -> Unit,
    onCommitItem: (index: Int) -> Unit,
    onClearDue: (index: Int) -> Unit,
    /** The row to put the cursor in, decided by the ViewModel. Null when focus shouldn't move. */
    focusIndex: Int?,
    onFocusConsumed: () -> Unit,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    val dim = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)

    // Which row is being dragged, and how far it's travelled. Held here rather than per-row so a
    // row can tell whether *it* is the one moving without every row owning drag state.
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    // Measured from a real row rather than assumed from a dp constant. The first version hardcoded
    // 40.dp against rows that actually lay out around 52.dp, so a drag committed roughly a third
    // fewer positions than the finger had travelled — the kind of "nearly right" that reads as the
    // list having a mind of its own. Starts at 1f purely to keep the first division safe; no drag
    // can be in flight before a row has been measured.
    var rowHeightPx by remember { mutableFloatStateOf(1f) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .testTag(ChecklistTestTags.LIST),
    ) {
        items.forEachIndexed { index, item ->
            ChecklistRow(
                item = item,
                index = index,
                isDragging = index == draggingIndex,
                dragOffset = if (index == draggingIndex) dragOffset else 0f,
                onTextChange = { onItemTextChange(index, it) },
                onCheckedChange = { onItemCheckedChange(index, it) },
                onDelete = { onDeleteItem(index) },
                onDragStart = {
                    draggingIndex = index
                    dragOffset = 0f
                },
                onDrag = { dragOffset += it },
                onDragEnd = {
                    // Rounding rather than truncating: a drag past the halfway point of the next
                    // row reads as wanting that row's place, which is where the finger is.
                    val moved = (dragOffset / rowHeightPx).roundToInt()
                    val target = (index + moved).coerceIn(0, items.lastIndex)
                    if (target != index) onMoveItem(index, target)
                    draggingIndex = -1
                    dragOffset = 0f
                },
                // Enter adds the next row, except on a trailing empty one, where it finishes
                // the note instead (T027). That is the idiom every checklist app uses, and on a
                // soft keyboard it is the only spare key there is: an empty last row is the
                // universal "I'm done" signal, and adding yet another blank row in response is
                // the one thing it cannot mean.
                onEnter = {
                    if (enterFinishesNote(item.text, index, items.lastIndex)) onFinish() else onAddItem()
                },
                onCommit = { onCommitItem(index) },
                onClearDue = { onClearDue(index) },
                today = today,
                onMeasured = { height -> rowHeightPx = height.toFloat() },
                autoFocus = index == focusIndex,
                onFocusClaimed = onFocusConsumed,
                textStyle = textStyle,
                dimStyle = dim,
            )
        }

        // `+ item` rather than a floating button — same reasoning as the header actions being
        // text. Sized to a real touch target for the same reason they are.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 48.dp)
                .clickable(onClick = onAddItem)
                .testTag(ChecklistTestTags.ADD),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(text = "+ item", style = dim)
        }
    }
}

@Composable
private fun ChecklistRow(
    item: ChecklistItem,
    index: Int,
    isDragging: Boolean,
    dragOffset: Float,
    onTextChange: (String) -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onEnter: () -> Unit,
    onCommit: () -> Unit,
    onClearDue: () -> Unit,
    today: LocalDate,
    onMeasured: (Int) -> Unit,
    autoFocus: Boolean,
    onFocusClaimed: () -> Unit,
    textStyle: TextStyle,
    dimStyle: TextStyle,
) {
    val focusRequester = rememberAutoFocus(autoFocus, onFocusClaimed)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { onMeasured(it.height) }
            // The dragged row floats above the rest and follows the finger. The others don't
            // animate out of its way — with a handful of items the gap that opens on drop reads
            // clearly enough, and animating them would mean tracking a projected order that the
            // drop then has to agree with.
            .graphicsLayer {
                translationY = dragOffset
                if (isDragging) {
                    shadowElevation = 8f
                    alpha = 0.9f
                }
            }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DragHandle(index, onDragStart, onDrag, onDragEnd, dimStyle)

        EditorAction(
            label = if (item.checked) CHECKED else UNCHECKED,
            tag = ChecklistTestTags.checkbox(index),
            style = if (item.checked) dimStyle else textStyle,
            onClick = { onCheckedChange(!item.checked) },
        )

        ItemTextField(
            item = item,
            index = index,
            focusRequester = focusRequester,
            onTextChange = onTextChange,
            onEnter = onEnter,
            onCommit = onCommit,
            textStyle = textStyle,
            dimStyle = dimStyle,
            modifier = Modifier.weight(1f),
        )

        item.due?.let { DueChip(it, index, today, onClearDue, dimStyle) }

        EditorAction(
            label = "×",
            tag = ChecklistTestTags.delete(index),
            style = dimStyle,
            onClick = onDelete,
        )
    }
}

/**
 * A [FocusRequester] that claims the cursor once, when [autoFocus] turns true.
 *
 * A newly added row takes the cursor, so `+ item` and Enter both leave you typing rather than
 * aiming. Guarded on [autoFocus] so it fires for the new row only — every other row would
 * otherwise grab focus back on its first composition.
 *
 * **`withFrameNanos` first, and it is load-bearing.** On a row composed this same frame the
 * `focusRequester` modifier node has not attached yet, and requesting focus against it logs
 * "FocusRequester is not initialized" and does nothing. It fails *quietly*: the row appears, the
 * keyboard does not, and the next thing you type goes nowhere. Waiting a frame lets the node
 * attach. Found on-device — the request is issued either way, so only looking at the screen shows
 * the difference.
 */
@Composable
private fun rememberAutoFocus(autoFocus: Boolean, onFocusClaimed: () -> Unit): FocusRequester {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            withFrameNanos { }
            focusRequester.requestFocus()
            onFocusClaimed()
        }
    }
    return focusRequester
}

/**
 * The drag affordance.
 *
 * **⚠️ The gesture lives on this handle, not on the row, and that is not stylistic.** A row is
 * mostly a `BasicTextField`, and a text field claims long-press for its own selection — so a
 * drag gesture attached to the row never fired at all. See [DRAG_HANDLE].
 *
 * A plain drag rather than a long-press one, now that nothing competes for the pointer: the handle
 * exists only to be dragged, so making you hold it first would be a wait for nothing.
 */
@Composable
private fun DragHandle(
    index: Int,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    style: TextStyle,
) {
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 40.dp, minHeight = 48.dp)
            .testTag(ChecklistTestTags.handle(index))
            .pointerInput(index) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, amount ->
                        change.consume()
                        onDrag(amount.y)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = DRAG_HANDLE, style = style)
    }
}

/** The row's editable text. Owns [hadFocus] because that is what the commit rule is about. */
@Composable
private fun ItemTextField(
    item: ChecklistItem,
    index: Int,
    focusRequester: FocusRequester,
    onTextChange: (String) -> Unit,
    onEnter: () -> Unit,
    onCommit: () -> Unit,
    textStyle: TextStyle,
    dimStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    // Tracks whether this row *had* focus, so commit fires on a real focus loss rather than on the
    // initial `false` every row reports when it first composes. Without it, every row would commit
    // once on appearing and dirty the body for nothing.
    var hadFocus by remember { mutableStateOf(false) }

    BasicTextField(
        value = item.text,
        onValueChange = onTextChange,
        // Struck through and dimmed when checked — the row stays readable (you want to see what
        // you already got) while being obviously done.
        textStyle = if (item.checked) {
            dimStyle.copy(textDecoration = TextDecoration.LineThrough)
        } else {
            textStyle
        },
        singleLine = true,
        cursorBrush = SolidColor(textStyle.color),
        // Enter adds the next item, which is what makes a list typeable in one go without reaching
        // for `+ item` between every line.
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = true),
        keyboardActions = KeyboardActions(onNext = { onEnter() }),
        modifier = modifier
            .focusRequester(focusRequester)
            // Date shortcuts resolve here, on the way *out* of the row. Doing it on every keystroke
            // would rewrite text under a live cursor, which is the T023 bug, and this field's
            // cursor snaps to the end on any external change.
            .onFocusChanged { if (!it.isFocused && hadFocus) onCommit() }
            .onFocusChanged { hadFocus = it.isFocused }
            .testTag(ChecklistTestTags.item(index)),
    )
}

/**
 * The due date, between the text and the delete, tappable to clear.
 *
 * **Tapping is the only way to remove a date**: the token is lifted out of the text on parse, so it
 * cannot be backspaced away in the field. Changing one needs no affordance — type another `@...`
 * and last-wins takes it.
 */
@Composable
private fun DueChip(
    due: LocalDate,
    index: Int,
    today: LocalDate,
    onClear: () -> Unit,
    dimStyle: TextStyle,
) {
    val label = dueLabel(due, today)
    Box(
        modifier = Modifier
            .sizeIn(minHeight = 48.dp)
            .clickable(onClick = onClear)
            .padding(horizontal = 4.dp)
            .testTag(ChecklistTestTags.due(index)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.text,
            // Overdue is coloured, but the date is still shown either way — colour is never the
            // only signal, and "how late" is the thing you actually need to judge.
            style = if (label.overdue) {
                dimStyle.copy(color = MaterialTheme.colorScheme.error)
            } else {
                dimStyle
            },
            maxLines = 1,
        )
    }
}
