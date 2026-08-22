package com.flambeauriver.dailydriver.notes

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flambeauriver.dailydriver.data.DailyDriverDatabase
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
        // Tags aren't passed in — they're parsed from the body inside the transaction (T019), so
        // this also covers "the body is the single source of truth" end to end rather than
        // trusting a caller to have supplied a set that matches.
        notes.upsertLocal(note(body = "ship it #work #urgent"), queuedAt = 5_000L)

        assertEquals("Standup", notes.byId("n1")?.title)
        assertEquals(listOf("urgent", "work"), notes.tagsOf("n1"))
        assertEquals(listOf("n1"), outbox.pending().map { it.noteId })
    }

    @Test
    fun editingTheBodyRewritesTheTags() = runBlocking {
        // A tag removed from the body has to disappear from the index — otherwise T026's search
        // keeps returning the note under a tag it no longer carries.
        notes.upsertLocal(note(body = "#work #urgent"), queuedAt = 1L)

        notes.upsertLocal(note(body = "#personal", updatedAt = 2L), queuedAt = 2L)

        assertEquals(listOf("personal"), notes.tagsOf("n1"))
    }

    @Test
    fun everyLocalMutationLeavesSomethingInTheOutbox() = runBlocking {
        // The property §7.1 actually depends on: there is no way to change a note locally and
        // end up with nothing queued, because that note would never sync and never be noticed.
        notes.upsertLocal(note(id = "a"), queuedAt = 1L)
        notes.upsertLocal(note(id = "b"), queuedAt = 2L)
        notes.markDeletedLocal("a", updatedAt = 9L, queuedAt = 3L)

        assertEquals(setOf("a", "b"), outbox.pending().map { it.noteId }.toSet())
    }

    // --- coalescing -----------------------------------------------------------------------------

    @Test
    fun repeatedEditsCoalesceToOnePendingRow() = runBlocking {
        // Stands in for §4.6's debounced autosave, which fires continuously while typing.
        repeat(50) { i ->
            notes.upsertLocal(note(body = "draft $i", updatedAt = i.toLong()), queuedAt = i.toLong())
        }

        assertEquals(1, outbox.pending().size)
    }

    @Test
    fun coalescingKeepsTheBaseRevFromWhenTheNoteFirstBecameDirty() = runBlocking {
        // The reason enqueue uses IGNORE rather than REPLACE. baseRev is the comparison point the
        // server uses to detect a conflict (§7.2) — if a later save overwrote it, a conflicting
        // remote edit would stop being detectable.
        notes.applyRemote(note(rev = 7L), tags = emptyList())

        notes.upsertLocal(note(rev = 7L, body = "first edit"), queuedAt = 100L)
        notes.upsertLocal(note(rev = 7L, body = "second edit"), queuedAt = 200L)

        val pending = outbox.pending().single()
        assertEquals(7L, pending.baseRev)
        assertEquals(100L, pending.queuedAt)
    }

    @Test
    fun updatingANoteDoesNotDestroyItsPendingOutboxRow() = runBlocking {
        // Regression test. `@Insert(onConflict = REPLACE)` on notes looked harmless but SQLite
        // implements it as delete-then-insert, and both outbox and note_tags CASCADE off notes —
        // so every re-save silently deleted the pending row and re-queued it with a fresh
        // baseRev. Asserting row *identity* survives an update is what catches that; asserting
        // the row merely exists would not, since a re-created row also exists.
        notes.upsertLocal(note(rev = 7L), queuedAt = 100L)

        notes.upsertLocal(note(rev = 7L, body = "edited", updatedAt = 2_000L), queuedAt = 200L)

        val pending = outbox.pending().single()
        assertEquals("outbox row was re-created, not preserved", 100L, pending.queuedAt)
    }

    @Test
    fun applyingRemoteStateDoesNotDestroyAPendingLocalEdit() = runBlocking {
        // Same cascade hazard from the other direction: a pull landing on a note with unpushed
        // local changes must not silently drop them from the queue. Reconciling the two is
        // T067/T069's conflict handling — losing the row here would mean there's nothing left to
        // reconcile.
        notes.upsertLocal(note(rev = 7L), queuedAt = 100L)

        notes.applyRemote(note(rev = 8L, body = "from server"), tags = emptyList())

        assertEquals(listOf("n1"), outbox.pending().map { it.noteId })
    }

    @Test
    fun pendingNotesReflectCurrentStateNotWhatWasQueued() = runBlocking {
        // The outbox stores a pointer, not a payload snapshot — so a push always sends the latest
        // body rather than whatever was true when the row was first queued.
        notes.upsertLocal(note(body = "old"), queuedAt = 1L)
        notes.upsertLocal(note(body = "new"), queuedAt = 2L)

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
        notes.upsertLocal(note(), queuedAt = 1L)
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
        notes.upsertLocal(note(updatedAt = 500L), queuedAt = 1L)

        notes.confirmPushed("n1", assignedRev = 12L, pushedUpdatedAt = 500L)

        assertEquals(12L, notes.byId("n1")?.rev)
        assertTrue(outbox.pending().isEmpty())
    }

    @Test
    fun anEditDuringAPushSurvivesTheConfirmation() = runBlocking {
        // The failure mode coalescing would otherwise introduce: the push sends updatedAt=500,
        // the user types while it's in flight (updatedAt=600, same outbox row), and a blind clear
        // would strand that edit with nothing left to push it.
        notes.upsertLocal(note(updatedAt = 500L), queuedAt = 1L)
        notes.upsertLocal(note(updatedAt = 600L, body = "typed mid-push"), queuedAt = 2L)

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
        notes.upsertLocal(note(body = "#work"), queuedAt = 1L)
        notes.markDeletedLocal("n1", updatedAt = 2L, queuedAt = 2L)

        assertTrue(notes.observeByTag("work").first().isEmpty())
    }

    // --- search and lookup (T025/T026) --------------------------------------------------------------------

    @Test
    fun searchMatchesTitleAndBody() = runBlocking {
        notes.applyRemote(note(id = "a", title = "Standup", body = "nothing here"), emptyList())
        notes.applyRemote(note(id = "b", title = "Other", body = "about the standup"), emptyList())
        notes.applyRemote(note(id = "c", title = "Third", body = "unrelated"), emptyList())

        val found = notes.observeMatching(escapeLike("standup")).first().map { it.id }

        assertEquals(setOf("a", "b"), found.toSet())
    }

    @Test
    fun searchIsCaseInsensitiveForAscii() = runBlocking {
        notes.applyRemote(note(title = "Standup"), emptyList())

        assertEquals(1, notes.observeMatching(escapeLike("STANDUP")).first().size)
        assertEquals(1, notes.observeMatching(escapeLike("stAnDuP")).first().size)
    }

    @Test
    fun searchExcludesTombstones() = runBlocking {
        notes.upsertLocal(note(body = "findable"), queuedAt = 1L)
        notes.markDeletedLocal("n1", updatedAt = 2L, queuedAt = 2L)

        assertTrue(notes.observeMatching(escapeLike("findable")).first().isEmpty())
    }

    @Test
    fun searchReturnsNewestFirst() = runBlocking {
        notes.applyRemote(note(id = "old", title = "match", updatedAt = 100L), emptyList())
        notes.applyRemote(note(id = "new", title = "match", updatedAt = 300L), emptyList())

        assertEquals(listOf("new", "old"), notes.observeMatching(escapeLike("match")).first().map { it.id })
    }

    @Test
    fun percentIsSearchedLiterallyRatherThanAsAWildcard() = runBlocking {
        // The reason escapeLike + ESCAPE exist. Unescaped, "%" is a LIKE wildcard and this query
        // would return every note in the database.
        notes.applyRemote(note(id = "pct", body = "battery at 50% today"), emptyList())
        notes.applyRemote(note(id = "other", body = "nothing relevant"), emptyList())

        val found = notes.observeMatching(escapeLike("50%")).first().map { it.id }

        assertEquals(listOf("pct"), found)
    }

    @Test
    fun underscoreIsSearchedLiterallyRatherThanAsASingleCharacterWildcard() = runBlocking {
        // Unescaped, "_" matches any one character, so "snake_case" would also match "snakeXcase".
        notes.applyRemote(note(id = "snake", body = "snake_case naming"), emptyList())
        notes.applyRemote(note(id = "decoy", body = "snakeXcase naming"), emptyList())

        assertEquals(listOf("snake"), notes.observeMatching(escapeLike("snake_case")).first().map { it.id })
    }

    @Test
    fun aBackslashInTheTermDoesNotCorruptTheSearch() = runBlocking {
        // The escape character escaping itself — wrong order here silently breaks any term
        // containing a backslash.
        notes.applyRemote(note(id = "path", body = """C:\Users\notes"""), emptyList())

        assertEquals(listOf("path"), notes.observeMatching(escapeLike("""C:\Users""")).first().map { it.id })
    }

    @Test
    fun lookupByTitleIgnoresCase() = runBlocking {
        // `:note Standup` should find the note you called "standup" — you're typing a name from
        // memory, not copying an identifier.
        notes.applyRemote(note(title = "standup"), emptyList())

        assertEquals("n1", notes.byTitle("Standup")?.id)
        assertEquals("n1", notes.byTitle("STANDUP")?.id)
    }

    @Test
    fun lookupByTitleSkipsTombstones() = runBlocking {
        // Naming a new note after a deleted one should create a fresh note, not resurrect the
        // tombstone with its old body still in it.
        notes.upsertLocal(note(title = "standup"), queuedAt = 1L)
        notes.markDeletedLocal("n1", updatedAt = 2L, queuedAt = 2L)

        assertNull(notes.byTitle("standup"))
    }

    @Test
    fun lookupByTitleIsExactNotPartial() = runBlocking {
        notes.applyRemote(note(title = "standup notes"), emptyList())

        assertNull(notes.byTitle("standup"))
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
