import { initializeApp } from 'firebase/app'
import { getAuth, GoogleAuthProvider, onAuthStateChanged, signInWithPopup, signOut } from 'firebase/auth'
import { getDatabase, onValue, ref, remove, set } from 'firebase/database'

const firebaseConfig = {
  projectId: 'pilovieira-sandbox',
  appId: '1:1099130718140:web:e792451962a559d87d0db9',
  databaseURL: 'https://pilovieira-sandbox.firebaseio.com',
  storageBucket: 'pilovieira-sandbox.appspot.com',
  apiKey: 'AIzaSyD2qVqtDkbieFXjTX5kbU8Dp1nuJkhcyOA',
  authDomain: 'pilovieira-sandbox.firebaseapp.com',
  messagingSenderId: '1099130718140'
}

const app = initializeApp(firebaseConfig)
const auth = getAuth(app)
const db = getDatabase(app)
const googleAuthProvider = new GoogleAuthProvider()

// Same convention as the Android app / Fiado web: root path is the user's uid, and each
// app that shares this Firebase project owns one top-level child under it. The launcher's
// clipboard entries live at <uid>/clipboard-launcher/<entryId>.
const clipboardPath = (uid) => `${uid}/clipboard-launcher`

class FirebaseApi {
  static registerAuthListener(listener) {
    return onAuthStateChanged(auth, listener)
  }

  static loginWithGoogle() {
    return signInWithPopup(auth, googleAuthProvider)
  }

  static logout() {
    return signOut(auth)
  }

  static registerClipboardListener(uid, listener) {
    const clipboardRef = ref(db, clipboardPath(uid))
    return onValue(
      clipboardRef,
      (snapshot) => listener(snapshot.val()),
      (error) => {
        console.error('Clipboard listener error:', error)
        listener(null)
      }
    )
  }

  // Same shape the Android app writes: <uid>/clipboard-launcher/<timestamp> = { text, timestamp }.
  static addEntry(uid, text) {
    const timestamp = Date.now()
    return set(ref(db, `${clipboardPath(uid)}/${timestamp}`), { text, timestamp })
  }

  static deleteEntry(uid, entryId) {
    return remove(ref(db, `${clipboardPath(uid)}/${entryId}`))
  }

  static clearAll(uid) {
    return remove(ref(db, clipboardPath(uid)))
  }
}

export default FirebaseApi
