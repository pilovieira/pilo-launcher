package br.com.pilovieira.launcher.clipboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ClipboardEntry(
    val id: Long,
    val text: String
)

// Persists a capped, most-recent-first history of copied text, backed by its own
// SharedPreferences file so it stays independent from the rest of the launcher's settings.
object ClipboardHistoryStore {

    private const val prefsName = "clipboard_history_prefs"
    private const val keyItems = "items"
    private const val maxItems = 200

    private fun prefs(context: Context) =
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun getAll(context: Context): List<ClipboardEntry> {
        val raw = prefs(context).getString(keyItems, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                ClipboardEntry(id = obj.getLong("id"), text = obj.getString("text"))
            }
        }.getOrDefault(emptyList())
    }

    // Adds a new entry to the front of the history, skipping it if identical to the most
    // recent one so re-copying the same text repeatedly doesn't spam the list.
    fun add(context: Context, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val current = getAll(context)
        if (current.firstOrNull()?.text == trimmed) return

        val newEntry = ClipboardEntry(id = System.currentTimeMillis(), text = trimmed)
        val updated = listOf(newEntry) + current
        save(context, updated.take(maxItems))
        ClipboardSync.push(newEntry)
    }

    // Inserts an entry that was captured elsewhere (the Pilfy web app) and already has an
    // id/timestamp assigned. Local-only: does not push back to Firebase, and is a no-op if
    // an entry with this id is already present, so it's safe to call more than once for the
    // same remote entry (e.g. if the alert service restarts).
    fun addRemote(context: Context, id: Long, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val current = getAll(context)
        if (current.any { it.id == id }) return

        val updated = listOf(ClipboardEntry(id = id, text = trimmed)) + current
        save(context, updated.take(maxItems))
    }

    fun remove(context: Context, id: Long) {
        save(context, getAll(context).filterNot { it.id == id })
        ClipboardSync.remove(id)
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(keyItems).apply()
        ClipboardSync.clear()
    }

    private fun save(context: Context, items: List<ClipboardEntry>) {
        val array = JSONArray()
        items.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("text", entry.text)
                }
            )
        }
        prefs(context).edit().putString(keyItems, array.toString()).apply()
    }
}
