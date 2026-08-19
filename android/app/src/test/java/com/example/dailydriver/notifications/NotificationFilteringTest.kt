package com.example.dailydriver.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationFilteringTest {

    private fun record(key: String, isOngoing: Boolean = false, postTime: Long = 0L) =
        NotificationRecord(
            key = key,
            appLabel = "App",
            title = "Title",
            text = "Text",
            isOngoing = isOngoing,
            postTime = postTime,
        )

    @Test
    fun `ongoing notifications are excluded — foreground services, media players, downloads`() {
        val downloading = record("download", isOngoing = true)
        val message = record("message", isOngoing = false)

        assertEquals(listOf(message), listOf(downloading, message).excludeOngoing())
    }

    @Test
    fun `a list with nothing ongoing is unchanged`() {
        val a = record("a")
        val b = record("b")

        assertEquals(listOf(a, b), listOf(a, b).excludeOngoing())
    }

    @Test
    fun `newestLast orders oldest-first so the newest lands closest to the input`() {
        // Mirrors AppListView's bottom-anchored intent: the most relevant item is the one
        // physically closest to the prompt, at the bottom of the screen.
        val oldest = record("oldest", postTime = 100L)
        val middle = record("middle", postTime = 200L)
        val newest = record("newest", postTime = 300L)

        val ordered = listOf(newest, oldest, middle).newestLast()

        assertEquals(listOf(oldest, middle, newest), ordered)
    }

    // --- excludeOngoing, in full ------------------------------------------------------------

    @Test
    fun `filtering an empty list gives an empty list`() {
        assertEquals(emptyList<NotificationRecord>(), emptyList<NotificationRecord>().excludeOngoing())
    }

    @Test
    fun `a list of nothing but ongoing notifications filters down to nothing`() {
        // The realistic quiet state: a music player and a running download, nothing to read.
        // The T148 dot depends on this being empty rather than merely short.
        val allOngoing = listOf(record("media", isOngoing = true), record("dl", isOngoing = true))

        assertEquals(emptyList<NotificationRecord>(), allOngoing.excludeOngoing())
    }

    @Test
    fun `filtering preserves the relative order of what survives`() {
        val a = record("a")
        val b = record("b", isOngoing = true)
        val c = record("c")

        assertEquals(listOf(a, c), listOf(a, b, c).excludeOngoing())
    }

    @Test
    fun `filtering does not mutate the receiver`() {
        val original = listOf(record("a"), record("b", isOngoing = true))
        val copy = original.toList()

        original.excludeOngoing()

        assertEquals(copy, original)
    }

    // --- newestLast, in full --------------------------------------------------------------------

    @Test
    fun `ordering an empty or single-item list is a no-op`() {
        val one = listOf(record("only"))

        assertEquals(emptyList<NotificationRecord>(), emptyList<NotificationRecord>().newestLast())
        assertEquals(one, one.newestLast())
    }

    @Test
    fun `an already-ordered list is left alone`() {
        val ordered = listOf(record("a", postTime = 1L), record("b", postTime = 2L))

        assertEquals(ordered, ordered.newestLast())
    }

    @Test
    fun `notifications posted in the same millisecond keep their original relative order`() {
        // sortedBy is stable, which is what stops two simultaneous notifications from swapping
        // places on every recomposition.
        val first = record("first", postTime = 500L)
        val second = record("second", postTime = 500L)

        assertEquals(listOf(first, second), listOf(first, second).newestLast())
        assertEquals(listOf(second, first), listOf(second, first).newestLast())
    }

    @Test
    fun `ordering does not mutate the receiver`() {
        val original = listOf(record("b", postTime = 2L), record("a", postTime = 1L))
        val copy = original.toList()

        original.newestLast()

        assertEquals(copy, original)
    }

    @Test
    fun `epoch and far-future timestamps sort sensibly`() {
        val epoch = record("epoch", postTime = 0L)
        val future = record("future", postTime = Long.MAX_VALUE)

        assertEquals(listOf(epoch, future), listOf(future, epoch).newestLast())
    }

    // --- the two together ------------------------------------------------------------------------

    @Test
    fun `the repository's pipeline drops ongoing notifications and orders the rest`() {
        // Exactly what NotificationRepository.notifications chains, asserted end to end.
        val media = record("media", isOngoing = true, postTime = 400L)
        val old = record("old", postTime = 100L)
        val new = record("new", postTime = 300L)

        assertEquals(listOf(old, new), listOf(new, media, old).excludeOngoing().newestLast())
    }

    @Test
    fun `order of the two operations does not change the result`() {
        val media = record("media", isOngoing = true, postTime = 400L)
        val old = record("old", postTime = 100L)
        val new = record("new", postTime = 300L)
        val input = listOf(new, media, old)

        assertEquals(input.excludeOngoing().newestLast(), input.newestLast().excludeOngoing())
    }
}
