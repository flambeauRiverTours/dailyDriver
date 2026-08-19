package com.example.dailydriver.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationDisplayTest {

    private fun record(title: String? = null, text: String? = null) = NotificationRecord(
        key = "k",
        appLabel = "Messages",
        title = title,
        text = text,
        isOngoing = false,
        postTime = 0L,
    )

    @Test
    fun `title is preferred when present`() {
        assertEquals("Messages: New message", record(title = "New message", text = "body").displayLine())
    }

    @Test
    fun `text is the fallback when there is no title`() {
        assertEquals("Messages: just the body", record(text = "just the body").displayLine())
    }

    @Test
    fun `neither title nor text still shows the app name, not a blank line`() {
        assertEquals("Messages: (no content)", record().displayLine())
    }
}
