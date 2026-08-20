package com.example.dailydriver.notes

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dailydriver.data.DailyDriverDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Database-backed tests for the outbox invariant (T018).
 *
 * android/CLAUDE.md singles this out: "The outbox is the clear case: a mutation whose
 * transactional write isn't covered by a test isn't trusted." These run against a real in-memory
 * SQLite through Room, so foreign keys, conflict algorithms and transaction boundaries all behave
 * as they will on-device — none of which a JVM fake would reproduce.
 */
@RunWith(AndroidJUnit4::class)
class NoteDaoTest {

    private lateinit var database: DailyDriverDatabase
    private lateinit var notes: NoteDao
    private lateinit var outbox: OutboxDao
    private lateinit var syncState: SyncStateDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, DailyDriverDatabase::class.java).build()
        notes = database.noteDao()
        outbox = database.outboxDao()
        syncState = database.syncStateDao()
    }

    @After
    fun tearDown() = database.close()

    private fun note(
        id: String = "n1",
        title: String = "Standup",
        body: String = "notes body",
        updatedAt: Long = 1_000L,
        deleted: Boolean = false,
        rev: Long? = null,
    ) = Note(
        id = id,
        type = NoteType.NOTE,
        title = title,
        body = body,
        createdAt = 1_000L,
        updatedAt = updatedAt,
        deleted = deleted,
        rev = rev,
        sourceDevice = "test-device",
    )

    // --- the core invariant: a local change and its outbox row land together -------------------

    @Test
    fun localUpsertWritesNoteTagsAndOutboxTogether() = runBlocking {
        notes.upsertLocal(note(), tags = listOf("work", "urgent"), queuedAt = 5_000L)

        assertEquals("Standup", notes.byId("n1")?.title)
        assertEquals(listOf("urgent", "work"), notes.tagsOf("n1"))
        assertEquals(listOf("n1"), outbox.pending().map { it.noteId })
    }

    @Test
    fun everyLocalMutationLeavesSomethingInTheOutbox() = runBlocking {
        // The property §7.1 actually depends on: there is no way to change a note locally and
        // end up with nothing queued, because that note would never sync and never be noticed.
        notes.upsertLocal(note(id = "a"), tags = emptyList(), queuedAt = 1L)
        notes.upsertLocal(note(id = "b"), tags = emptyList(), queuedAt = 2L)
        notes.markDeletedLocal("a", updatedAt = 9L, queuedAt = 3L)

        assertEquals(setOf("a", "b"), outbox.pending().map { it.noteId }.toSet())
    }

    // --- coalescing -----------------------------------------------------------------------------

    @Test
    fun repeatedEditsCoalesceToOnePendingRow() = runBlocking {
        // Stands in for §4.6's debounced autosave, which fires continuously while typing.
        repeat(50) { i ->
            notes.upsertLocal(note(body = "draft $i", updatedAt = i.toLong()), emptyList(), queuedAt = i.toLong())
        }

        assertEquals(1, outbox.pending().size)
    }

    @Test
    fun coalescingKeepsTheBaseRevFromWhenTheNoteFirstBecameDirty() = runBlocking {
        // The reason enqueue uses IGNORE rather than REPLACE. baseRev is the comparison point the
        // server uses to detect a conflict (§7.2) — if a later save overwrote it, a conflicting
        // remote edit would stop being detectable.
        notes.applyRemote(note(rev = 7L), tags = emptyList())

        notes.upsertLocal(note(rev = 7L, body = "first edit"), emptyList(), queuedAt = 100L)
        notes.upsertLocal(note(rev = 7L, body = "second edit"), emptyList(), queuedAt = 200L)

        val pending = outbox.pending().single()
        assertEquals(7L, pending.baseRev)
        assertEquals(100L, pending.queuedAt)
    }

    @Test
    fun pendingNotesReflectCurrentStateNotWhatWasQueued() = runBlocking {
        // The outbox stores a pointer, not a payload snapshot — so a push always sends the latest
        // body rather than whatever was true when the row was first queued.
        notes.upsertLocal(note(body = "old"), emptyList(), queuedAt = 1L)
        notes.upsertLocal(note(body = "new"), emptyList(), queuedAt = 2L)

        assertEquals("new", outbox.pendingNotes().single().body)
    }

    // --- deletes are soft, and propagate ------------------------------------------------------------

    @Test
    fun deleteMarksATombstoneAndQueuesIt() = runBlocking {
        notes.applyRemote(note(rev = 3L), tags = emptyList())

        notes.markDeletedLocal("n1", updatedAt = 9_000L, queuedAt = 9_000L)

        val stored = notes.byId("n1")
        assertEquals(true, stored?.deleted)
        assertEquals(9_000L, stored?.updatedAt)
        assertEquals(3L, outbox.pending().single().baseRev)
    }

    @Test
    fun deletingAnUnknownNoteQueuesNothing() = runBlocking {
        // A row pointing at a non-existent note would violate the foreign key anyway; failing
        // quietly here is better than a crash from the editor's delete button on a stale id.
        notes.markDeletedLocal("ghost", updatedAt = 1L, queuedAt = 1L)

        assertTrue(outbox.pending().isEmpty())
    }

    @Test
    fun tombstonesAreHiddenFromTheNoteListButStillReadable() = runBlocking {
        notes.upsertLocal(note(), emptyList(), queuedAt = 1L)
        notes.markDeletedLocal("n1", updatedAt = 2L, queuedAt = 2L)

        assertTrue(notes.observeAll().first().isEmpty())

        // Still retrievable by id, because sync has to be able to see the tombstone in order to
        // propagate the deletion — it's hidden from the UI, not gone.
        val tombstone = notes.byId("n1")
        assertEquals(true, tombstone?.deleted)
        assertEquals("Standup", tombstone?.title)
    }

    // --- applying server state ------------------------------------------------------------------------

    @Test
    fun applyingARemoteNoteDoesNotQueueItBack() = runBlocking {
        // Pulled state is already the server's. Queueing it would push it straight back, and make
        // every pull indistinguishable from a local edit for conflict purposes.
        notes.applyRemote(note(rev = 4L), tags = listOf("work"))

        assertTrue(outbox.pending().isEmpty())
        assertEquals(4L, notes.byId("n1")?.rev)
        assertEquals(listOf("work"), notes.tagsOf("n1"))
    }

    @Test
    fun tagsAreReplacedWholesaleNotMerged() = runBlocking {
        // Tags are derived from the body (T019), so the incoming set is authoritative — a tag
        // removed from the body has to disappear from the index too.
        notes.applyRemote(note(), tags = listOf("work", "urgent"))

        notes.applyRemote(note(), tags = listOf("personal"))

        assertEquals(listOf("personal"), notes.tagsOf("n1"))
    }

    @Test
    fun duplicateTagsCollapse() = runBlocking {
        // "#work today, #work tomorrow" parses to the same tag twice; the composite primary key
        // would otherwise reject the insert outright.
        notes.applyRemote(note(), tags = listOf("work", "work"))

        assertEquals(listOf("work"), notes.tagsOf("n1"))
    }

    // --- confirming a push ------------------------------------------------------------------------------

    @Test
    fun confirmingAPushStoresTheRevAndDrainsTheQueue() = runBlocking {
        notes.upsertLocal(note(updatedAt = 500L), emptyList(), queuedAt = 1L)

        notes.confirmPushed("n1", assignedRev = 12L, pushedUpdatedAt = 500L)

        assertEquals(12L, notes.byId("n1")?.rev)
        assertTrue(outbox.pending().isEmpty())
    }

    @Test
    fun anEditDuringAPushSurvivesTheConfirmation() = runBlocking {
        // The failure mode coalescing would otherwise introduce: the push sends updatedAt=500,
        // the user types while it's in flight (updatedAt=600, same outbox row), and a blind clear
        // would strand that edit with nothing left to push it.
        notes.upsertLocal(note(updatedAt = 500L), emptyList(), queuedAt = 1L)
        notes.upsertLocal(note(updatedAt = 600L, body = "typed mid-push"), emptyList(), queuedAt = 2L)

        notes.confirmPushed("n1", assignedRev = 12L, pushedUpdatedAt = 500L)

        assertEquals(listOf("n1"), outbox.pending().map { it.noteId })
        assertEquals("typed mid-push", outbox.pendingNotes().single().body)
    }

    // --- tag queries ---------------------------------------------------------------------------------------

    @Test
    fun tagLookupIsExactNotSubstring() = runBlocking {
        // The concrete reason note_tags is a table rather than a delimited column: LIKE '%work%'
        // matches "workout", so T026's search would return notes the user didn't tag.
        notes.applyRemote(note(id = "a", title = "A"), tags = listOf("work"))
        notes.applyRemote(note(id = "b", title = "B"), tags = listOf("workout"))

        assertEquals(listOf("a"), notes.observeByTag("work").first().map { it.id })
        assertEquals(listOf("b"), notes.observeByTag("workout").first().map { it.id })
    }

    @Test
    fun tagLookupExcludesTombstones() = runBlocking {
        notes.upsertLocal(note(), tags = listOf("work"), queuedAt = 1L)
        notes.markDeletedLocal("n1", updatedAt = 2L, queuedAt = 2L)

        assertTrue(notes.observeByTag("work").first().isEmpty())
    }

    // --- sync watermark ------------------------------------------------------------------------------------

    @Test
    fun theWatermarkStartsUnsetAndRoundTrips() = runBlocking {
        assertNull(syncState.lastPulledRev())

        syncState.set(SyncState(lastPulledRev = 42L))

        assertEquals(42L, syncState.lastPulledRev())
    }

    @Test
    fun advancingTheWatermarkReplacesRatherThanAccumulates() = runBlocking {
        syncState.set(SyncState(lastPulledRev = 42L))
        syncState.set(SyncState(lastPulledRev = 99L))

        assertEquals(99L, syncState.lastPulledRev())
    }
}
