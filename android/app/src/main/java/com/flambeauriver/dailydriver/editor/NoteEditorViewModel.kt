package com.flambeauriver.dailydriver.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.flambeauriver.dailydriver.notes.ChecklistItem
import com.flambeauriver.dailydriver.notes.Note
import com.flambeauriver.dailydriver.notes.NoteType
import com.flambeauriver.dailydriver.notes.NotesRepository
import com.flambeauriver.dailydriver.notes.moved
import com.flambeauriver.dailydriver.notes.normalized
import com.flambeauriver.dailydriver.notes.parseTags
import com.flambeauriver.dailydriver.notes.withTag
import com.flambeauriver.dailydriver.notes.withoutTag
import com.flambeauriver.dailydriver.notes.parseChecklist
import com.flambeauriver.dailydriver.notes.splitTagLines
import com.flambeauriver.dailydriver.notes.renderChecklist
import com.flambeauriver.dailydriver.notes.toChecklistBody
import com.flambeauriver.dailydriver.notes.toNoteBody
import com.flambeauriver.dailydriver.notes.cleaned
import com.flambeauriver.dailydriver.notes.resolveDueInText
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/** How long typing must pause before the draft is written. */
private const val AUTOSAVE_DEBOUNCE_MS = 500L

/**
 * Owns the note editor's draft and its persistence (T020/T021).
 *
 * **Separate from `ShellViewModel` on purpose.** The shell's state is the transcript and one input
 * line; this carries a draft, a debounce job, and a database. Folding them together would put an
 * editor's lifecycle inside the thing the editor is drawn over, and every future editor feature
 * would grow the shell.
 *
 * **Autosave is the real save** (§4.6). The ✓/✗ buttons choose whether to *keep* the note, not
 * whether to commit it — by the time you reach for either, the text is already durable.
 */
