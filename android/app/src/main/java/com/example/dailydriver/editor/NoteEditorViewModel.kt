package com.example.dailydriver.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.dailydriver.notes.NotesRepository
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
    fun open(initialTitle: String = "") {
        draft = NoteDraft(id = newId(), title = initialTitle.trim(), createdAt = now())
        confirmingDiscard = false
    }

    fun onTitleChange(value: String) = edit { it.copy(title = value) }

    fun onBodyChange(value: String) = edit { it.copy(body = value) }

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
        flush()
        draft = null
        confirmingDiscard = false
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
