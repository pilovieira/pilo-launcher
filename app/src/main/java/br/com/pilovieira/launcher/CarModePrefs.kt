package br.com.pilovieira.launcher

import android.content.Context

private const val CAR_MODE_PREFS = "car_mode_prefs"
private const val PREF_ENABLED = "enabled"
private const val PREF_AUTO_ENABLED = "auto_enabled"
private const val PREF_AUTO_DEVICES = "auto_devices"

enum class CarModeGridSize(val columns: Int, val rows: Int) {
    TWO_BY_THREE(2, 3)
}

data class CarModeBluetoothDevice(val address: String, val name: String)

sealed class CarModeSlotContent {
    data class App(val appKey: String) : CarModeSlotContent()
    data class Widget(val appWidgetId: Int) : CarModeSlotContent()
}

data class CarModeRowConfig(
    val wide: Boolean,
    val slots: List<CarModeSlotContent?>
)

private fun rowWideKey(index: Int) = "row_${index}_wide_mode"
private fun rowSlotKey(rowIndex: Int, columnIndex: Int) = "row_${rowIndex}_col_${columnIndex}"

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

private fun encodeDevice(device: CarModeBluetoothDevice): String = "${device.address}|${device.name}"

private fun decodeDevice(raw: String): CarModeBluetoothDevice? {
    val separatorIndex = raw.indexOf('|')
    if (separatorIndex < 0) return null
    val address = raw.substring(0, separatorIndex)
    val name = raw.substring(separatorIndex + 1)
    if (address.isEmpty()) return null
    return CarModeBluetoothDevice(address, name)
}

object CarModePrefs {

    fun loadRows(context: Context, gridSize: CarModeGridSize): List<CarModeRowConfig> =
        (0 until gridSize.rows).map { index -> loadRow(context, index, gridSize.columns) }

    fun loadRow(context: Context, index: Int, columns: Int): CarModeRowConfig {
        val p = prefs(context)
        val slots = (0 until columns).map { col -> decodeContent(p.getString(rowSlotKey(index, col), null)) }
        return CarModeRowConfig(
            wide = p.getBoolean(rowWideKey(index), false),
            slots = slots
        )
    }

    fun saveRow(context: Context, index: Int, config: CarModeRowConfig) {
        val editor = prefs(context).edit().putBoolean(rowWideKey(index), config.wide)
        config.slots.forEachIndexed { col, content ->
            editor.putString(rowSlotKey(index, col), encodeContent(content))
        }
        editor.apply()
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

    fun getAutoDevices(context: Context): List<CarModeBluetoothDevice> {
        val stored = prefs(context).getStringSet(PREF_AUTO_DEVICES, emptySet()) ?: emptySet()
        return stored.mapNotNull { decodeDevice(it) }.sortedBy { it.name }
    }

    fun setAutoDevices(context: Context, devices: List<CarModeBluetoothDevice>) {
        prefs(context).edit()
            .putStringSet(PREF_AUTO_DEVICES, devices.map { encodeDevice(it) }.toSet())
            .apply()
    }

    fun addAutoDevice(context: Context, address: String, name: String) {
        val current = getAutoDevices(context).filter { it.address != address }
        setAutoDevices(context, current + CarModeBluetoothDevice(address, name))
    }

    fun removeAutoDevice(context: Context, address: String) {
        setAutoDevices(context, getAutoDevices(context).filter { it.address != address })
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(CAR_MODE_PREFS, Context.MODE_PRIVATE)
}
