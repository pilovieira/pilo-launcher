package br.com.pilovieira.launcher.clipboard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import br.com.pilovieira.launcher.R
import br.com.pilovieira.launcher.auth.AuthManager
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference

// Foreground service that keeps a live Firebase Realtime Database listener on this user's
// clipboard entries, so pasting text on the Pilfy web app pops a modal on the device right
// away (see ClipboardAlertActivity). Only reacts to entries tagged with source "web" (this
// device's own writes are tagged "android" by ClipboardSync, so they're ignored here to
// avoid showing a modal for a copy the user just made on the phone itself), and only to
// entries added after the listener attaches, so a service restart doesn't replay history.
class ClipboardAlertService : Service() {

    private var databaseRef: DatabaseReference? = null
    private var childEventListener: ChildEventListener? = null
    private var startedAtMillis = 0L

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        attachListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        childEventListener?.let { databaseRef?.removeEventListener(it) }
        databaseRef = null
        childEventListener = null
    }

    private fun attachListener() {
        if (AuthManager.currentUser == null) {
            stopSelf()
            return
        }
        val ref = ClipboardSync.clipboardRef() ?: run {
            stopSelf()
            return
        }
        startedAtMillis = System.currentTimeMillis()
        databaseRef = ref

        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val source = snapshot.child("source").getValue(String::class.java)
                val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: return
                val text = snapshot.child("text").getValue(String::class.java) ?: return
                if (source == ClipboardSync.sourceAndroid || timestamp <= startedAtMillis) return

                val id = snapshot.key?.toLongOrNull() ?: timestamp
                ClipboardHistoryStore.addRemote(this@ClipboardAlertService, id, text)
                showAlert(text)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        childEventListener = listener
        ref.addChildEventListener(listener)
    }

    private fun showAlert(text: String) {
        val intent = Intent(this, ClipboardAlertActivity::class.java).apply {
            putExtra(ClipboardAlertActivity.EXTRA_TEXT, text)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        }
        startActivity(intent)
    }

    private fun buildNotification(): android.app.Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.clipboard_alert_service_channel),
                NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.clipboard_alert_service_notification))
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "clipboard_alert_service"
        private const val NOTIFICATION_ID = 43

        fun start(context: Context) {
            val intent = Intent(context, ClipboardAlertService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ClipboardAlertService::class.java))
        }
    }
}
