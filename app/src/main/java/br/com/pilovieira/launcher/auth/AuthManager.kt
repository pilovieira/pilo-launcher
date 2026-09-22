package br.com.pilovieira.launcher.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

// Wraps Google Sign-In + Firebase Auth so the rest of the app only needs to know
// whether there's a signed-in FirebaseUser, used later to identify the account that
// owns synced clipboard entries.
object AuthManager {

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    val currentUser: FirebaseUser? get() = auth.currentUser

    val isSignedIn: Boolean get() = currentUser != null

    private fun signInClient(context: Context): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(br.com.pilovieira.launcher.R.string.default_web_client_id))
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, options)
    }

    fun signInIntent(context: Context): Intent = signInClient(context).signInIntent

    // Call from onActivityResult / the ActivityResultLauncher callback with the intent
    // returned by signInIntent(); resolves once Firebase has exchanged the Google ID
    // token for a signed-in FirebaseUser.
    suspend fun handleSignInResult(context: Context, data: Intent?): Result<FirebaseUser> {
        return runCatching {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data).await()
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            auth.signInWithCredential(credential).await().user
                ?: error("Firebase sign-in returned no user")
        }
    }

    fun signOut(context: Context) {
        auth.signOut()
        signInClient(context).signOut()
    }
}
