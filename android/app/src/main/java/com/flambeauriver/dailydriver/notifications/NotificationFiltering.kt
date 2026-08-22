package com.flambeauriver.dailydriver.notifications

/**
 * Persistent notifications (foreground services, media players, active downloads) are ambient
 * status, not "something happened" — excluded from `:notif`'s listing per the original request.
 * `isOngoing` alone, not also `!isClearable`, per the 2026-08-19 design check-in — a no-clear-
 * but-not-ongoing notification is rare enough not to warrant the more aggressive filter.
 */
fun List<NotificationRecord>.excludeOngoing(): List<NotificationRecord> = filterNot { it.isOngoing }

/**
 * Oldest first, so the newest notification lands last in the list — the design doc's bottom-
 * anchored convention (see AppListView) puts the physically-closest-to-the-input row last, and
 * the newest notification is the one most likely to be what you opened `:notif` for.
 */
fun List<NotificationRecord>.newestLast(): List<NotificationRecord> = sortedBy { it.postTime }
