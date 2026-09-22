package br.com.pilovieira.launcher.clipboard

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.view.accessibility.AccessibilityEvent

// Runs continuously in the background once the user enables it in Accessibility settings.
// Its only purpose is to hold a live ClipboardManager listener: Android only allows clipboard
// reads from an app that's in the foreground OR that has an enabled AccessibilityService, so
// simply having this service running grants the whole app process permission to read clipboard
// changes regardless of which app is currently on screen.
class ClipboardAccessibilityService : AccessibilityService() {

    private var listener: ClipboardManager.OnPrimaryClipChangedListener? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val newListener = ClipboardManager.OnPrimaryClipChangedListener {
            runCatching {
                val text = clipboardManager.primaryClip
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)
                    ?.coerceToText(this)
                    ?.toString()
                if (!text.isNullOrBlank()) {
                    ClipboardHistoryStore.add(this, text)
                }
            }
        }
        clipboardManager.addPrimaryClipChangedListener(newListener)
        listener = newListener
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No accessibility events are needed; this service only exists to unlock
        // background clipboard reads.
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        listener?.let {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).removePrimaryClipChangedListener(it)
        }
        listener = null
        super.onDestroy()
    }
}
