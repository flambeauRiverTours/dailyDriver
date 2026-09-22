package com.flambeauriver.dailydriver.apps

/**
 * Rate-limits re-reads of the app index (2026-09-22).
 *
 * The index used to be built exactly once, in `MainActivity.onCreate`, and afterwards only on a
 * package install/uninstall broadcast. That reasoning was sound — this Activity *is* the home
 * screen, so it is resumed on every app switch and rebuilding each time would be constant work for
 * a list that rarely changes — but it left the app with no way to recover from a single bad read.
 * A truncated index survived for the life of the process, and the only cure was a force-stop.
 *
 * So the index is re-read when the launcher regains focus, and this decides how often that is
 * allowed to actually cost anything. Returning to the home screen happens dozens of times an hour;
 * the set of installed apps changes a few times a month.
 *
 * **[shouldRefresh] records the decision it returns**, so it is a gate rather than a predicate.
 * Splitting "may I?" from "I did" would leave two callers able to disagree, and the only caller
 * always refreshes when told yes.
 */
class RefreshGate(private val minimumIntervalMillis: Long = DEFAULT_INTERVAL_MILLIS) {

    private var lastRefreshAt: Long? = null

    /**
     * True if enough time has passed since the last refresh, recording [now] as the new mark.
     *
     * The first call always returns true: an index that has never been read is not fresh, it is
     * absent, and waiting out an interval before the first read would leave the launcher empty.
     *
     * [now] is caller-supplied rather than read here, so this is testable without waiting, and
     * the caller passes `SystemClock.elapsedRealtime()` rather than a wall clock.
     *
     * **A [now] earlier than the last mark refreshes rather than refusing.** `elapsed < interval`
     * alone is true for a negative elapsed, so a clock that went backwards would lock refreshes
     * out for the whole size of the jump — an hour of a launcher unable to notice its app list is
     * wrong, from a daylight-saving correction. The monotonic clock the caller passes should make
     * that unreachable, which is exactly why it is worth handling here instead: the guard costs
     * one comparison, and it means this class is not silently depending on a property of a clock
     * chosen somewhere else.
     */
    fun shouldRefresh(now: Long): Boolean {
        val last = lastRefreshAt
        if (last != null && now - last in 0 until minimumIntervalMillis) return false
        lastRefreshAt = now
        return true
    }

    /**
     * Drops the mark so the next [shouldRefresh] says yes.
     *
     * For the cases where something already known to change the app list has happened — a package
     * was installed, the user was unlocked — and waiting out the interval would show a list that
     * is known to be stale.
     */
    fun invalidate() {
        lastRefreshAt = null
    }

    companion object {
        /**
         * Long enough that returning to the launcher repeatedly costs nothing, short enough that a
         * wrong index heals on its own within a minute or two of being noticed. Not a correctness
         * boundary in either direction: the broadcast receivers still catch every real change, and
         * this is only the backstop for changes that arrive without one.
         */
        const val DEFAULT_INTERVAL_MILLIS = 60_000L
    }
}
