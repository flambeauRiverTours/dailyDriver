package com.flambeauriver.dailydriver.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flambeauriver.dailydriver.notes.Note
import com.flambeauriver.dailydriver.notes.noteSnippet

/**
 * The `:notes` browse-and-search view (T025/T026) — same bottom-anchored, tap-to-act shape as
 * [AppListView] and [NotifListView], and filtered live by whatever follows the command, exactly
 * as `:apps` filters.
 *
 * **Two lines per row, unlike the other lists.** Blank titles default to a `Thu 20 Aug - 12:24 PM`
 * stamp (T021), which is the common case for journal entries — a title-only list of those is a
 * list of timestamps you'd have to open one by one to identify. The snippet is what makes them
 * distinguishable, so it earns the extra line.
 */
@Composable
fun NoteListView(
    notes: List<Note>,
    onNoteClick: (Note) -> Unit,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    // Reversed for the same reason AppListView reverses: the newest note should be the one
    // closest to the prompt, where your thumb already is.
    val rows = notes.reversed()

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .testTag(ShellTestTags.NOTE_LIST),
        verticalArrangement = Arrangement.Bottom,
    ) {
        items(rows, key = { it.id }) { note ->
            NoteRow(note = note, onClick = { onNoteClick(note) }, textStyle = textStyle)
        }
    }
}

@Composable
private fun NoteRow(note: Note, onClick: () -> Unit, textStyle: TextStyle) {
    val snippet = noteSnippet(note.body)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Text(text = note.title, style = textStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        // Dropped entirely rather than rendered blank, so a title-only note doesn't leave a gap
        // that reads like a rendering fault.
        if (snippet.isNotEmpty()) {
            Text(
                text = snippet,
                style = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
