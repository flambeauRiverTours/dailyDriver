package com.example.dailydriver.notes

import android.content.Context
import android.os.Build
import android.text.format.DateFormat
import com.example.dailydriver.data.DatabaseProvider
import kotlinx.coroutines.flow.Flow
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
}
