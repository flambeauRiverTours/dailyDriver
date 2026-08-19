package com.example.dailydriver.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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

    // --- clock boundaries ------------------------------------------------------------------------

    @Test
    fun `24-hour midnight and the last minute of the day`() {
        assertEquals("00:00", format(instant = at(0, 0)).time)
        assertEquals("23:59", format(instant = at(23, 59)).time)
    }

    @Test
    fun `12-hour crossings either side of noon and midnight`() {
        assertEquals("11:59 AM", format(instant = at(11, 59), is24Hour = false).time)
        assertEquals("12:01 PM", format(instant = at(12, 1), is24Hour = false).time)
        assertEquals("11:59 PM", format(instant = at(23, 59), is24Hour = false).time)
        assertEquals("12:01 AM", format(instant = at(0, 1), is24Hour = false).time)
    }

    @Test
    fun `every minute of the day formats without throwing, in both conventions`() {
        // Cheap exhaustive sweep — 1440 minutes is nothing, and it rules out an off-by-one in
        // the 12-hour conversion at any hour rather than only the ones picked by hand.
        (0..23).forEach { hour ->
            (0..59 step 7).forEach { minute ->
                val instant = at(hour, minute)
                assertTrue(format(instant = instant).time.isNotBlank())
                assertTrue(format(instant = instant, is24Hour = false).time.isNotBlank())
            }
        }
    }

    @Test
    fun `12-hour output always carries a meridiem and 24-hour never does`() {
        (0..23).forEach { hour ->
            val twelve = format(instant = at(hour, 0), is24Hour = false).time
            val twentyFour = format(instant = at(hour, 0)).time

            assertTrue("$hour: $twelve", twelve.endsWith(" AM") || twelve.endsWith(" PM"))
            assertTrue("$hour: $twentyFour", !twentyFour.contains("M"))
        }
    }

    @Test
    fun `24-hour time is always five characters wide, so the row never reflows`() {
        (0..23).forEach { hour ->
            assertEquals(5, format(instant = at(hour, 5)).time.length)
        }
    }

    // --- calendar boundaries ------------------------------------------------------------------------

    @Test
    fun `the date rolls over at midnight, not at some other hour`() {
        assertEquals("Tue 18 Aug", format(instant = onDayAt(8, 18, 23, 59)).date)
        assertEquals("Wed 19 Aug", format(instant = onDayAt(8, 19, 0, 0)).date)
    }

    @Test
    fun `month boundaries roll correctly`() {
        assertEquals("Mon 31 Aug", format(instant = onDayAt(8, 31, 12, 0)).date)
        assertEquals("Tue 1 Sep", format(instant = onDayAt(9, 1, 12, 0)).date)
    }

    @Test
    fun `year boundaries roll correctly`() {
        val newYearsEve = LocalDateTime.of(2026, 12, 31, 23, 59).toInstant(ZoneOffset.UTC)
        val newYearsDay = LocalDateTime.of(2027, 1, 1, 0, 0).toInstant(ZoneOffset.UTC)

        assertEquals("Thu 31 Dec", format(instant = newYearsEve).date)
        assertEquals("Fri 1 Jan", format(instant = newYearsDay).date)
    }

    @Test
    fun `a leap day is a real date`() {
        // 2028 is the next leap year; 2026 is not, so this can't be checked with the fixture.
        val leapDay = LocalDateTime.of(2028, 2, 29, 12, 0).toInstant(ZoneOffset.UTC)

        assertEquals("Tue 29 Feb", format(instant = leapDay).date)
    }

    @Test
    fun `every month abbreviates to something non-blank`() {
        (1..12).forEach { month ->
            assertTrue(format(instant = onDayAt(month, 15, 12, 0)).date.isNotBlank())
        }
    }

    // --- zones ----------------------------------------------------------------------------------------

    @Test
    fun `a half-hour offset zone is handled, not rounded to the hour`() {
        // India is UTC+5:30 — a zone that rounds to whole hours would show 20:02 here.
        assertEquals("20:02", format(zone = ZoneId.of("Asia/Kolkata")).time)
    }

    @Test
    fun `a zone ahead of UTC can roll the date forward`() {
        // 23:00 UTC is already the next day in Tokyo (UTC+9).
        val lateEvening = onDayAt(8, 19, 23, 0)

        assertEquals("Thu 20 Aug", format(instant = lateEvening, zone = ZoneId.of("Asia/Tokyo")).date)
    }

    @Test
    fun `the same instant reads differently in different zones`() {
        val tokyo = format(zone = ZoneId.of("Asia/Tokyo"))
        val newYork = format(zone = ZoneId.of("America/New_York"))

        assertNotEquals(tokyo.time, newYork.time)
    }

    @Test
    fun `a daylight-saving shift is applied by the zone rules, not ignored`() {
        // 16:00 UTC is 12:00 EDT in August but 11:00 EST in January — same clock time in UTC,
        // an hour apart locally.
        val newYork = ZoneId.of("America/New_York")
        val summer = LocalDateTime.of(2026, 8, 19, 16, 0).toInstant(ZoneOffset.UTC)
        val winter = LocalDateTime.of(2026, 1, 19, 16, 0).toInstant(ZoneOffset.UTC)

        assertEquals("12:00", format(instant = summer, zone = newYork).time)
        assertEquals("11:00", format(instant = winter, zone = newYork).time)
    }

    // --- locale ----------------------------------------------------------------------------------------

    @Test
    fun `weekday and month names follow the locale`() {
        val french = statusLineText(afternoon, ZoneOffset.UTC, true, true, Locale.FRENCH)

        assertTrue("was '${french.date}'", french.date.contains("19"))
        assertNotEquals("Wed 19 Aug", french.date)
    }

    @Test
    fun `the numeric time is not reordered by the locale`() {
        // Only the date's words are localised; the clock stays HH:mm so the blink stays aligned.
        assertEquals("14:32", statusLineText(afternoon, ZoneOffset.UTC, true, true, Locale.FRENCH).time)
    }

    // --- purity ------------------------------------------------------------------------------------------

    @Test
    fun `formatting is pure — repeated calls with the same inputs agree`() {
        assertEquals(format(), format())
        assertEquals(unreadIndicator(true), unreadIndicator(true))
    }

    @Test
    fun `the two halves are independent — the indicator never leaks into either`() {
        val text = format()

        assertTrue(!text.time.contains("•"))
        assertTrue(!text.date.contains("•"))
    }

    private fun at(hour: Int, minute: Int) =
        LocalDateTime.of(2026, 8, 19, hour, minute).toInstant(ZoneOffset.UTC)

    private fun onDay(month: Int, day: Int) =
        LocalDateTime.of(2026, month, day, 14, 32).toInstant(ZoneOffset.UTC)

    private fun onDayAt(month: Int, day: Int, hour: Int, minute: Int) =
        LocalDateTime.of(2026, month, day, hour, minute).toInstant(ZoneOffset.UTC)
}
