package com.flambeauriver.dailydriver.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
    // Newest first from the query, so item 0 is the newest and BottomAnchoredList puts it
    // nearest the prompt. Passed in natural order — see that file on why not to reverse here.
    BottomAnchoredList(
        rows = notes,
        key = { it.id },
        testTag = ShellTestTags.NOTE_LIST,
        modifier = modifier,
    ) { note ->
        NoteRow(note = note, onClick = { onNoteClick(note) }, textStyle = textStyle)
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
