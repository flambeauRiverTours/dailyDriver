package com.example.dailydriver.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.dailydriver.notes.ChecklistItem
import com.example.dailydriver.notes.Note
import com.example.dailydriver.notes.NoteType
import com.example.dailydriver.notes.NotesRepository
import com.example.dailydriver.notes.moved
import com.example.dailydriver.notes.normalized
import com.example.dailydriver.notes.parseChecklist
import com.example.dailydriver.notes.renderChecklist
import com.example.dailydriver.notes.toChecklistBody
import com.example.dailydriver.notes.toNoteBody
import com.example.dailydriver.notes.cleaned
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
     * Set when ✗ is pressed on a note that has something in it. §4.6 assigns delete to the
     * overflow menu, but the design check-in gave ✗ a genuinely destructive meaning, and T138's
     * undo snackbar doesn't exist yet — so the one case that could actually lose writing asks
     * first. Remove this once T138 lands and discard becomes reversible from the UI.
     */
    var confirmingDiscard by mutableStateOf(false)
        private set

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
        confirmingDiscard = false

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
        confirmingDiscard = false
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
    val items: List<ChecklistItem> get() = draft?.let { parseChecklist(it.body) }.orEmpty()

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

    /** Appends an empty item and leaves it for the UI to focus. */
    fun addItem() = editItems { it + ChecklistItem("") }

    fun onItemTextChange(displayedIndex: Int, text: String) =
        editItems { current -> current.replacingAt(displayedIndex) { it.copy(text = text) } }

    /**
     * Ticking re-applies the unchecked-above-checked invariant, so the item drops below the
     * unchecked ones (§4.6, Keep's behaviour) rather than staying put.
     */
    fun onItemCheckedChange(displayedIndex: Int, checked: Boolean) = editItems { current ->
        current.replacingAt(displayedIndex) { it.copy(checked = checked) }.normalized()
    }

    fun deleteItem(displayedIndex: Int) = editItems { current ->
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

    /** Every item mutation goes through the body, which is what keeps the body authoritative. */
    private fun editItems(transform: (List<ChecklistItem>) -> List<ChecklistItem>) =
        edit { it.copy(body = renderChecklist(transform(parseChecklist(it.body)))) }

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
        val becomingChecklist = current.type == NoteType.NOTE
        edit {
            it.copy(
                type = if (becomingChecklist) NoteType.CHECKLIST else NoteType.NOTE,
                body = if (becomingChecklist) toChecklistBody(it.body) else toNoteBody(it.body),
            )
        }
        if (!becomingChecklist) hideChecked = false
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
        dropEmptyItems()
        flush()
        draft = null
        confirmingDiscard = false
        hideChecked = false
    }

    private fun dropEmptyItems() {
        val current = draft ?: return
        if (current.type != NoteType.CHECKLIST) return
        val kept = parseChecklist(current.body).cleaned()
        draft = current.copy(body = renderChecklist(kept))
    }

    /**
     * ✗ — discard. Silent when there's nothing to lose; otherwise raises [confirmingDiscard]
     * first, because until T138 there's no way back.
     */
    fun requestDiscard() {
        val current = draft ?: return
        if (current.hasContent()) confirmingDiscard = true else close()
    }

    fun cancelDiscard() {
        confirmingDiscard = false
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
    }

    private fun close() {
        autosaveJob?.cancel()
        draft = null
        confirmingDiscard = false
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
