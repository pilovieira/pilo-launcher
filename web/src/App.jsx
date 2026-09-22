import { useEffect, useState } from 'react'
import FirebaseApi from './firebase'
import './App.css'

const toEntryList = (data) => {
  if (!data) return []
  return Object.entries(data)
    .map(([id, value]) => ({ id, text: value?.text ?? '', timestamp: value?.timestamp ?? (Number(id) || 0) }))
    .sort((a, b) => b.timestamp - a.timestamp)
}

function App() {
  const [user, setUser] = useState(undefined)
  const [entries, setEntries] = useState([])
  const [copiedId, setCopiedId] = useState(null)

  useEffect(() => FirebaseApi.registerAuthListener(setUser), [])

  useEffect(() => {
    if (!user) {
      setEntries([])
      return
    }
    return FirebaseApi.registerClipboardListener(user.uid, (data) => setEntries(toEntryList(data)))
  }, [user])

  const handleCopy = async (entry) => {
    try {
      await navigator.clipboard.writeText(entry.text)
      setCopiedId(entry.id)
      setTimeout(() => setCopiedId((current) => (current === entry.id ? null : current)), 1500)
    } catch (error) {
      console.error('Failed to copy to clipboard:', error)
    }
  }

  if (user === undefined) {
    return <div className="screen center">Carregando...</div>
  }

  if (user === null) {
    return (
      <div className="screen center">
        <h1>Clipboard</h1>
        <p className="subtitle">Sincronizado com o Pilo Launcher</p>
        <button className="google-btn" onClick={() => FirebaseApi.loginWithGoogle()}>
          Entrar com o Google
        </button>
      </div>
    )
  }

  return (
    <div className="screen">
      <header className="header">
        <div>
          <h1>Clipboard</h1>
          <span className="user-email">{user.email}</span>
        </div>
        <div className="header-actions">
          {entries.length > 0 && (
            <button className="link-btn" onClick={() => FirebaseApi.clearAll(user.uid)}>
              Limpar
            </button>
          )}
          <button className="link-btn" onClick={() => FirebaseApi.logout()}>
            Sair
          </button>
        </div>
      </header>

      {entries.length === 0 ? (
        <p className="empty">Nada copiado ainda</p>
      ) : (
        <ul className="entry-list">
          {entries.map((entry) => (
            <li key={entry.id} className="entry" onClick={() => handleCopy(entry)}>
              <span className="entry-text">{entry.text}</span>
              <div className="entry-actions">
                {copiedId === entry.id && <span className="copied-label">Copiado</span>}
                <button
                  className="delete-btn"
                  onClick={(event) => {
                    event.stopPropagation()
                    FirebaseApi.deleteEntry(user.uid, entry.id)
                  }}
                >
                  ✕
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export default App
