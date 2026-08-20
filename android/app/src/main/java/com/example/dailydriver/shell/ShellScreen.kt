package com.example.dailydriver.shell

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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.dailydriver.alias.formatAliasListing
import com.example.dailydriver.alias.parseAliasDefinition
import com.example.dailydriver.apps.AppEntry
import com.example.dailydriver.apps.filteredBy
import com.example.dailydriver.apps.matchPreview
import com.example.dailydriver.apps.soleAutoLaunchTarget
import com.example.dailydriver.apps.topMatch
import com.example.dailydriver.command.ReservedCommand
import com.example.dailydriver.editor.ChecklistBinding
import com.example.dailydriver.editor.DiscardConfirmation
import com.example.dailydriver.editor.NoteEditorView
import com.example.dailydriver.editor.NoteEditorViewModel
import com.example.dailydriver.notes.Note
import com.example.dailydriver.notes.NotesRepository
import com.example.dailydriver.notes.soleAutoOpenTarget
import com.example.dailydriver.notifications.NotificationRecord
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
internal object ShellTestTags {
    const val TRANSCRIPT_LIST = "shell_transcript_list"
    const val INPUT_FIELD = "shell_input_field"
    const val APP_LIST = "shell_app_list"
    const val NOTIF_LIST = "shell_notif_list"
    const val NOTE_LIST = "shell_note_list"
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
    onSubmit: () -> Unit,
    reservedCommand: ReservedCommand?,
    apps: List<AppEntry>,
    onAppClick: (AppEntry) -> Unit,
    onSettingsCommand: () -> Unit,
    aliases: Map<String, String>,
    onAliasListRequested: (listing: String) -> Unit,
    onAliasExpanded: (expansion: String) -> Unit,
    onDefineAlias: suspend (input: String, name: String, command: String) -> Unit,
    notifications: List<NotificationRecord>,
    hasNotificationAccess: Boolean,
    onNotificationClick: (NotificationRecord) -> Unit,
    onNotifAccessSettings: () -> Unit,
    /** A lambda, not a Boolean — see [StatusLine], which polls it on its own tick. */
    hasUnreadNotifications: () -> Boolean,
    editor: NoteEditorViewModel,
    /** Clears the `:note ...` text off the prompt once the editor has taken over. */
    onNoteEditorOpened: () -> Unit,
    /** Live results for a `:notes` query — see [NotesRepository.observe]. */
    observeNotes: (query: String) -> Flow<List<Note>>,
    /**
     * Bumped by [MainActivity][com.example.dailydriver.MainActivity] whenever the window regains
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
    val appsCommand = reservedCommand as? ReservedCommand.Apps
    val notifCommand = (reservedCommand as? ReservedCommand.Notif)?.takeIf { hasNotificationAccess }
    val notesCommand = reservedCommand as? ReservedCommand.Notes

    // Re-subscribed whenever the query text changes, so the list narrows as you type — the same
    // live-filter behaviour :apps has, except the filtering happens in SQL rather than in memory
    // because notes aren't all resident the way the app index is.
    val noteResults by remember(notesCommand?.query) {
        notesCommand?.let { observeNotes(it.query) } ?: flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    // Narrowed to exactly one note — Enter or a typing pause opens it, the same affordance (and
    // the same blank-query guard) :apps has when its filter reaches a single app.
    val noteAutoOpenTarget = notesCommand?.let { noteResults.soleAutoOpenTarget(it.query) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
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
    LaunchedEffect(editor.isOpen, focusRegainedSignal) {
        if (!editor.isOpen) focusRequester.requestFocus()
    }

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
    // Sole-match only — :apps browse mode's own auto-launch is unchanged by the T1 revision below.
    val autoLaunchTarget = remember(filteredApps, appsCommand) {
        appsCommand?.let { filteredApps.soleAutoLaunchTarget(it.query) }
    }

    // T1 only applies to bare prompt input — not while a reserved command is active, not to an
    // alias definition, and not to an alias name (which expands rather than fuzzy-matching).
    val isPlainQuery = reservedCommand == null && parseAliasDefinition(input) == null &&
        !aliases.containsKey(input.trim())

    // The best T1 match, if any — what a plain-prompt Enter or pause commits to. No ambiguity
    // gate: the best match always wins, even among near-ties (":apps" keeps its own stricter
    // sole-match-only rule above, untouched by this).
    val plainMatchTarget = remember(apps, input, isPlainQuery) {
        if (isPlainQuery) apps.topMatch(input) else null
    }

    // Shown dim above the prompt so you can see what Enter will do before committing to it.
    // An alias previews its expansion for the same reason a fuzzy match previews its target.
    val previewText = remember(apps, aliases, input, isPlainQuery, reservedCommand) {
        when {
            reservedCommand != null || parseAliasDefinition(input) != null -> null
            aliases.containsKey(input.trim()) -> aliases[input.trim()]
            else -> apps.matchPreview(input)
        }
    }

    // Auto-launch after a pause in typing — either :apps' sole match, or T1's best guess at the
    // bare prompt. Keyed on `input` itself, not just the resolved target: "chr", "chro" and
    // "chrom" can all resolve to the same app, so keying on the target alone would leave the
    // original timer running and fire mid-word. Re-keying on every keystroke cancels and
    // restarts it, which is what "a one second pause" actually means.
    val currentAutoLaunchTarget = autoLaunchTarget ?: plainMatchTarget
    LaunchedEffect(currentAutoLaunchTarget, input) {
        val target = currentAutoLaunchTarget ?: return@LaunchedEffect
        delay(AUTO_LAUNCH_DELAY_MS)
        onAppClick(target)
    }

    // The same pause-to-act behaviour for a sole note result. Kept as its own effect rather than
    // folded into the one above: that one resolves to an AppEntry and ends in onAppClick, and
    // unifying them would mean a nullable-of-either just to share four lines.
    //
    // Keyed on the note's id, not the Note itself — an autosave elsewhere changes updatedAt and
    // would otherwise restart the timer for what is still the same note.
    LaunchedEffect(noteAutoOpenTarget?.id, input) {
        val target = noteAutoOpenTarget ?: return@LaunchedEffect
        delay(AUTO_LAUNCH_DELAY_MS)
        editor.openExisting(target)
        onNoteEditorOpened()
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
                    onNoteEditorOpened()
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

        val draft = editor.draft
        if (draft != null) {
            // The editor takes the prompt's place rather than floating over it: while writing a
            // note there is no command to type, and §4.6's "returns to the prompt with focus
            // restored" only makes sense if the prompt was genuinely out of play.
            if (editor.confirmingDiscard) {
                DiscardConfirmation(
                    onConfirm = editor::confirmDiscard,
                    onCancel = editor::cancelDiscard,
                    textStyle = monospace,
                )
            }
            NoteEditorView(
                draft = draft,
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
                val aliasAttempt = parseAliasDefinition(input)
                val aliasExpansion = aliases[input.trim()]
                when {
                    // Enter opens a uniquely-matched app immediately rather than waiting out the
                    // pause.
                    autoLaunchTarget != null -> onAppClick(autoLaunchTarget)
                    // Same idea for :notes — Enter opens the sole remaining note rather than
                    // waiting out the pause. Sits beside the :apps case because they're the same
                    // affordance, not near the other reserved-command branches.
                    noteAutoOpenTarget != null -> {
                        editor.openExisting(noteAutoOpenTarget)
                        onNoteEditorOpened()
                    }
                    // :settings (T142) has no browse mode to select from like :apps does — Enter
                    // is what fires it. Checked ahead of the alias branches below only because
                    // it's the more specific state; the two never actually overlap (alias names
                    // can't start with `:`).
                    reservedCommand is ReservedCommand.Settings -> onSettingsCommand()
                    // :alias (T144) has no browse mode either — it just lists what's defined.
                    // Formatted here, not in the ViewModel, since ShellScreen is what already
                    // holds the live aliases map.
                    reservedCommand is ReservedCommand.Alias ->
                        onAliasListRequested(aliases.formatAliasListing())
                    // :notif (T147) without access has nothing to browse — Enter deep-links
                    // straight to Settings -> Notification access, no explanation screen first
                    // (design check-in, 2026-08-19). With access, notifCommand is non-null and
                    // the browse view above is already showing; Enter has nothing to commit to
                    // (tap-only, like :apps has no keyboard-driven selection beyond a sole
                    // match), so it falls through to the `else` branch below same as Apps' own
                    // no-sole-match case does.
                    reservedCommand is ReservedCommand.Notif && !hasNotificationAccess ->
                        onNotifAccessSettings()
                    // :note (T143 registered it, T020 finally gives it something to open). The
                    // query becomes the initial title, so `:note standup` starts a named note and
                    // bare `:note` starts an untitled one that gets a date-time stamp on save.
                    // Opening an EXISTING note by name is T025's job, not this.
                    reservedCommand is ReservedCommand.Note -> {
                        editor.open(reservedCommand.query)
                        onNoteEditorOpened()
                    }
                    // Checked before the definition branch only for readability — the two shapes
                    // are disjoint, since an alias name can't contain the spaces or "=" that a
                    // definition requires.
                    aliasExpansion != null -> onAliasExpanded(aliasExpansion)
                    // Captured before launching: onDefineAlias awaits a Room write, and by the
                    // time it returns the live `input` may already be something else the user
                    // has since typed.
                    aliasAttempt != null -> {
                        val submittedInput = input
                        coroutineScope.launch {
                            onDefineAlias(submittedInput, aliasAttempt.name, aliasAttempt.command)
                        }
                    }
                    // T1 (design doc §4.2). Enter commits to the same best guess the preview
                    // line and the pause-timer above already agree on — the best match wins even
                    // among near-ties, per explicit request.
                    plainMatchTarget != null -> onAppClick(plainMatchTarget)

                    // Nothing matched at any tier — falls through to the normal submit, which
                    // no-ops in apps mode rather than recording ":apps ca" as a command.
                    else -> onSubmit()
                }
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
