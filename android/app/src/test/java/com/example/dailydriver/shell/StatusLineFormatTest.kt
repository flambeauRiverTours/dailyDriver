package com.example.dailydriver.shell

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class StatusLineFormatTest {

    /** 2026-08-19 14:32 UTC — a Wednesday, so the weekday isn't accidentally right. */
    private val afternoon = LocalDateTime.of(2026, 8, 19, 14, 32).toInstant(ZoneOffset.UTC)

    private fun format(
        instant: java.time.Instant = afternoon,
        zone: ZoneId = ZoneOffset.UTC,
        is24Hour: Boolean = true,
        colonVisible: Boolean = true,
    ) = statusLineText(instant, zone, is24Hour, colonVisible, Locale.US)

    // --- time ------------------------------------------------------------------------------------

    @Test
    fun `24-hour time is zero-padded`() {
        assertEquals("14:32", format().time)
        assertEquals("09:05", format(instant = at(9, 5)).time)
    }

    @Test
    fun `12-hour time drops the leading zero and carries a meridiem`() {
        assertEquals("2:32 PM", format(is24Hour = false).time)
        assertEquals("9:05 AM", format(instant = at(9, 5), is24Hour = false).time)
    }

    @Test
    fun `12-hour midnight and noon are 12, not 0`() {
        assertEquals("12:15 AM", format(instant = at(0, 15), is24Hour = false).time)
        assertEquals("12:00 PM", format(instant = at(12, 0), is24Hour = false).time)
    }

    @Test
    fun `hiding the colon swaps in a space, so the text never changes width`() {
        // Monospace: ':' and ' ' occupy the same cell, so the blink doesn't shift the layout.
        assertEquals("14 32", format(colonVisible = false).time)
        assertEquals("2 32 PM", format(is24Hour = false, colonVisible = false).time)
    }

    @Test
    fun `the zone is applied, not ignored in favour of UTC`() {
        val newYork = ZoneId.of("America/New_York") // UTC-4 on this date

        assertEquals("10:32", format(zone = newYork).time)
    }

    // --- date ------------------------------------------------------------------------------------

    @Test
    fun `date is short weekday, day, short month`() {
        assertEquals("Wed 19 Aug", format().date)
    }

    @Test
    fun `single-digit days are not zero-padded`() {
        assertEquals("Sat 5 Sep", format(instant = onDay(9, 5)).date)
    }

    @Test
    fun `the date rolls with the zone, not just the clock`() {
        // 00:15 UTC is still the previous evening in New York — the date has to follow.
        val newYork = ZoneId.of("America/New_York")

        assertEquals("Tue 18 Aug", format(instant = at(0, 15), zone = newYork).date)
    }

    @Test
    fun `the blinking colon never touches the date`() {
        assertEquals(format().date, format(colonVisible = false).date)
    }

    // --- T148 unread indicator ---------------------------------------------------------------

    @Test
    fun `the indicator is the same width present or absent, so the clock never shifts`() {
        // The whole point of reserving the cells: a notification arriving must not shove the
        // time sideways. Same invariant as the blinking colon becoming a space.
        assertEquals(unreadIndicator(hasUnread = true).length, unreadIndicator(hasUnread = false).length)
    }

    @Test
    fun `the indicator shows a dot only when there is something unread`() {
        assertEquals("• ", unreadIndicator(hasUnread = true))
        assertEquals("  ", unreadIndicator(hasUnread = false))
    }

    private fun at(hour: Int, minute: Int) =
        LocalDateTime.of(2026, 8, 19, hour, minute).toInstant(ZoneOffset.UTC)

    private fun onDay(month: Int, day: Int) =
        LocalDateTime.of(2026, month, day, 14, 32).toInstant(ZoneOffset.UTC)
}
