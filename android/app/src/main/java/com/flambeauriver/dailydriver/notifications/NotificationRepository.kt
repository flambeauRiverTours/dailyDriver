package com.flambeauriver.dailydriver.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat

/**
 * Bridges [DailyDriverNotificationListenerService]'s live state to the UI (T147). Context-owning
 * glue with no separable pure logic beyond what NotificationFiltering.kt/NotificationDisplay.kt
 * already cover — not unit-tested directly, mirroring AppIndexRepository/AliasRepository.
 *
 * Unlike those two, there's no register()/unregister() here: the underlying service is bound and
 * unbound entirely by the system based on the access grant, not by this app's Activity lifecycle.
 */
class NotificationRepository(private val context: Context) {

    /** True once the user has granted access under Settings -> Notification access, by hand — see
     *  [openNotificationAccessSettings]; there is no runtime dialog for this permission. */
    fun hasAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /**
     * A fresh snapshot of currently active notifications, persistent ones excluded and oldest
     * first (T147's `newestLast` — the newest notification renders closest to the input). Empty
     * — not an error — whenever access isn't granted or the service isn't connected yet; callers
     * distinguish "no notifications" from "no access" via [hasAccess], not this list's emptiness.
     */
    val notifications: List<NotificationRecord>
        get() = DailyDriverNotificationListenerService.instance
            ?.activeNotifications
            ?.map { it.toNotificationRecord(context) }
            ?.excludeOngoing()
            ?.newestLast()
            .orEmpty()

    /**
     * Whether the T148 dot should show. Cheap by design — a plain field read on the connected
     * service (see [DailyDriverNotificationListenerService.hasUnread]), because the status line
     * polls this on every blink. False whenever access isn't granted or the service isn't
     * connected, which is the behavior you want: no access means nothing to indicate.
     */
    val hasUnreadNotifications: Boolean
        get() = DailyDriverNotificationListenerService.instance?.hasUnread == true

    /** Deep-links to the one place this permission can be granted — see the class doc. */
    fun openNotificationAccessSettings() {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /**
     * Fires the tapped notification's own `contentIntent` — the same mechanism the real
     * notification shade uses to "open" one. Also cancels it if the notification's own
     * `FLAG_AUTO_CANCEL` says it should disappear once acted on, matching shade behavior; firing
     * the `PendingIntent` ourselves bypasses SystemUI, which is what normally does that for you.
     * No-ops (returns false) if the notification is already gone or never had a `contentIntent`.
     */
    fun open(key: String): Boolean {
        val sbn = DailyDriverNotificationListenerService.instance
            ?.activeNotifications
            ?.firstOrNull { it.key == key }
            ?: return false
        val contentIntent = sbn.notification.contentIntent ?: return false
        val sent = try {
            contentIntent.send()
            true
        } catch (e: PendingIntent.CanceledException) {
            false
        }
        if (sent && (sbn.notification.flags and Notification.FLAG_AUTO_CANCEL) != 0) {
            DailyDriverNotificationListenerService.instance?.cancelNotification(key)
        }
        return sent
    }
}

private fun StatusBarNotification.toNotificationRecord(context: Context): NotificationRecord {
    val appLabel = try {
        context.packageManager.getApplicationInfo(packageName, 0)
            .loadLabel(context.packageManager)
            .toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }
    return NotificationRecord(
        key = key,
        appLabel = appLabel,
        title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
        text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
        isOngoing = isOngoing,
        postTime = postTime,
    )
}
