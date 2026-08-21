package com.example.dailydriver.notes

import android.content.Context
import android.os.Build
import android.text.format.DateFormat
import com.example.dailydriver.data.DatabaseProvider
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

/**
 * Context-owning glue over [NoteDao], in the same mould as `AliasRepository` — no separable pure
 * logic beyond what `TagParsing.kt` and `NoteDraft.kt` already cover on their own, so not
 * unit-tested directly. The transactional behaviour underneath it is covered by `NoteDaoTest`.
 *
 * Exists as of T020 because there is finally a caller; T018 deliberately shipped the DAO without
 * one rather than guess at an API before the editor existed to shape it.
 */
class NotesRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dao = DatabaseProvider.get(context).noteDao()
    private val tasks = DatabaseProvider.get(context).taskDao()

    /**
     * Formatting inputs for a blank-title stamp (`NoteDraft.resolvedTitle`). They live here
     * because this is already the layer that holds a `Context`, which is the only way to reach
     * the system 12/24-hour setting — the alternative was handing the ViewModel a set of supplier
     * lambdas, which is a lot of machinery to read one boolean.
     *
     * Read per call, not cached, so flipping the system setting takes effect on the next save.
     */
    fun is24Hour(): Boolean = DateFormat.is24HourFormat(appContext)

    fun zone(): ZoneId = ZoneId.systemDefault()

    /**
     * Identifies which replica authored a change. §7.2 puts this in the title of a conflict copy
     * (`<title> (conflict, <device>, <timestamp>)`), so it wants to be human-readable rather than
     * an opaque install id — you have to recognise which of your own devices it names.
     */
    val deviceName: String = Build.MODEL ?: "android"

    suspend fun save(note: Note, now: Long) = dao.upsertLocal(note, queuedAt = now)

    /**
     * Soft-deletes, per §7.1 — the row survives as a tombstone so the deletion propagates to the
     * other replica rather than the note simply reappearing on next sync. Also why the editor's
     * discard is recoverable in principle, which is what T138's undo will surface.
     */
    suspend fun discard(id: String, now: Long) =
        dao.markDeletedLocal(id, updatedAt = now, queuedAt = now)

    suspend fun byId(id: String): Note? = dao.byId(id)

    suspend fun byTitle(title: String): Note? = dao.byTitle(title.trim())

    /**
     * The live note list for a `:notes` query (T025/T026).
     *
     * The only place [escapeLike] is applied, which is why `observeMatching`'s escaping
     * requirement can't be forgotten at a call site — there is exactly one call site.
     */
    fun observe(query: NoteQuery): Flow<List<Note>> = when (query) {
        is NoteQuery.All -> dao.observeAll()
        is NoteQuery.Tag -> dao.observeByTag(query.tag)
        is NoteQuery.Text -> dao.observeMatching(escapeLike(query.term))
    }

    // --- tasks (T155/T156/T157) -------------------------------------------------------------------

    /** Outstanding tasks, soonest first — `:tasks`. An indexed query, not a scan (ADR-0007). */
    fun observeTasks(): Flow<List<TaskListItem>> = tasks.observeOutstanding()

    /** Completed tasks, most recent first — `:tasksdone`. */
    fun observeCompletedTasks(): Flow<List<TaskListItem>> = tasks.observeCompleted()

    /**
     * Adds a task (T155).
     *
     * Appends one dated line to a note titled [TASKS_NOTE_TITLE], creating it as a checklist if it
     * doesn't exist. That reuses T025's open-or-create-by-title path, so there is no new storage
     * code and no second way a task can come into being: `:task` and typing a date onto a checklist
     * row produce byte-identical lines.
     *
     * **An undated `:task` is due today** (design check-in, 2026-08-21). `:task` means you intend
     * to do the thing, and a task the list never shows would read as the command silently failing.
     */
    suspend fun addTask(text: String, due: LocalDate, now: Long, newId: () -> String): Note {
        val existing = byTitle(TASKS_NOTE_TITLE)
        val item = ChecklistItem(text = text.trim(), due = due)

        val note = if (existing == null) {
            Note(
                id = newId(),
                type = NoteType.CHECKLIST,
                title = TASKS_NOTE_TITLE,
                body = renderChecklist(listOf(item)),
                createdAt = now,
                updatedAt = now,
                sourceDevice = deviceName,
            )
        } else {
            existing.copy(
                type = NoteType.CHECKLIST,
                body = renderChecklist(parseChecklist(existing.body) + item),
                updatedAt = now,
            )
        }

        save(note, now)
        return note
    }

    /**
     * Ticks or unticks a task from the list view (T156).
     *
     * **Writes the note body, not the index.** The body is where the item is authored, so the row
     * in `tasks` is refreshed as a consequence of that write rather than being updated alongside
     * it — updating both would be the two-sources-of-truth problem ADR-0007 exists to avoid.
     *
     * Re-applies the unchecked-above-checked invariant, so ticking from `:tasks` reorders the
     * source checklist exactly as ticking inside the editor would. [position] is resolved before
     * that reorder, since it indexes the list as stored.
     */
    suspend fun setTaskChecked(noteId: String, position: Int, checked: Boolean, now: Long) {
        val note = byId(noteId) ?: return
        val items = parseChecklist(note.body)
        if (position !in items.indices) return

        val updated = items
            .mapIndexed { index, item -> if (index == position) item.copy(checked = checked) else item }
            .normalized()

        save(note.copy(body = renderChecklist(updated), updatedAt = now), now)
    }

    /**
     * Rebuilds the whole task index from note bodies (T157).
     *
     * The repair path ADR-0007 requires: the index is derived, so a wrong one is fixed by
     * recomputing it rather than by patching rows. Also what fills it after the 1→2 migration,
     * which deliberately creates the table empty rather than running Kotlin inside a migration.
     */
    suspend fun rebuildTaskIndex() = dao.rebuildAllTasks()

    companion object {
        /** The note `:task` appends to. Opened or created by title, exactly as `:note <name>` does. */
        const val TASKS_NOTE_TITLE = "tasks"
    }
}
