package com.example.dailydriver.notifications

import android.service.notification.NotificationListenerService

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

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
    }

    override fun onListenerDisconnected() {
        instance = null
        super.onListenerDisconnected()
    }

    companion object {
        var instance: DailyDriverNotificationListenerService? = null
            private set
    }
}
