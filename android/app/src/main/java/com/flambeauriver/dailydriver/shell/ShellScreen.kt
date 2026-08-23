package com.flambeauriver.dailydriver.shell

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.flambeauriver.dailydriver.alias.expandAlias
import com.flambeauriver.dailydriver.alias.formatAliasListing
import com.flambeauriver.dailydriver.apps.AppEntry
import com.flambeauriver.dailydriver.apps.filteredBy
import com.flambeauriver.dailydriver.apps.displayNames
import com.flambeauriver.dailydriver.apps.matchPreview
import com.flambeauriver.dailydriver.apps.soleAutoLaunchTarget
import com.flambeauriver.dailydriver.apps.topMatch
import com.flambeauriver.dailydriver.command.CommandResolution
import com.flambeauriver.dailydriver.command.classifyResolution
import com.flambeauriver.dailydriver.command.reservedCommandToken
import com.flambeauriver.dailydriver.command.ReservedCommand
import com.flambeauriver.dailydriver.editor.ChecklistBinding
import com.flambeauriver.dailydriver.editor.UndoRow
import com.flambeauriver.dailydriver.editor.NoteEditorView
import com.flambeauriver.dailydriver.editor.NoteEditorViewModel
import com.flambeauriver.dailydriver.notes.Note
import com.flambeauriver.dailydriver.notes.TaskListItem
import com.flambeauriver.dailydriver.notes.NotesRepository
import com.flambeauriver.dailydriver.notes.soleAutoOpenTarget
import com.flambeauriver.dailydriver.notifications.NotificationRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** The prompt glyph, shown on the input line and on every recorded command in the scrollback. */
private const val PROMPT = "❯"

/** How long typing must be idle before a uniquely-matched app opens on its own. */
private const val AUTO_LAUNCH_DELAY_MS = 1_000L

/**
 * Stable anchors for Compose UI tests (androidx.compose.ui.test), per the "semantics/test tags
 * rather than brittle text matching" convention in android/CLAUDE.md. Nothing consumes these yet
 * — they exist so the first UI test doesn't also have to invent them.
 */
/**
 * How long an undo offer stands (T138).
 *
 * Longer than a Material snackbar's default four seconds: this replaces a confirmation dialog, so
 * the window has to be long enough to notice a mistake you did not expect to have made, and
 * nothing is competing for the space.
 */
private const val UNDO_TIMEOUT_MS = 8_000L

internal object ShellTestTags {
    const val TRANSCRIPT_LIST = "shell_transcript_list"
    const val INPUT_FIELD = "shell_input_field"
    const val APP_LIST = "shell_app_list"
    const val NOTIF_LIST = "shell_notif_list"
    const val NOTE_LIST = "shell_note_list"
    const val TASK_LIST = "shell_task_list"

    /** Per-row, since `:tasks` has two tap targets on a row and a test needs to reach each. */
    fun taskCheckbox(noteId: String, position: Int) = "shell_task_checkbox_${noteId}_$position"
}

/**
 * The launcher surface: scrollback transcript above, input line pinned at the bottom — or, while
 * [reservedCommand] is the `:apps` command, the browse list in place of the transcript. Same
 * input line either way; it drives the live filter in apps mode instead of submitting commands.
 *
 * Stateless — all state is hoisted to [ShellViewModel], per the convention in android/CLAUDE.md.
 */
