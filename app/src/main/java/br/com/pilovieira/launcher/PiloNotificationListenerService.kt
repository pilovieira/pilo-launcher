package br.com.pilovieira.launcher

import android.service.notification.StatusBarNotification
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks the number of active, user-visible notifications so the lock screen can display a
 * count. Requires the user to grant "Notification access" in system settings.
 */
class PiloNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        updateCount()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        updateCount()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        updateCount()
    }

    private fun updateCount() {
        val count = try {
            activeNotifications.count { sbn -> !sbn.isOngoing }
        } catch (_: Exception) {
            0
        }
        _notificationCount.value = count
    }

    companion object {
        private val _notificationCount = MutableStateFlow(0)
        val notificationCount: StateFlow<Int> = _notificationCount.asStateFlow()
    }
}
