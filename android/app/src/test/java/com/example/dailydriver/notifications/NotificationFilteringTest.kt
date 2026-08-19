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
}
