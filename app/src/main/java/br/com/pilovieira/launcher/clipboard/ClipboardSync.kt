package br.com.pilovieira.launcher.clipboard

import br.com.pilovieira.launcher.auth.AuthManager
import com.google.firebase.database.FirebaseDatabase

// Mirrors the local clipboard history to Realtime Database so the web app at
// pilfy.web.app can show it. Unlike the rest of the Firebase project (where the root is
// directly the user's uid), Pilfy's own data lives under its own "pilfy" root, keyed by
// uid: pilfy/<uid>/<entryId>. A no-op whenever the user isn't signed in.
object ClipboardSync {

    private const val rootKey = "pilfy"

    // Tags every entry this device writes so ClipboardAlertService can tell apart entries
    // that originated here (which shouldn't trigger the on-device alert modal) from ones
    // pasted on the Pilfy web app (which should).
    const val sourceAndroid = "android"

    fun clipboardRef() =
        AuthManager.currentUser?.uid?.let { uid ->
            FirebaseDatabase.getInstance().getReference(rootKey).child(uid)
        }

    fun push(entry: ClipboardEntry) {
        clipboardRef()?.child(entry.id.toString())
            ?.setValue(mapOf("text" to entry.text, "timestamp" to entry.id, "source" to sourceAndroid))
    }

    fun remove(id: Long) {
        clipboardRef()?.child(id.toString())?.removeValue()
    }

    fun clear() {
        clipboardRef()?.removeValue()
    }
}
