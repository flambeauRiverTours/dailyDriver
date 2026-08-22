package com.flambeauriver.dailydriver.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    // --- blank is not the same as absent --------------------------------------------------------
    //
    // A plain `title ?: text` only falls back on null, so a notification carrying EXTRA_TITLE ""
    // alongside real body text rendered as "Messages: " with nothing after the colon — the one
    // case where the fallback exists and didn't fire. Real notifications do ship empty extras.

    @Test
    fun `an empty title falls through to the text`() {
        assertEquals("Messages: real body", record(title = "", text = "real body").displayLine())
    }

    @Test
    fun `a whitespace-only title falls through to the text`() {
        assertEquals("Messages: real body", record(title = "   ", text = "real body").displayLine())
        assertEquals("Messages: real body", record(title = "\t\n", text = "real body").displayLine())
    }

    @Test
    fun `an empty text is skipped in favour of the placeholder`() {
        assertEquals("Messages: (no content)", record(title = null, text = "").displayLine())
        assertEquals("Messages: (no content)", record(title = "", text = "").displayLine())
    }

    @Test
    fun `both fields blank in any combination gives the placeholder`() {
        assertEquals("Messages: (no content)", record(title = "  ", text = "  ").displayLine())
        assertEquals("Messages: (no content)", record(title = "", text = null).displayLine())
    }

    // --- content handling -------------------------------------------------------------------------

    @Test
    fun `a title wins over text whenever it has real content`() {
        assertEquals("Messages: Title", record(title = "Title", text = "Body").displayLine())
    }

    @Test
    fun `content is not trimmed, only tested for blankness`() {
        // Leading space in a title is the sender's formatting, not ours to silently rewrite.
        assertEquals("Messages:  padded ", record(title = " padded ").displayLine())
    }

    @Test
    fun `long content is shown in full rather than truncated`() {
        // Documents the current choice — the row is a single Text and will clip on screen, but
        // the string itself is complete.
        val long = "x".repeat(500)

        assertEquals("Messages: $long", record(text = long).displayLine())
    }

    @Test
    fun `newlines inside the content are preserved rather than collapsed`() {
        // Multi-line notification bodies are common; nothing here flattens them, so the row will
        // render them as-is.
        assertEquals("Messages: line one\nline two", record(text = "line one\nline two").displayLine())
    }

    @Test
    fun `non-ASCII content survives`() {
        assertEquals("Messages: 日本語のメッセージ", record(title = "日本語のメッセージ").displayLine())
    }

    @Test
    fun `the app label always leads the line`() {
        listOf(
            record(title = "T"),
            record(text = "B"),
            record(),
            record(title = "", text = "B"),
        ).forEach { assertTrue(it.displayLine().startsWith("Messages: ")) }
    }

    @Test
    fun `content containing a colon does not confuse the label separator`() {
        assertEquals("Messages: Re: your order", record(title = "Re: your order").displayLine())
    }

    @Test
    fun `formatting is pure — repeated calls agree`() {
        val notification = record(title = "Title")

        assertEquals(notification.displayLine(), notification.displayLine())
    }
}
