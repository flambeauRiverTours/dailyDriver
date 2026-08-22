package com.flambeauriver.dailydriver.notes

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flambeauriver.dailydriver.data.DailyDriverDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The derived task index against real SQLite (T157).
 *
 * ADR-0007 marks these as required rather than optional. A stale index means `:tasks` lies, and
 * that is invisible to unit tests and to the compiler in exactly the way T018's `INSERT OR REPLACE`
 * cascade was — which shipped green through 410 unit tests and lint, and failed on the first
 * instrumented run.
 */
@RunWith(AndroidJUnit4::class)
class TaskIndexDaoTest {

    private lateinit var db: DailyDriverDatabase
    private lateinit var notes: NoteDao
    private lateinit var tasks: TaskDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DailyDriverDatabase::class.java,
        ).build()
        notes = db.noteDao()
        tasks = db.taskDao()
    }

    @After
    fun tearDown() = db.close()

    private fun note(id: String, body: String, title: String = "tasks") = Note(
        id = id,
        type = NoteType.CHECKLIST,
        title = title,
        body = body,
        createdAt = 1,
        updatedAt = 1,
        sourceDevice = "test",
    )

    @Test
    fun savingANoteIndexesItsDatedItems() = runBlocking {
        notes.upsertLocal(note("n1", "- [ ] milk\n- [ ] passport @2026-08-25"), queuedAt = 1)

        val rows = tasks.rowsFor("n1")
        assertEquals(1, rows.size)
        assertEquals("passport", rows.single().text)
        assertEquals(1, rows.single().position)
    }

    @Test
    fun rewritingABodyReplacesTheIndexRatherThanAccumulating() = runBlocking {
        notes.upsertLocal(note("n1", "- [ ] a @2026-08-25"), queuedAt = 1)
        notes.upsertLocal(note("n1", "- [ ] b @2026-09-01"), queuedAt = 2)

        val rows = tasks.rowsFor("n1")
        assertEquals(1, rows.size)
        assertEquals("b", rows.single().text)
    }

    @Test
    fun removingADateRemovesTheTask() = runBlocking {
        notes.upsertLocal(note("n1", "- [ ] a @2026-08-25"), queuedAt = 1)
        notes.upsertLocal(note("n1", "- [ ] a"), queuedAt = 2)

        assertTrue(tasks.rowsFor("n1").isEmpty())
    }

    @Test
    fun indexingDoesNotDestroyThePendingOutboxRow() = runBlocking {
        // The T018 trap, re-checked on the new write path. `@Upsert` plus a CASCADE child is
        // exactly the shape that silently deleted the outbox row last time.
        notes.upsertLocal(note("n1", "- [ ] a @2026-08-25"), queuedAt = 1)
        notes.upsertLocal(note("n1", "- [ ] a @2026-08-26"), queuedAt = 2)

        assertEquals(1, db.outboxDao().pending().size)
    }

    @Test
    fun outstandingExcludesCheckedAndSortsSoonestFirst() = runBlocking {
        notes.upsertLocal(
            note("n1", "- [ ] later @2026-09-01\n- [ ] sooner @2026-08-22\n- [x] done @2026-08-01"),
            queuedAt = 1,
        )

        val outstanding = tasks.observeOutstanding().first()
        assertEquals(listOf("sooner", "later"), outstanding.map { it.text })
    }

    @Test
    fun completedIsItsOwnQuery() = runBlocking {
        notes.upsertLocal(note("n1", "- [ ] open @2026-09-01\n- [x] done @2026-08-01"), queuedAt = 1)

        assertEquals(listOf("done"), tasks.observeCompleted().first().map { it.text })
    }

    @Test
    fun aSoftDeletedNotesTasksDisappearFromTheListButKeepTheirRows() = runBlocking {
        notes.upsertLocal(note("n1", "- [ ] a @2026-08-25"), queuedAt = 1)
        notes.markDeletedLocal("n1", updatedAt = 2, queuedAt = 2)

        // Excluded from the list...
        assertTrue(tasks.observeOutstanding().first().isEmpty())
        // ...but the rows survive, so undoing the delete needs no rebuild.
        assertEquals(1, tasks.rowsFor("n1").size)
    }

    @Test
    fun aProseNoteIsNeverIndexed() = runBlocking {
        notes.upsertLocal(
            note("n1", "meeting @2026-08-25").copy(type = NoteType.NOTE),
            queuedAt = 1,
        )

        assertTrue(tasks.rowsFor("n1").isEmpty())
    }

    @Test
    fun theListCarriesTheTitleOfTheNoteTheTaskLivesIn() = runBlocking {
        notes.upsertLocal(note("n1", "- [ ] a @2026-08-25", title = "errands"), queuedAt = 1)

        assertEquals("errands", tasks.observeOutstanding().first().single().noteTitle)
    }

    @Test
    fun rebuildReproducesExactlyWhatIncrementalWritesProduced() = runBlocking {
        // The repair path ADR-0007 requires. If these two ever disagree, the index is not derived
        // and the whole argument for the table falls over.
        notes.upsertLocal(note("n1", "- [ ] a @2026-08-25\n- [ ] b"), queuedAt = 1)
        notes.upsertLocal(note("n2", "- [x] c @2026-09-01", title = "other"), queuedAt = 1)
        val incremental = tasks.observeOutstanding().first() + tasks.observeCompleted().first()

        notes.rebuildAllTasks()
        val rebuilt = tasks.observeOutstanding().first() + tasks.observeCompleted().first()

        assertEquals(incremental, rebuilt)
    }

    @Test
    fun rebuildRecoversFromAnEmptyIndex() = runBlocking {
        // What the 1 to 2 migration leaves behind: the table exists and is empty.
        notes.upsertLocal(note("n1", "- [ ] a @2026-08-25"), queuedAt = 1)
        db.compileStatement("DELETE FROM tasks").executeUpdateDelete()
        assertTrue(tasks.rowsFor("n1").isEmpty())

        notes.rebuildAllTasks()

        assertEquals(1, tasks.rowsFor("n1").size)
    }

    @Test
    fun hardDeletingANoteCascadesItsTasksAway() = runBlocking {
        notes.upsertLocal(note("n1", "- [ ] a @2026-08-25"), queuedAt = 1)
        db.compileStatement("DELETE FROM notes WHERE id = 'n1'").executeUpdateDelete()

        assertTrue(tasks.rowsFor("n1").isEmpty())
    }
}
