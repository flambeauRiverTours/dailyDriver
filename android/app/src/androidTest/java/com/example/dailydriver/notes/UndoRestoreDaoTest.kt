package com.example.dailydriver.notes

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dailydriver.data.DailyDriverDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Undo of a discarded note, against real SQLite (T138).
 *
 * The claim undo rests on is that re-saving a discarded draft *un-tombstones* it: `toNote()`
 * produces `deleted = false` and `@Upsert` updates every column. That is a reasonable reading of
 * Room's behaviour and exactly the kind of reasonable reading that was wrong at T018, where
 * `@Upsert` versus `INSERT OR REPLACE` silently destroyed an outbox row. If this is wrong, undo
 * looks like it worked and the note stays deleted.
 */
@RunWith(AndroidJUnit4::class)
class UndoRestoreDaoTest {

    private lateinit var db: DailyDriverDatabase
    private lateinit var notes: NoteDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DailyDriverDatabase::class.java,
        ).build()
        notes = db.noteDao()
    }

    @After
    fun tearDown() = db.close()

    private fun note(body: String = "milk #errands") = Note(
        id = "n1",
        type = NoteType.NOTE,
        title = "groceries",
        body = body,
        createdAt = 1,
        updatedAt = 1,
        sourceDevice = "test",
    )

    @Test
    fun resavingADiscardedNoteBringsItBack() = runBlocking {
        notes.upsertLocal(note(), queuedAt = 1)
        notes.markDeletedLocal("n1", updatedAt = 2, queuedAt = 2)
        assertEquals(true, notes.byId("n1")?.deleted)

        // What undo does.
        notes.upsertLocal(note().copy(updatedAt = 3), queuedAt = 3)

        val restored = notes.byId("n1")
        assertFalse("undo left the note tombstoned", restored?.deleted ?: true)
        assertEquals("milk #errands", restored?.body)
    }

    @Test
    fun restoringReindexesTheTagsThatWentWithIt() = runBlocking {
        notes.upsertLocal(note(), queuedAt = 1)
        notes.markDeletedLocal("n1", updatedAt = 2, queuedAt = 2)
        notes.upsertLocal(note().copy(updatedAt = 3), queuedAt = 3)

        assertEquals(listOf("errands"), notes.tagsOf("n1"))
    }

    @Test
    fun restoringQueuesTheResurrectionForSync() = runBlocking {
        // A restore that never reaches the server would come back deleted on the next pull.
        notes.upsertLocal(note(), queuedAt = 1)
        notes.markDeletedLocal("n1", updatedAt = 2, queuedAt = 2)
        notes.upsertLocal(note().copy(updatedAt = 3), queuedAt = 3)

        assertEquals(1, db.outboxDao().pending().size)
    }
}
