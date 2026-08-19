package com.example.dailydriver.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * The bound-by-the-system service that gives this app read access to other apps' notifications
 * (T147) — this can only exist once the user has granted access by hand under Settings ->
 * Notification access (`NotificationRepository.hasAccess`/`openNotificationAccessSettings`);
 * there is no runtime permission dialog for it, so this class is inert dead weight until then.
 *
 * [instance] is the standard pattern for reaching a bound service's live state from the rest of
 * the app: Android owns the lifecycle (you can't construct or bind to this yourself), so the only
 * way for [NotificationRepository] to read `activeNotifications` is through a reference this
 * service publishes about itself while connected.
 */
class DailyDriverNotificationListenerService : NotificationListenerService() {

    /**
     * Whether any non-persistent notification is currently present — the boolean behind T148's
     * indicator dot. Maintained here, updated only when notifications actually change, so the
     * status line's twice-a-second tick can read it as a plain field instead of making a binder
     * call to `activeNotifications` on every blink.
     *
     * `!isOngoing` is the same rule [excludeOngoing] applies to the `:notif` listing. The
     * predicate is deliberately duplicated rather than shared, because answering yes/no through
     * that path would mean materializing a [NotificationRecord] per notification — including a
     * `PackageManager` label lookup each — just to check whether the list is empty.
     */
    var hasUnread: Boolean = false
        private set

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        refreshUnread()
    }

    override fun onListenerDisconnected() {
        instance = null
        hasUnread = false
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refreshUnread()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshUnread()
    }

    /**
     * `activeNotifications` throws if the listener isn't currently bound — which these callbacks
     * shouldn't hit, but a disconnect racing an in-flight callback is exactly the kind of thing
     * that would take the home screen down with it.
     */
    private fun refreshUnread() {
        hasUnread = try {
            activeNotifications?.any { !it.isOngoing } == true
        } catch (e: SecurityException) {
            false
        }
    }

    companion object {
        var instance: DailyDriverNotificationListenerService? = null
            private set
    }
}