@Composable
fun ShellScreen(
    transcript: Transcript,
    input: String,
    onInputChange: (String) -> Unit,
    /** Runs whatever is at the prompt. Dispatch lives in [ShellViewModel] since T178. */
    onSubmit: () -> Unit,
    /**
     * Acts on typed input *without* Enter — the one-second pause, or a tapped row.
     *
     * Routed through the ViewModel rather than performed here so that it goes through the same
     * dispatch and the same T014 logging. Before T178 each pause path did its own logging, and one
     * of them — opening a sole note — did none.
     */
    onResolved: (input: String) -> Unit,
    /** Clears the prompt when the UI takes over without a command running (a tapped row). */
    onHandedOff: () -> Unit,
    reservedCommand: ReservedCommand?,
    apps: List<AppEntry>,
    /** Opens an app from a tapped row. Enter and the pause go through [onResolved] instead. */
    onAppClick: (AppEntry) -> Unit,
    aliases: Map<String, String>,
    notifications: List<NotificationRecord>,
    hasNotificationAccess: Boolean,
    onNotificationClick: (NotificationRecord) -> Unit,
    /** A lambda, not a Boolean — see [StatusLine], which polls it on its own tick. */
    hasUnreadNotifications: () -> Boolean,
    editor: NoteEditorViewModel,
    /** Live results for a `:notes` query — see [NotesRepository.observe]. */
    observeNotes: (query: String) -> Flow<List<Note>>,
    /** Outstanding tasks — an indexed query over the derived index (T156/T157). */
    observeTasks: () -> Flow<List<TaskListItem>>,
    /** Completed tasks, for `:tasksdone`. */
    observeCompletedTasks: () -> Flow<List<TaskListItem>>,
    /** Ticks or unticks a task by rewriting the note body it lives in (T156). */
    onToggleTask: suspend (TaskListItem) -> Unit,
    /** Resolves the note a task lives in, so tapping the text can open it. */
    onOpenTaskNote: suspend (TaskListItem) -> Note?,
    /**
     * Bumped by [MainActivity][com.flambeauriver.dailydriver.MainActivity] whenever the window regains
     * focus — HOME chief among the ways that happens (T015, design doc §4.1: "HOME re-focuses
     * the input"). An `Int` rather than a one-shot event/callback so the existing focus
     * [LaunchedEffect] below can simply add it as a second key, the same way [editor]'s own state
     * already re-triggers that effect.
     *
     * Deliberately has no default: `modifier` must stay the *first optional* parameter (Compose
     * convention, and lint's `ModifierParameter` check), and there's exactly one call site, which
     * always has a real value to pass.
     */
    focusRegainedSignal: Int,
    modifier: Modifier = Modifier,
) {
    // Apps has a browse-mode UI, and so does Notif — but only once access is granted; without it
    // there's nothing to browse, so it falls back to the transcript like :settings/:alias, and
    // Enter deep-links to the one place this permission can be granted instead (see the submit
    // handler below). :settings (T142) fires-and-forgets on Enter; :alias (T144) is the same
    // shape — ShellScreen already holds the live aliases map, so it formats the listing here and
    // hands the result to the ViewModel rather than the ViewModel resolving it itself. :note
    // (T143) needs no ShellScreen change at all: with no browse mode and no external data to
    // reach for, it falls straight through the `else -> onSubmit()` branch below like any other
    // unmatched-tier input, and ShellViewModel decides what that means. :help (T145) will add its
    // own behavior as it lands.
    // One pure derivation of everything this line of input implies (T179). Keyed on the app
    // index as well as the input, since installing or removing an app changes the answer.
    val appDisplayNames = remember(apps) { displayNames(apps) }
    val ui = remember(input, apps, aliases, appDisplayNames) {
        shellUiState(input, apps, aliases, appDisplayNames)
    }
    val appsCommand = ui.appsCommand
    val notifCommand = ui.notifCommand(hasNotificationAccess)
    val notesCommand = ui.notesCommand
    val tasksCommand = ui.tasksCommand
    val tasksDoneCommand = ui.tasksDoneCommand

    // Re-subscribed whenever the query text changes, so the list narrows as you type — the same
    // live-filter behaviour :apps has, except the filtering happens in SQL rather than in memory
    // because notes aren't all resident the way the app index is.
    val noteResults by remember(notesCommand?.query) {
        notesCommand?.let { observeNotes(it.query) } ?: flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    // Indexed queries, not scans (ADR-0007) — the whole reason the derived `tasks` table exists.
    val taskResults by remember(tasksCommand, tasksDoneCommand) {
        when {
            tasksCommand != null -> observeTasks()
            tasksDoneCommand != null -> observeCompletedTasks()
            else -> flowOf(emptyList())
        }
    }.collectAsState(initial = emptyList())

    // Narrowed to exactly one note — Enter or a typing pause opens it, the same affordance (and
    // the same blank-query guard) :apps has when its filter reaches a single app.
    val noteAutoOpenTarget = notesCommand?.let { noteResults.soleAutoOpenTarget(it.query) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val monospace = LocalTextStyle.current.copy(
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onBackground,
    )

    // IME open by default (design doc §4.1) — focusing the field is what raises it.
    // Keyed on whether the editor is up, not Unit. §4.6 requires that dismissing the editor
    // "returns to the prompt with focus restored to the input line and the IME open" — with a
    // Unit key this fires once at startup and never again, so closing the editor left the prompt
    // unfocused and the keyboard down. Caught on-device; a build can't see it.
    //
    // Also keyed on focusRegainedSignal (T015): the identical bug, general form — returning to
    // the launcher window (HOME chief among the ways that happens) can leave the prompt
    // unfocused and the keyboard down the same way closing the editor did. Guarded the same way:
    // skipped while the editor is open, which leaves its own field to manage its own focus.
    // **Focus alone is not enough, which is what T165 fixes.** `requestFocus()` is a no-op when the
    // field never lost focus — and that is exactly what unlocking the phone looks like: the window
    // regains focus with the input still focused, nothing changes, and no one ever asks for the
    // keyboard. So the prompt sat there focused with the IME down, and every command started with
    // a tap on a launcher whose entire premise is that you type.
    //
    // Showing the IME explicitly is unconditional for the same reason: it must not depend on
    // whether focus actually moved.
    LaunchedEffect(editor.isOpen, focusRegainedSignal) {
        if (editor.isOpen) return@LaunchedEffect
        focusRequester.requestFocus()
        keyboard?.show()
    }

    // Keep the newest entry visible as the transcript grows.
    LaunchedEffect(transcript.entries.size) {
        if (transcript.entries.isNotEmpty()) {
            listState.scrollToItem(transcript.entries.lastIndex)
        }
    }

    val filteredApps = ui.filteredApps
    val autoLaunchTarget = ui.autoLaunchTarget
    val plainMatchTarget = ui.plainMatchTarget
    val previewText = ui.previewText
    val pendingAliasExpansion = ui.pendingAliasExpansion

    val currentAutoLaunchTarget = ui.currentAutoLaunchTarget

    // Acting on a pause rather than on Enter, for the three things that resolve without one. All
    // of them hand the submitted line back to the ViewModel, so they go through the same dispatch
    // and the same T014 logging as Enter (T178). Before that, each did its own logging and the
    // note case did none at all — losing exactly the examples where the timer beat the user.
    //
    // **On the pause rather than instantly**, which was asked for and would not work: expanding
    // `a` the moment it is typed puts the caret after `:apps ` and makes `alarm` unreachable by
    // fuzzy match. That is the trap that made `:notes` untypeable at T140.
    //
    // Keyed on `input` itself, not just the resolved target: "chr", "chro" and "chrom" all
    // resolve to the same app, so keying on the target alone would leave the first timer running
    // and fire mid-word. Re-keying on every keystroke is what "a one second pause" means.
    LaunchedEffect(pendingAliasExpansion, input) {
        if (pendingAliasExpansion == null) return@LaunchedEffect
        delay(AUTO_LAUNCH_DELAY_MS)
        onResolved(input)
    }

    LaunchedEffect(currentAutoLaunchTarget, input, pendingAliasExpansion) {
        // Yields to the alias timer above rather than racing it: an exact alias is T0 and a fuzzy
        // app match is T1, so the cascade's order holds here exactly as it does on Enter.
        if (pendingAliasExpansion != null) return@LaunchedEffect
        if (currentAutoLaunchTarget == null) return@LaunchedEffect
        delay(AUTO_LAUNCH_DELAY_MS)
        onResolved(input)
    }

    // The same pause-to-act behaviour for a sole note result. Keyed on the note's id, not the
    // Note itself — an autosave elsewhere changes updatedAt and would otherwise restart the timer
    // for what is still the same note.
    LaunchedEffect(noteAutoOpenTarget?.id, input) {
        if (noteAutoOpenTarget == null) return@LaunchedEffect
        delay(AUTO_LAUNCH_DELAY_MS)
        onResolved(input)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 12.dp),
    ) {
        // Above everything and outside the browse-mode branch below: it's a status line, so it
        // stays put while :apps/:notif replace the transcript (design check-in, 2026-08-19).
        // Its own state ticks internally — this scope doesn't recompose with it.
        StatusLine(hasUnreadNotifications = hasUnreadNotifications, textStyle = monospace)

        if (appsCommand != null) {
            AppListView(
                apps = filteredApps,
                names = appDisplayNames,
                onAppClick = onAppClick,
                textStyle = monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        } else if (notesCommand != null) {
            NoteListView(
                notes = noteResults,
                onNoteClick = { note ->
                    editor.openExisting(note)
                    onHandedOff()
                },
                textStyle = monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        } else if (tasksCommand != null || tasksDoneCommand != null) {
            TaskListView(
                tasks = taskResults,
                onToggle = { task ->
                    coroutineScope.launch { onToggleTask(task) }
                },
                onOpenNote = { task ->
                    coroutineScope.launch {
                        onOpenTaskNote(task)?.let { note ->
                            editor.openExisting(note)
                            onHandedOff()
                        }
                    }
                },
                textStyle = monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        } else if (notifCommand != null) {
            NotifListView(
                notifications = notifications,
                onNotificationClick = onNotificationClick,
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

        // Rendered whether or not the editor is open (T138): discarding a note closes it, so an
        // undo offer that lived inside the editor would be one you could never reach.
        editor.pendingUndo?.let { undo ->
            // Auto-dismissed rather than left standing. Keyed on the action so a second
            // destructive action restarts the clock instead of inheriting the first one's.
            LaunchedEffect(undo) {
                delay(UNDO_TIMEOUT_MS)
                editor.dismissUndo()
            }
            UndoRow(label = undo.label, onUndo = editor::undo, textStyle = monospace)
        }

        val draft = editor.draft
        if (draft != null) {
            // The editor takes the prompt's place rather than floating over it: while writing a
            // note there is no command to type, and §4.6's "returns to the prompt with focus
            // restored" only makes sense if the prompt was genuinely out of play.
            NoteEditorView(
                draft = draft,
                tags = editor.tags,
                onRemoveTag = editor::removeTag,
                onAddTag = editor::startAddingTag,
                tagEntry = editor.tagEntry,
                onTagEntryChange = editor::onTagEntryChange,
                onCommitTag = editor::commitTag,
                onTitleChange = editor::onTitleChange,
                onBodyChange = editor::onBodyChange,
                onKeep = editor::keepAndClose,
                onDiscard = editor::requestDiscard,
                checklist = ChecklistBinding(
                    items = editor.visibleItems,
                    hideChecked = editor.hideChecked,
                    onItemTextChange = editor::onItemTextChange,
                    onItemCheckedChange = editor::onItemCheckedChange,
                    onDeleteItem = editor::deleteItem,
                    onMoveItem = editor::moveItem,
                    onAddItem = editor::addItem,
                onCommitItem = { editor.commitItem(it) },
                onClearDue = editor::clearDue,
                onFinish = { editor.keepAndClose() },
                focusIndex = editor.pendingItemFocus,
                onFocusConsumed = editor::consumeItemFocus,
                    onToggleType = editor::toggleType,
                    onToggleHideChecked = editor::toggleHideChecked,
                ),
                textStyle = monospace,
                expanded = editor.expanded,
                modifier = if (editor.expanded) Modifier.weight(1f) else Modifier,
            )
            // BACK keeps rather than discards. It's the ambiguous gesture — people use it to mean
            // "go back", not "throw this away" — and autosave has already written the text, so
            // treating it as ✓ is both the safe reading and the truthful one. (T015 owns BACK
            // generally; handled here because an overlay with no back-out is a trap, and BACK is
            // otherwise a deliberate no-op at the prompt.)
            BackHandler(enabled = true, onBack = editor::keepAndClose)
        } else {
            if (previewText != null) {
                Text(
                    text = "→ $previewText",
                    style = monospace.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            InputLine(
            value = input,
            onValueChange = onInputChange,
            onSubmit = {
                // Priority order is arbitrary in practice — apps-mode input (":apps ...") and
                // alias definitions ("alias ... = ...") have disjoint shapes, so at most one of
                // these branches can ever apply to a given input.
                // Enter just submits (T178). Everything this used to decide — which command,
                // whether a sole match exists, whether and how to log it — is now one dispatch in
                // ShellViewModel, reachable from a unit test for the first time.
                onSubmit()
            },
                textStyle = monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .focusRequester(focusRequester),
            )
        }
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
            // TextFieldValue, not the plain-String overload, and the cursor is forced to the end
            // on every recomposition rather than left for Compose to infer. This is load-bearing
            // for ShellViewModel's auto-inserted space after a completed command token: with the
            // plain-String overload, a programmatic change (the text growing without a matching
            // user keystroke) has no explicit cursor to carry forward, and the well-known failure
            // mode is the cursor staying put right after the original text instead of jumping past
            // the inserted character — so the next keystroke lands BEFORE the space, not after it
            // (":appsc " instead of ":apps c"). There's no supported use case here for repositioning
            // the cursor mid-string (single-line command entry, typed left to right), so always
            // snapping to the end on every value change, from whatever source, is a fair trade.
            value = TextFieldValue(text = value, selection = TextRange(value.length)),
            onValueChange = { onValueChange(it.text) },
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
