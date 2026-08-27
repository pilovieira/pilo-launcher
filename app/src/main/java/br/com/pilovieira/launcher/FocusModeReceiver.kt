package br.com.pilovieira.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class FocusModeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!FocusModeHelper.isFocusModeEnabled(context)) return

        when (intent.action) {
            FocusModeHelper.ACTION_CHECK_FOCUS_MODE,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                FocusModeHelper.applyRingerMode(context)
                FocusModeHelper.scheduleNextAlarm(context)
            }
        }
    }
}
