package br.com.pilovieira.launcher

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import java.util.Calendar

object FocusModeHelper {
    private const val PREFS_NAME = "focus_mode_prefs"
    private const val KEY_FOCUS_ENABLED = "key_focus_enabled"
    const val ACTION_CHECK_FOCUS_MODE = "br.com.pilovieira.launcher.ACTION_CHECK_FOCUS_MODE"
    private const val REQUEST_CODE_FOCUS_ALARM = 1001

    fun isFocusModeEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_FOCUS_ENABLED, false)
    }

    fun setFocusModeEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_FOCUS_ENABLED, enabled).apply()

        if (enabled) {
            scheduleNextAlarm(context)
            applyRingerMode(context)
        } else {
            cancelAlarm(context)
            // Restore to normal ring mode if permission is granted
            restoreNormalMode(context)
        }
    }

    fun hasNotificationPolicyAccess(context: Context): Boolean {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        return notificationManager?.isNotificationPolicyAccessGranted == true
    }

    fun isWithinVibrateWindow(): Boolean {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        // Between 9:00 (inclusive) and 18:00 (exclusive)
        return hour in 9..17
    }

    fun applyRingerMode(context: Context) {
        if (!isFocusModeEnabled(context)) return

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        // On Android 7.0+, changing ringer mode to/from silent/vibrate might require notification policy access
        if (notificationManager != null && !notificationManager.isNotificationPolicyAccessGranted) {
            return
        }

        try {
            if (isWithinVibrateWindow()) {
                // 9:00 to 18:00 -> Vibrate
                if (audioManager.ringerMode != AudioManager.RINGER_MODE_VIBRATE) {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                }
            } else {
                // 18:00 to 9:00 -> Normal (Ring)
                if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                }
            }
        } catch (_: SecurityException) {
            // Handled if permission is revoked
        }
    }

    private fun restoreNormalMode(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (notificationManager != null && !notificationManager.isNotificationPolicyAccessGranted) {
            return
        }
        try {
            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
        } catch (_: SecurityException) {
        }
    }

    fun scheduleNextAlarm(context: Context) {
        if (!isFocusModeEnabled(context)) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, FocusModeReceiver::class.java).apply {
            action = ACTION_CHECK_FOCUS_MODE
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE_FOCUS_ALARM, intent, flags)

        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

        // Target next change point: 9:00 or 18:00
        val targetCalendar = Calendar.getInstance().apply {
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (currentHour < 9) {
            // Next is 9:00 today
            targetCalendar.set(Calendar.HOUR_OF_DAY, 9)
        } else if (currentHour < 18) {
            // Next is 18:00 today
            targetCalendar.set(Calendar.HOUR_OF_DAY, 18)
        } else {
            // Next is 9:00 tomorrow
            targetCalendar.add(Calendar.DAY_OF_YEAR, 1)
            targetCalendar.set(Calendar.HOUR_OF_DAY, 9)
        }

        val triggerTime = targetCalendar.timeInMillis

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }

    private fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, FocusModeReceiver::class.java).apply {
            action = ACTION_CHECK_FOCUS_MODE
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE_FOCUS_ALARM, intent, flags)
        alarmManager.cancel(pendingIntent)
    }
}
