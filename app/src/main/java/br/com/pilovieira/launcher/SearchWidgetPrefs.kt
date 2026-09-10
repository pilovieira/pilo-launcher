package br.com.pilovieira.launcher

import android.content.Context

private const val PREFS = "search_widget_prefs"
private const val KEY_WIDGET_ID = "widget_id"

object SearchWidgetPrefs {
    fun getWidgetId(context: Context): Int? {
        val id = prefs(context).getInt(KEY_WIDGET_ID, -1)
        return if (id == -1) null else id
    }

    fun setWidgetId(context: Context, id: Int?) {
        prefs(context).edit().putInt(KEY_WIDGET_ID, id ?: -1).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
