package br.com.pilovieira.launcher.clipboard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import br.com.pilovieira.launcher.R

// Handles "Pilfy" as a destination in the system Share sheet (Send/Share flow), for apps
// that don't expose ACTION_PROCESS_TEXT in their text-selection menu (e.g. web pages
// rendered inside a browser). Saves the shared text into the clipboard history and closes
// without showing any UI.
class ShareReceiveActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = intent?.takeIf { it.action == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT)
        if (!text.isNullOrBlank()) {
            ClipboardHistoryStore.add(this, text)
            Toast.makeText(this, getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
