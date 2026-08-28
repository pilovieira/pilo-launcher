package br.com.pilovieira.launcher

import android.content.Context
import android.provider.Settings

object NotificationAccessHelper {
    fun isNotificationListenerEnabled(context: Context): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return enabledListeners.split(":").any { it.contains(context.packageName) }
    }
}
