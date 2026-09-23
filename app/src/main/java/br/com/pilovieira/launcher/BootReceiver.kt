package br.com.pilovieira.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts the lock screen and clipboard alert services after a reboot, for whichever
 * features the user has enabled.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences("launcher_app_prefs", Context.MODE_PRIVATE)
        val lockScreenEnabled = prefs.getBoolean("key_lock_screen_enabled", false)
        if (lockScreenEnabled) {
            LockScreenService.start(context)
        }

        val clipboardAlertEnabled = prefs.getBoolean("key_clipboard_alert_enabled", false)
        if (clipboardAlertEnabled) {
            br.com.pilovieira.launcher.clipboard.ClipboardAlertService.start(context)
        }
    }
}
