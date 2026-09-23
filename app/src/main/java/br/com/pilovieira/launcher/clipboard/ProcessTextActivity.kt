package br.com.pilovieira.launcher.clipboard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import br.com.pilovieira.launcher.R

// Handles the "CopyCopy" entry Android adds to the text-selection floating toolbar
// (next to Copy / Select All / Paste) in any app, via the standard Process Text API.
// Saves the selected text straight into the clipboard history and closes without UI.
class ProcessTextActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        if (!text.isNullOrBlank()) {
            ClipboardHistoryStore.add(this, text)
            Toast.makeText(this, getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
