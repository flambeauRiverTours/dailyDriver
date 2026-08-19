package com.example.dailydriver.shell

import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** The two halves of the status row — [time] sits at the left edge, [date] at the right. */
data class StatusLineText(val time: String, val date: String)

/**
 * T148's unread-notification indicator, as the leading cells of the status row.
 *
 * Always the same width whether or not it's showing, for the same reason the blinking colon
 * becomes a space rather than vanishing: the time sits immediately to its right, and a dot that
 * changed the row's width would shove the clock sideways every time a notification arrived.
 *
 * Not `#FFFFFF` — T005 established this project uses no literal white; the caller styles this
 * with the existing `onBackground` token, brighter than the dimmed time and date beside it.
 */
fun unreadIndicator(hasUnread: Boolean): String = if (hasUnread) "$UNREAD_DOT " else "  "

private const val UNREAD_DOT = '•'

/**
 * Renders the status line's clock and date (T146). Pure and Android-independent so it lives in
 * `src/test/` per android/CLAUDE.md — only the *ticking* needs Compose, and that's [StatusLine]'s
 * job.
 *
 * [colonVisible] drives the blink by swapping the colon for a space rather than dropping it: in a
 * monospace font both occupy one cell, so the time never changes width and the row doesn't jitter
 * twice a second.
 *
 * `java.time` needs no desugaring here — it's API 26+, well below this project's `minSdk` of 33.
 */
fun statusLineText(
    instant: Instant,
    zone: ZoneId,
    is24Hour: Boolean,
    colonVisible: Boolean,
    locale: Locale = Locale.getDefault(),
): StatusLineText {
    val local = instant.atZone(zone)

    val time = if (is24Hour) {
        "%02d:%02d".format(locale, local.hour, local.minute)
    } else {
        // 0 and 12 both display as 12 — the usual 12-hour convention, which `hour % 12` alone
        // gets wrong for midnight and noon.
        val hour12 = local.hour % 12
        val meridiem = if (local.hour < 12) "AM" else "PM"
        "%d:%02d %s".format(locale, if (hour12 == 0) 12 else hour12, local.minute, meridiem)
    }

    // Deliberately not zero-padded and not locale-reordered — "Wed 19 Aug" was the chosen shape
    // (design check-in, 2026-08-19). The weekday and month names still come from the locale.
    val date = buildString {
        append(local.dayOfWeek.getDisplayName(TextStyle.SHORT, locale))
        append(' ')
        append(local.dayOfMonth)
        append(' ')
        append(local.month.getDisplayName(TextStyle.SHORT, locale))
    }

    return StatusLineText(
        time = if (colonVisible) time else time.replace(':', ' '),
        date = date,
    )
}
