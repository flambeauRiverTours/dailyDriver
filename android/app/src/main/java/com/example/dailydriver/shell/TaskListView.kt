package com.example.dailydriver.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.dailydriver.notes.TaskListItem
import com.example.dailydriver.notes.dueLabel
import java.time.LocalDate

/**
 * The `:tasks` and `:tasksdone` views (T156) — the same bottom-anchored, tap-to-act shape as
 * [NoteListView], [AppListView] and [NotifListView].
 *
 * **Two tap targets per row, unlike the other lists** (design check-in, 2026-08-21). The checkbox
 * ticks the task; the text opens the note it lives in. That mirrors the checklist editor's own row,
 * where the two are already separate, and it keeps the common action — ticking something off — at
 * one tap while leaving the surrounding context reachable.
 *
 * **Ticking writes the note body**, not the index row. The body is where a task is authored
 * (ADR-0007), so the index refreshes as a consequence of that write rather than beside it.
 */
@Composable
fun TaskListView(
    tasks: List<TaskListItem>,
    onToggle: (TaskListItem) -> Unit,
    onOpenNote: (TaskListItem) -> Unit,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    // Reversed like every other list here: the row nearest the prompt is the one your thumb is
    // already on. The query hands them over soonest-first, so reversing puts the most urgent
    // task closest to hand.
    val rows = tasks.reversed()

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .testTag(ShellTestTags.TASK_LIST),
        verticalArrangement = Arrangement.Bottom,
    ) {
        items(rows, key = { "${it.noteId}:${it.position}" }) { task ->
            TaskRowView(
                task = task,
                today = today,
                onToggle = { onToggle(task) },
                onOpenNote = { onOpenNote(task) },
                textStyle = textStyle,
            )
        }
    }
}

@Composable
private fun TaskRowView(
    task: TaskListItem,
    today: LocalDate,
    onToggle: () -> Unit,
    onOpenNote: () -> Unit,
    textStyle: TextStyle,
) {
    val dim = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val label = dueLabel(LocalDate.parse(task.due), today)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clickable(onClick = onToggle)
                .testTag(ShellTestTags.taskCheckbox(task.noteId, task.position)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = if (task.checked) "[x]" else "[ ]", style = if (task.checked) dim else textStyle)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpenNote)
                .padding(vertical = 8.dp),
        ) {
            Text(
                text = task.text,
                style = if (task.checked) {
                    dim.copy(textDecoration = TextDecoration.LineThrough)
                } else {
                    textStyle
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Which note this came from. A task list that can't say where a task lives is hard to
            // act on, and it's the only hint that tapping the text goes somewhere.
            Text(text = task.noteTitle, style = dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Text(
            text = label.text,
            style = if (label.overdue && !task.checked) {
                dim.copy(color = MaterialTheme.colorScheme.error)
            } else {
                dim
            },
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}
