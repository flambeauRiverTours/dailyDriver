package com.example.dailydriver.command

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dailydriver.data.DailyDriverDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The command log against real SQLite (T014).
 *
 * The whole table rests on `ON CONFLICT DO UPDATE` incrementing rather than replacing. A plain
 * insert would fail on the second occurrence; an upsert that replaced would reset the count to 1
 * every time. Both are invisible from Kotlin and both destroy the frequency signal the table
 * exists to carry — you would collect for months and find every count was 1.
 */
@RunWith(AndroidJUnit4::class)
class CommandLogDaoTest {

    private lateinit var db: DailyDriverDatabase
    private lateinit var log: CommandLogDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DailyDriverDatabase::class.java,
        ).build()
        log = db.commandLogDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun repeatingAnInputIncrementsRatherThanDuplicating() = runBlocking {
        log.record("spotify", "com.spotify.music", "T1", now = 1)
        log.record("spotify", "com.spotify.music", "T1", now = 2)
        log.record("spotify", "com.spotify.music", "T1", now = 3)

        val rows = log.all()
        assertEquals(1, rows.size)
        assertEquals(3, rows.single().count)
    }

    @Test
    fun firstSeenIsKeptAndLastSeenMoves() = runBlocking {
        log.record("spotify", "com.spotify.music", "T1", now = 100)
        log.record("spotify", "com.spotify.music", "T1", now = 500)

        val row = log.all().single()
        assertEquals(100, row.firstSeen)
        assertEquals(500, row.lastSeen)
    }

    @Test
    fun theSameInputResolvingDifferentlyIsItsOwnRow() = runBlocking {
        log.record("sp", "com.spotify.music", "T1", now = 1)
        log.record("sp", "spotify", "T0", now = 2)

        assertEquals(2, log.all().size)
    }

    @Test
    fun unresolvedInputIsCollectedSeparately() = runBlocking {
        log.record("spotify", "com.spotify.music", "T1", now = 1)
        log.record("remind me to call the dentist", "", "T3", now = 2)
        log.record("asdf", "", "T3", now = 3)

        val unknowns = log.unresolved().map { it.input }
        assertEquals(2, unknowns.size)
        assertTrue("remind me to call the dentist" in unknowns)
    }

    @Test
    fun theExportIsOrderedByHowOftenSomethingIsTyped() = runBlocking {
        log.record("rare", "com.rare", "T1", now = 1)
        repeat(5) { log.record("common", "com.common", "T1", now = 2) }

        assertEquals(listOf("common", "rare"), log.all().map { it.input })
    }

    @Test
    fun casingIsPreservedBecauseItIsPartOfTheInput() = runBlocking {
        log.record("Spotify", "com.spotify.music", "T1", now = 1)
        log.record("spotify", "com.spotify.music", "T1", now = 2)

        assertEquals(2, log.all().size)
    }

    @Test
    fun clearingRemovesEverything() = runBlocking {
        log.record("spotify", "com.spotify.music", "T1", now = 1)
        log.clear()

        assertTrue(log.all().isEmpty())
    }
}
