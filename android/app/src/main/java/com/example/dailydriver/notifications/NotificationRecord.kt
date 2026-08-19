package com.example.dailydriver.notifications

/**
 * A plain, Android-independent snapshot of one active notification — mirrors `apps.AppEntry`'s
 * role: everything the pure filtering/display logic needs, already resolved out of the real
 * `StatusBarNotification`/`PackageManager` calls that can't run in a JVM unit test.
 */
data class NotificationRecord(
    val key: String,
    val appLabel: String,
    val title: String?,
    val text: String?,
    val isOngoing: Boolean,
    val postTime: Long,
)
