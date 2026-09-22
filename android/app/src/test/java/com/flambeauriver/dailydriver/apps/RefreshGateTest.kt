package com.flambeauriver.dailydriver.apps

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RefreshGate] is the whole of the app index's new re-read policy, and it is pure, so it is
 * tested directly rather than through [AppIndexRepository] — which needs a real Context and a real
 * PackageManager, and so has no unit-test surface at all.
 */
class RefreshGateTest {

    @Test
    fun `the first call always refreshes`() {
        // An index that has never been read is not fresh, it is empty. Waiting out an interval
        // before the first read would mean the launcher starts with no apps in it.
        assertTrue(RefreshGate(minimumIntervalMillis = 60_000).shouldRefresh(now = 0))
    }

    @Test
    fun `a second call inside the interval does not refresh`() {
        val gate = RefreshGate(minimumIntervalMillis = 60_000)
        gate.shouldRefresh(now = 1_000)

        // This is the case that actually happens: returning to the home screen repeatedly, which
        // is most of what a launcher ever does.
        assertFalse(gate.shouldRefresh(now = 1_000 + 59_999))
    }

    @Test
    fun `a call after the interval refreshes again`() {
        val gate = RefreshGate(minimumIntervalMillis = 60_000)
        gate.shouldRefresh(now = 1_000)

        assertTrue(gate.shouldRefresh(now = 1_000 + 60_000))
    }

    @Test
    fun `the interval runs from the last refresh, not the first`() {
        val gate = RefreshGate(minimumIntervalMillis = 60_000)
        gate.shouldRefresh(now = 0)
        gate.shouldRefresh(now = 60_000)

        // If the mark were never moved, everything after the first interval would refresh on every
        // single focus regain — exactly the per-HOME-press cost this exists to prevent.
        assertFalse(gate.shouldRefresh(now = 90_000))
        assertTrue(gate.shouldRefresh(now = 120_000))
    }

    @Test
    fun `a refused call does not move the mark`() {
        val gate = RefreshGate(minimumIntervalMillis = 60_000)
        gate.shouldRefresh(now = 0)
        gate.shouldRefresh(now = 30_000)

        // The refusal at 30s must not count as a refresh, or a busy stretch of app switching would
        // push the next real read further and further out and the index would never heal.
        assertTrue(gate.shouldRefresh(now = 60_000))
    }

    @Test
    fun `invalidate forces the next call to refresh`() {
        val gate = RefreshGate(minimumIntervalMillis = 60_000)
        gate.shouldRefresh(now = 1_000)

        gate.invalidate()

        // What a package broadcast and a user unlock both do: the list is known to be stale rather
        // than merely old, so the interval should not hold it back.
        assertTrue(gate.shouldRefresh(now = 1_001))
    }

    @Test
    fun `a clock that goes backwards does not lock refreshes out`() {
        val gate = RefreshGate(minimumIntervalMillis = 60_000)
        gate.shouldRefresh(now = 1_000_000)

        // SystemClock.elapsedRealtime is monotonic so this should not arise, and the assertion is
        // here to pin why that clock was chosen: with a wall clock, a correction jumping backwards
        // would make `now - last` negative and hold refreshes off for the size of the jump.
        assertTrue(gate.shouldRefresh(now = 0))
    }
}
