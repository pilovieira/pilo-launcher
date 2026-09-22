package br.com.pilovieira.launcher.clipboard

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils

object ClipboardAccessibilityHelper {

    fun isServiceEnabled(context: Context): Boolean {
        val expectedComponent = "${context.packageName}/${ClipboardAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return TextUtils.SimpleStringSplitter(':').apply { setString(enabledServices) }
            .asSequence()
            .any { it.equals(expectedComponent, ignoreCase = true) }
    }

    fun requestEnableService(context: Context) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }
}

private fun TextUtils.SimpleStringSplitter.asSequence(): Sequence<String> = sequence {
    while (hasNext()) yield(next())
}