class NoteEditorViewModel(
    private val notes: NotesRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    var draft by mutableStateOf<NoteDraft?>(null)
        private set

    /** True while the editor is on screen — [ShellScreen] renders against this. */
    val isOpen: Boolean get() = draft != null

    /**
     * The last destructive action, and how to put it back (T138).
     *
     * **This replaces the ✗ confirmation entirely.** That prompt existed only because discard had
     * no way back; asking first is what you do when you cannot undo, and doing both is friction
     * charged twice. Now that every destructive action is reversible, ✗ just acts.
     *
     * Survives the editor closing on purpose. Discarding a note closes the editor, so an undo
     * offer that died with it would be an undo you can never reach — the shell renders this
     * whether or not a draft is open.
     */
    var pendingUndo by mutableStateOf<UndoableAction?>(null)
        private set

    /** Drops the undo offer without acting on it. Called when it times out or is superseded. */
    fun dismissUndo() {
        pendingUndo = null
    }

    /** Puts back whatever [pendingUndo] describes, and clears the offer. */
    fun undo() {
        val action = pendingUndo ?: return
        pendingUndo = null
        action.restore()
    }

    private fun offerUndo(label: String, restore: () -> Unit) {
        pendingUndo = UndoableAction(label = label, restore = restore)
    }

    private var autosaveJob: Job? = null

    /**
     * Opens a fresh draft. [initialTitle] carries `:note standup`'s argument; blank for bare
     * `:note`, in which case a stamp is substituted at save time (see [NoteDraft.resolvedTitle]).
     *
     * No database row is created here. An accidental `:note` that's closed immediately leaves
     * nothing behind.
     */
    /**
     * True when the editor should take the full available height instead of growing from a few
     * lines (design check-in, 2026-08-20).
     *
     * Set when an *existing* note is opened: that's a read/edit action on something already
     * written, so the small starting height — sized for beginning a quick note — fights you
     * immediately. New notes still grow from small, which is the quick-capture feel T020 exists
     * for.
     */
    var expanded by mutableStateOf(false)
        private set

    /**
     * Opens a draft for `:note`. With no [initialTitle] this is a new blank note, created
     * synchronously so the editor appears on the same frame.
     *
     * With a name, it **opens that note if one already exists, otherwise creates it** (T025) —
     * so `:note standup` twice gets you back to the same note rather than a second one wearing
     * the same name. That lookup is a database read, hence the coroutine; the editor appears a
     * frame later in that case only.
     */
    fun open(initialTitle: String = "") {
        val name = initialTitle.trim()

        hideChecked = false

        if (name.isEmpty()) {
            draft = NoteDraft(id = newId(), title = "", createdAt = now())
            expanded = false
            return
        }

        viewModelScope.launch {
            val existing = notes.byTitle(name)
            if (existing != null) openExisting(existing) else {
                draft = NoteDraft(id = newId(), title = name, createdAt = now())
                expanded = false
            }
        }
    }

    /**
     * Opens a note chosen from the `:notes` list (T025).
     *
     * `persisted = true` matters: the row already exists, so discarding must soft-delete it
     * rather than silently forgetting a draft that was never written.
     */
    fun openExisting(note: Note) {
        draft = NoteDraft(
            id = note.id,
            title = note.title,
            body = note.body,
            createdAt = note.createdAt,
            rev = note.rev,
            persisted = true,
            type = note.type,
        )
        expanded = true
        hideChecked = false
    }

    fun onTitleChange(value: String) = edit { it.copy(title = value) }

    fun onBodyChange(value: String) = edit { it.copy(body = value) }

    // --- checklist (T023) -------------------------------------------------------------------------

    /**
     * The draft's checklist items, parsed from the body every time rather than cached beside it.
     *
     * That's the whole design: §6.2 permits one source of truth, so there is no item list to fall
     * out of step with the text. Re-parsing per read is a handful of string operations on a
     * grocery list — small enough that buying consistency with it is obviously the right trade.
     */
    val items: List<ChecklistItem> get() = draft?.let { checklistOf(it.body) }.orEmpty()

    /**
     * Items as *displayed*: [items], minus the checked ones while [hideChecked] is on (T024).
     *
     * Indices into this list are not indices into [items] — every mutation below takes a
     * displayed index and resolves it through here, so hiding checked items can't silently make
     * a tap land on the wrong row.
     */
    val visibleItems: List<ChecklistItem> get() = items.visible(hideChecked)

    /**
     * Hide-checked-items (T024). **Transient by design** (check-in, 2026-08-20): it resets when
     * the note closes. Persisting it would mean a new column, schema v2, and a change to §6.2's
     * document model that the C# side has to match — a lot of moving parts for a display toggle,
     * and one whose absence you'd notice within a day of daily-driving if it turned out to matter.
     */
    var hideChecked by mutableStateOf(false)
        private set

    fun toggleHideChecked() {
        hideChecked = !hideChecked
    }

    /**
     * The displayed row the editor should put the cursor in, or null when it shouldn't move focus.
     *
     * **Owned here rather than in [ChecklistView].** The view used to guess the new row's index
     * itself, which worked only because `addItem` happens to append; anything that reordered on
     * insert would have focused the wrong row, silently. The ViewModel is the only thing that
     * knows where an item actually landed, so it says so. Consumed once, via [consumeItemFocus],
     * so it can't steal focus back on a later recomposition.
     */
    var pendingItemFocus by mutableStateOf<Int?>(null)
        private set

    fun consumeItemFocus() {
        pendingItemFocus = null
    }

    /** Appends an empty item and asks the editor to put the cursor in it. */
    fun addItem() {
        editItems { it + ChecklistItem("") }
        pendingItemFocus = visibleItems.lastIndex
    }

    fun onItemTextChange(displayedIndex: Int, text: String) =
        editItems { current -> current.replacingAt(displayedIndex) { it.copy(text = text) } }

    /**
     * Turns any date shortcut typed into a row into a real due date (T154).
     *
     * **Called when a row loses focus, never while typing into it.** Typing `@tomorrow` is the
     * whole input mechanism — the row is already a text field, so a date needs no picker — but
     * rewriting text under a live cursor is what T023 got wrong, and this field is a
     * `BasicTextField(String)` whose cursor snaps to the end on any external change. Resolving at
     * commit means the text only ever changes while nobody is typing into it.
     *
     * A no-op when nothing resolves, so an item carrying `@bob` is never rewritten and the body
     * isn't dirtied by merely tabbing through a row.
     */
    fun commitItem(displayedIndex: Int, today: LocalDate = LocalDate.now()) = editItems { current ->
        current.replacingAt(displayedIndex) { item ->
            val resolved = resolveDueInText(item.text, today)
            if (resolved == item.text) item else item.copy(text = resolved)
        }
    }

    /**
     * Clears a row's due date, demoting the task back to an ordinary checklist item.
     *
     * Tapping the date is the only way to remove one, because the token is lifted out of the text
     * on parse and so can't be backspaced away in the field. Setting a *different* date needs no
     * separate affordance: type another `@...` and last-wins takes it.
     */
    fun clearDue(displayedIndex: Int) =
        editItems { current -> current.replacingAt(displayedIndex) { it.copy(due = null) } }

    /**
     * Ticking re-applies the unchecked-above-checked invariant, so the item drops below the
     * unchecked ones (§4.6, Keep's behaviour) rather than staying put.
     */
    fun onItemCheckedChange(displayedIndex: Int, checked: Boolean) = editItems { current ->
        current.replacingAt(displayedIndex) { it.copy(checked = checked) }.normalized()
    }

    /** Deleting a row offers it back (T138) — the body before the delete is the whole snapshot. */
    fun deleteItem(displayedIndex: Int) {
        val before = draft?.body
        val removed = visibleItems.getOrNull(displayedIndex)?.text?.ifBlank { "item" }
        deleteItemInternal(displayedIndex)
        if (before != null && removed != null) {
            offerUndo("deleted \"$removed\"") { edit { it.copy(body = before) } }
        }
    }

    private fun deleteItemInternal(displayedIndex: Int) = editItems { current ->
        val actual = current.actualIndexOf(displayedIndex) ?: return@editItems current
        current.filterIndexed { index, _ -> index != actual }
    }

    /**
     * Commits a finished drag. Both indices are into the *displayed* list; they're resolved to
     * real ones before the move, then the invariant is re-applied — so dragging an unchecked item
     * into the checked group returns it to the bottom of the unchecked ones rather than leaving
     * a list that ticking a box could never have produced.
     */
    fun moveItem(fromDisplayed: Int, toDisplayed: Int) = editItems { current ->
        val from = current.actualIndexOf(fromDisplayed) ?: return@editItems current
        val to = current.actualIndexOf(toDisplayed) ?: return@editItems current
        current.moved(from, to).normalized()
    }

    /** Maps an index in [visibleItems] back to one in [items] — see `ChecklistDisplay.kt`. */
    private fun List<ChecklistItem>.actualIndexOf(displayedIndex: Int): Int? =
        actualIndex(displayedIndex, hideChecked)

    private fun List<ChecklistItem>.replacingAt(
        displayedIndex: Int,
        transform: (ChecklistItem) -> ChecklistItem,
    ): List<ChecklistItem> {
        val actual = actualIndexOf(displayedIndex) ?: return this
        return mapIndexed { index, item -> if (index == actual) transform(item) else item }
    }

    /**
     * Every item mutation goes through the body, which is what keeps the body authoritative.
     *
     * **Tag lines are lifted off first and put back after** (T169). `parseChecklist` reads any
     * non-blank line as an item, so the `#shopping` that `withTag` appends became a checkbox you
     * could tick. Splitting here rather than teaching the parser to skip them is deliberate twice
     * over: the parser is a cross-language contract pinned by `fixtures/checklist-parsing.json`,
     * and — more immediately — this line is `render(transform(parse(body)))`, so a line the parser
     * dropped is a line the render would never write back. The first tag added would vanish on the
     * next keystroke.
     */
    /**
     * Rewrites a body's checklist content while leaving its tag lines alone (T169/T177).
     *
     * **Every read or write of a checklist body goes through here or [checklistOf].** `withTag`
     * appends a tag on its own line and `parseChecklist` reads any non-blank line as an item, so a
     * body handed straight to the parser turns `#shopping` into a checkbox you can tick. The split
     * used to be applied by hand at four call sites, which is one "remember to do this" away from
     * the bug coming back — and "remember to apply it everywhere" is exactly how T169 happened.
     *
     * **Deliberately not solved inside `parseChecklist`.** It is a cross-language contract pinned
     * by `fixtures/checklist-parsing.json`, and — more immediately — this is
     * `render(transform(parse(body)))`, so a line the parser dropped is a line the render would
     * never write back. Teaching it to skip tag lines would delete the first tag you added.
     */
    private inline fun editBody(body: String, transform: (content: String) -> String): String {
        val parts = splitTagLines(body)
        return parts.rejoin(transform(parts.content))
    }

    /** The checklist items in a body, with tag lines excluded. See [editBody]. */
    private fun checklistOf(body: String): List<ChecklistItem> =
        parseChecklist(splitTagLines(body).content)

    private fun editItems(transform: (List<ChecklistItem>) -> List<ChecklistItem>) =
        edit { it.copy(body = editBody(it.body) { content -> renderChecklist(transform(parseChecklist(content))) }) }

    // --- tags (T139) --------------------------------------------------------------------------------

    /** The note's tags, parsed from the body — the same set `NoteDao` derives on save. */
    val tags: List<String> get() = draft?.let { parseTags(it.body) }.orEmpty()

    /**
     * Removes a tag by editing the body (T139).
     *
     * The chip is a view over the body's hashtags, so removing one is a text edit and everything
     * downstream carries on unchanged. Offered back like any other destructive action.
     */
    fun removeTag(tag: String) {
        val before = draft?.body ?: return
        edit { it.copy(body = withoutTag(it.body, tag)) }
        offerUndo("removed #$tag") { edit { it.copy(body = before) } }
    }

    /**
     * The tag being typed on a checklist, or null when no tag entry is open (T169).
     *
     * Only ever non-null in checklist mode — see [startAddingTag].
     */
    var tagEntry: String? by mutableStateOf(null)
        private set

    /**
     * `+ tag` — on a prose note, appends a bare `#` to the body and hands the cursor back to it
     * (T139). **On a checklist, opens a one-line tag input instead** (T169).
     *
     * T139 chose the append-a-`#` gesture precisely so tags had no second text field, and that
     * reasoning still holds for prose: the body is right there, and typing `#work` into it is the
     * same gesture that has always made a tag.
     *
     * **A checklist has no body field for the cursor to go back to.** Its body renders as rows, so
     * the bare `#` landed as a checkbox you could tick, and typing the name into it produced
     * `- [ ] #shopping` — an item that looks like a tag and can never become one. That was the
     * reported bug, and no amount of tag-line splitting reaches it: the text never has the shape of
     * a tag line in the first place.
     *
     * **This is still not a second writer of the tag set.** The input is transient and its only
     * action is [commitTag], which calls the same `withTag` the chips already use. Everything is
     * stored in the body and derived from it, exactly as before.
     */
    fun startAddingTag() {
        val current = draft ?: return
        if (current.type == NoteType.CHECKLIST) {
            tagEntry = ""
        } else {
            edit { it.copy(body = if (it.body.isEmpty()) "#" else it.body.trimEnd() + "\n#") }
        }
    }

    fun onTagEntryChange(value: String) {
        if (tagEntry != null) tagEntry = value
    }

    /**
     * Commits whatever is in the tag entry and closes it.
     *
     * A blank entry just closes, so tapping `+ tag` and changing your mind costs nothing. `withTag`
     * does the rest: it already refuses a name with no letters in it, and is idempotent, so
     * committing a tag the note already carries is a no-op rather than a duplicate chip.
     */
    fun commitTag() {
        val typed = tagEntry ?: return
        tagEntry = null
        if (typed.isBlank()) return
        edit { it.copy(body = withTag(it.body, typed)) }
    }

    /** Abandons the tag entry without writing anything. */
    fun cancelTag() {
        tagEntry = null
    }

    // --- conversion (T024) ------------------------------------------------------------------------

    /**
     * Flips between prose and checklist, reformatting the body to match (§4.6).
     *
     * Turning hide-checked off on the way out of a checklist matters: it's the only place the
     * toggle is reachable, so leaving it on would strand a prose note whose next conversion back
     * would open with items already hidden and no obvious reason why.
     */
    fun toggleType() {
        val current = draft ?: return
        // Snapshotted before the reformat. Converting a checklist to prose drops every tick, which
        // is the one conversion that genuinely loses information, so both directions are offered
        // back rather than trusting "just convert it again" to be lossless.
        val beforeBody = current.body
        val beforeType = current.type
        offerUndo(if (beforeType == NoteType.NOTE) "converted to checklist" else "converted to note") {
            edit { it.copy(body = beforeBody, type = beforeType) }
        }
        val becomingChecklist = current.type == NoteType.NOTE
        edit {
            // Tags survive the conversion untouched (T169) — they are a property of the note, not
            // of whether it currently renders as prose or as checkboxes.
            it.copy(
                type = if (becomingChecklist) NoteType.CHECKLIST else NoteType.NOTE,
                body = editBody(it.body) { content ->
                    if (becomingChecklist) toChecklistBody(content) else toNoteBody(content)
                },
            )
        }
        if (!becomingChecklist) {
            hideChecked = false
            pendingItemFocus = null
            return
        }

        // Converting *to* a checklist should leave you typing rather than hunting for `+ item`
        // (UI tweak, 2026-08-21). A blank note converts to an empty list, so it gets its first row
        // created here; a note with prose already has rows, and gets the cursor at the end of the
        // last one instead of a spurious blank appended to real content.
        if (visibleItems.isEmpty()) addItem() else pendingItemFocus = rowToFocusAfterConversion(visibleItems.size)
    }

    private fun edit(transform: (NoteDraft) -> NoteDraft) {
        val current = draft ?: return
        draft = transform(current)
        scheduleAutosave()
    }

    /**
     * Restarts the debounce on every keystroke, so a burst of typing writes once at the end
     * rather than once per character. The write itself is idempotent — same row, same id.
     */
    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DEBOUNCE_MS)
            persist()
        }
    }

    /**
     * Writes the draft immediately, bypassing the debounce. Called when the editor closes and
     * when the Activity is paused — §4.6's promise is that "a killed process, a phone call, or a
     * swipe-away loses nothing", and a pending 500ms debounce would break exactly that.
     */
    fun flush() {
        autosaveJob?.cancel()
        viewModelScope.launch { persist() }
    }

    private suspend fun persist() {
        val current = draft ?: return
        if (!current.hasContent()) return
        val timestamp = now()
        notes.save(
            current.toNote(notes.deviceName, timestamp, notes.zone(), notes.is24Hour()),
            timestamp,
        )
        // Marks the row as existing so a later discard knows to tombstone rather than just
        // forget. Guarded against the draft having been closed while this write was in flight.
        if (draft?.id == current.id) draft = draft?.copy(persisted = true)
    }

    /** ✓ — keep the note and leave. A blank draft is silently dropped rather than stored empty. */
    fun keepAndClose() {
        // Checklist rows are tidied here rather than as you type: an item has to be allowed to
        // sit empty (and to end in the space you just typed) while you're filling it in, but
        // storing either would leave a blank checkbox, or stray whitespace, in the list forever.
        tidyChecklistForStorage()
        flush()
        draft = null
        hideChecked = false
    }

    private fun tidyChecklistForStorage() {
        val current = draft ?: return
        if (current.type != NoteType.CHECKLIST) return
        draft = current.copy(
            body = editBody(current.body) { content ->
                val kept = parseChecklist(content)
                    .map { it.copy(text = resolveDueInText(it.text, LocalDate.now())) }
                    .cleaned()
                renderChecklist(kept)
            },
        )
    }

    /**
     * ✗ — discard.
     *
     * **No longer asks first** (T138). The confirmation existed only because discard had no way
     * back: asking first is what you do when you cannot undo, and doing both charges the same
     * friction twice. A note with content is soft-deleted and offered back through [pendingUndo];
     * an empty one is dropped silently, since there is nothing to restore and an undo offer for
     * nothing is noise.
     */
    fun requestDiscard() {
        val current = draft ?: return
        if (current.hasContent()) confirmDiscard() else close()
    }

    /**
     * Soft-deletes if a row was ever written, then closes. The `persisted` check matters: a draft
     * that never reached the database has nothing to tombstone, and asking Room to delete an
     * unknown id would be a silent no-op that reads like it worked.
     */
    fun confirmDiscard() {
        val current = draft ?: return
        autosaveJob?.cancel()
        if (current.persisted) {
            viewModelScope.launch { notes.discard(current.id, now()) }
        }
        close()

        // Restoring re-saves the draft as it stood, which un-tombstones the row: `save` writes
        // `deleted = false` and queues the outbox entry that propagates the resurrection. Holding
        // the draft rather than the id is what makes an unpersisted note recoverable too.
        offerUndo("discarded \"${current.resolvedTitle(notes.zone(), notes.is24Hour())}\"") {
            val restoredAt = now()
            viewModelScope.launch {
                notes.save(
                    current.toNote(notes.deviceName, restoredAt, notes.zone(), notes.is24Hour()),
                    restoredAt,
                )
            }
        }
    }

    private fun close() {
        autosaveJob?.cancel()
        draft = null
        hideChecked = false
    }

    companion object {
        /**
         * This ViewModel needs a repository, which needs a `Context`, so it can't use the no-arg
         * `viewModel()` the shell does.
         */
        fun factory(notes: NotesRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                NoteEditorViewModel(notes) as T
        }
    }
}
