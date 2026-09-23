package br.com.pilovieira.launcher.clipboard

import br.com.pilovieira.launcher.auth.AuthManager
import com.google.firebase.database.FirebaseDatabase

// Mirrors the local clipboard history to Realtime Database so the web app at
// pilo-clipboard.web.app can show it. Same convention as the rest of the Firebase
// project: the root of the path is the signed-in user's uid, and this app owns the
// "clipboard-launcher" child under it. A no-op whenever the user isn't signed in.
object ClipboardSync {

    private const val childKey = "clipboard-launcher"

    // Tags every entry this device writes so ClipboardAlertService can tell apart entries
    // that originated here (which shouldn't trigger the on-device alert modal) from ones
    // pasted on the Pilfy web app (which should).
    const val sourceAndroid = "android"

    fun clipboardRef() =
        AuthManager.currentUser?.uid?.let { uid ->
            FirebaseDatabase.getInstance().getReference(uid).child(childKey)
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
