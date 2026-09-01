package br.com.pilovieira.launcher

import android.content.Context

private const val CAR_MODE_PREFS = "car_mode_prefs"
private const val PREF_ENABLED = "enabled"
private const val PREF_AUTO_ENABLED = "auto_enabled"
private const val PREF_AUTO_DEVICE_ADDRESS = "auto_device_address"
private const val PREF_AUTO_DEVICE_NAME = "auto_device_name"
private const val SLOT_COUNT = 6

private fun slotKey(index: Int) = "slot_$index"

object CarModePrefs {
    const val SLOTS = SLOT_COUNT

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

    fun loadSlots(context: Context): List<String?> =
        (0 until SLOT_COUNT).map { index -> prefs(context).getString(slotKey(index), null) }

    fun saveSlot(context: Context, index: Int, appKey: String?) {
        prefs(context).edit().putString(slotKey(index), appKey).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(CAR_MODE_PREFS, Context.MODE_PRIVATE)
}
