package br.com.pilovieira.launcher

import android.content.Context

private const val CAR_MODE_PREFS = "car_mode_prefs"
private const val PREF_ENABLED = "enabled"
private const val PREF_AUTO_ENABLED = "auto_enabled"
private const val PREF_AUTO_DEVICE_ADDRESS = "auto_device_address"
private const val PREF_AUTO_DEVICE_NAME = "auto_device_name"
const val CAR_MODE_ROW_COUNT = 3

enum class CarModeSlotPosition { LEFT, RIGHT, WIDE }

sealed class CarModeSlotContent {
    data class App(val appKey: String) : CarModeSlotContent()
    data class Widget(val appWidgetId: Int) : CarModeSlotContent()
}

data class CarModeRowConfig(
    val wide: Boolean,
    val left: CarModeSlotContent?,
    val right: CarModeSlotContent?
)

private fun rowWideKey(index: Int) = "row_${index}_wide_mode"
private fun rowLeftKey(index: Int) = "row_${index}_left"
private fun rowRightKey(index: Int) = "row_${index}_right"

private fun encodeContent(content: CarModeSlotContent?): String? = when (content) {
    null -> null
    is CarModeSlotContent.App -> "app:${content.appKey}"
    is CarModeSlotContent.Widget -> "widget:${content.appWidgetId}"
}

private fun decodeContent(raw: String?): CarModeSlotContent? {
    if (raw == null) return null
    return when {
        raw.startsWith("app:") -> CarModeSlotContent.App(raw.removePrefix("app:"))
        raw.startsWith("widget:") -> raw.removePrefix("widget:").toIntOrNull()?.let { CarModeSlotContent.Widget(it) }
        else -> null
    }
}

object CarModePrefs {

    fun loadRows(context: Context): List<CarModeRowConfig> =
        (0 until CAR_MODE_ROW_COUNT).map { index -> loadRow(context, index) }

    fun loadRow(context: Context, index: Int): CarModeRowConfig {
        val p = prefs(context)
        return CarModeRowConfig(
            wide = p.getBoolean(rowWideKey(index), false),
            left = decodeContent(p.getString(rowLeftKey(index), null)),
            right = decodeContent(p.getString(rowRightKey(index), null))
        )
    }

    fun saveRow(context: Context, index: Int, config: CarModeRowConfig) {
        prefs(context).edit()
            .putBoolean(rowWideKey(index), config.wide)
            .putString(rowLeftKey(index), encodeContent(config.left))
            .putString(rowRightKey(index), encodeContent(config.right))
            .apply()
    }

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(PREF_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(PREF_ENABLED, enabled).apply()
    }

    fun isAutoEnabled(context: Context): Boolean =
        prefs(context).getBoolean(PREF_AUTO_ENABLED, false)

    fun setAutoEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(PREF_AUTO_ENABLED, enabled).apply()
    }

    fun getAutoDeviceAddress(context: Context): String? =
        prefs(context).getString(PREF_AUTO_DEVICE_ADDRESS, null)

    fun getAutoDeviceName(context: Context): String? =
        prefs(context).getString(PREF_AUTO_DEVICE_NAME, null)

    fun setAutoDevice(context: Context, address: String?, name: String?) {
        prefs(context).edit()
            .putString(PREF_AUTO_DEVICE_ADDRESS, address)
            .putString(PREF_AUTO_DEVICE_NAME, name)
            .apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(CAR_MODE_PREFS, Context.MODE_PRIVATE)
}
