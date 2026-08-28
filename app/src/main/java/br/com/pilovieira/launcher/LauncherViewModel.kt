package br.com.pilovieira.launcher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ClockStyle {
    ANALOG,
    DIGITAL
}

enum class ListDensity {
    COMPACT,
    NORMAL
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("launcher_app_prefs", Context.MODE_PRIVATE)
    private val keyHiddenApps = "key_hidden_apps"
    private val keyRecentApps = "key_recent_apps"
    private val keyClockStyle = "key_clock_style"
    private val keyLockScreenEnabled = "key_lock_screen_enabled"
    private val keyListDensity = "key_list_density"
    private val customLabelPrefix = "label_"
    private val maxRecentApps = 15

    private val _rawApps = MutableStateFlow<List<AppInfo>>(emptyList())

    private val _hiddenAppKeys = MutableStateFlow<Set<String>>(loadHiddenAppKeys())
    val hiddenAppKeys: StateFlow<Set<String>> = _hiddenAppKeys.asStateFlow()

    private val _customLabels = MutableStateFlow<Map<String, String>>(loadCustomLabels())
    val customLabels: StateFlow<Map<String, String>> = _customLabels.asStateFlow()

    private val _recentAppKeys = MutableStateFlow<List<String>>(loadRecentAppKeys())

    private val _clockStyle = MutableStateFlow(loadClockStyle())
    val clockStyle: StateFlow<ClockStyle> = _clockStyle.asStateFlow()

    private val _lockScreenEnabled = MutableStateFlow(prefs.getBoolean(keyLockScreenEnabled, false))
    val lockScreenEnabled: StateFlow<Boolean> = _lockScreenEnabled.asStateFlow()

    private val _listDensity = MutableStateFlow(loadListDensity())
    val listDensity: StateFlow<ListDensity> = _listDensity.asStateFlow()

    // All installed apps, with any custom labels applied and re-sorted.
    val allApps: StateFlow<List<AppInfo>> = combine(_rawApps, _customLabels) { raw, labels ->
        raw.map { app ->
            val customLabel = labels[app.key]
            if (customLabel != null) app.copy(label = customLabel) else app
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Apps displayed on the launcher screen (only non-hidden apps)
    val apps: StateFlow<List<AppInfo>> = combine(allApps, _hiddenAppKeys) { all, hidden ->
        all.filter { app -> !hidden.contains(app.key) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Recently opened apps, most recent first.
    val recentApps: StateFlow<List<AppInfo>> = combine(allApps, _recentAppKeys) { all, recentKeys ->
        val byKey = all.associateBy { it.key }
        recentKeys.mapNotNull { byKey[it] }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadApps()
        }
    }

    init {
        loadApps()
        registerPackageReceiver()
    }

    private fun loadHiddenAppKeys(): Set<String> {
        return prefs.getStringSet(keyHiddenApps, emptySet())?.toSet() ?: emptySet()
    }

    private fun loadCustomLabels(): Map<String, String> {
        return prefs.all
            .filterKeys { it.startsWith(customLabelPrefix) }
            .mapKeys { it.key.removePrefix(customLabelPrefix) }
            .mapNotNull { (key, value) -> (value as? String)?.let { key to it } }
            .toMap()
    }

    private fun loadRecentAppKeys(): List<String> {
        val stored = prefs.getString(keyRecentApps, null) ?: return emptyList()
        return stored.split("|").filter { it.isNotBlank() }
    }

    private fun loadClockStyle(): ClockStyle {
        val stored = prefs.getString(keyClockStyle, null) ?: return ClockStyle.ANALOG
        return try {
            ClockStyle.valueOf(stored)
        } catch (_: IllegalArgumentException) {
            ClockStyle.ANALOG
        }
    }

    fun setClockStyle(style: ClockStyle) {
        _clockStyle.value = style
        prefs.edit().putString(keyClockStyle, style.name).apply()
    }

    private fun loadListDensity(): ListDensity {
        val stored = prefs.getString(keyListDensity, null) ?: return ListDensity.NORMAL
        return try {
            ListDensity.valueOf(stored)
        } catch (_: IllegalArgumentException) {
            ListDensity.NORMAL
        }
    }

    fun setListDensity(density: ListDensity) {
        _listDensity.value = density
        prefs.edit().putString(keyListDensity, density.name).apply()
    }

    fun setLockScreenEnabled(enabled: Boolean) {
        _lockScreenEnabled.value = enabled
        prefs.edit().putBoolean(keyLockScreenEnabled, enabled).apply()
        if (enabled) {
            LockScreenService.start(getApplication())
        } else {
            LockScreenService.stop(getApplication())
        }
    }

    fun setAppVisibility(app: AppInfo, visible: Boolean) {
        val updated = _hiddenAppKeys.value.toMutableSet()
        if (visible) {
            updated.remove(app.key)
        } else {
            updated.add(app.key)
        }
        _hiddenAppKeys.value = updated
        prefs.edit().putStringSet(keyHiddenApps, updated).apply()
    }

    fun renameApp(app: AppInfo, newLabel: String) {
        val trimmed = newLabel.trim()
        val updated = _customLabels.value.toMutableMap()
        if (trimmed.isEmpty()) {
            updated.remove(app.key)
            prefs.edit().remove(customLabelPrefix + app.key).apply()
        } else {
            updated[app.key] = trimmed
            prefs.edit().putString(customLabelPrefix + app.key, trimmed).apply()
        }
        _customLabels.value = updated
    }

    fun recordAppOpened(app: AppInfo) {
        val updated = _recentAppKeys.value.toMutableList()
        updated.remove(app.key)
        updated.add(0, app.key)
        val trimmed = if (updated.size > maxRecentApps) updated.take(maxRecentApps) else updated
        _recentAppKeys.value = trimmed
        prefs.edit().putString(keyRecentApps, trimmed.joinToString("|")).apply()
    }

    fun clearRecentApps() {
        _recentAppKeys.value = emptyList()
        prefs.edit().remove(keyRecentApps).apply()
    }

    fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val packageManager = getApplication<Application>().packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(0L)
                )
            } else {
                packageManager.queryIntentActivities(intent, 0)
            }

            val appList = resolveInfos.mapNotNull { resolveInfo ->
                val label = resolveInfo.loadLabel(packageManager).toString().trim()
                val packageName = resolveInfo.activityInfo?.packageName
                val activityName = resolveInfo.activityInfo?.name

                if (label.isNotEmpty() && !packageName.isNullOrEmpty() && !activityName.isNullOrEmpty()) {
                    AppInfo(
                        label = label,
                        packageName = packageName,
                        activityName = activityName
                    )
                } else {
                    null
                }
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

            withContext(Dispatchers.Main) {
                _rawApps.value = appList
            }
        }
    }

    private fun registerPackageReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        getApplication<Application>().registerReceiver(packageReceiver, filter)
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(packageReceiver)
        } catch (_: Exception) {
            // Ignored if receiver wasn't registered
        }
    }
}
