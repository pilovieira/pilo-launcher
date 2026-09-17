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
    private val keySortByUsage = "key_sort_by_usage"
    private val customLabelPrefix = "label_"
    private val openCountPrefix = "open_count_"
    private val recentTimePrefix = "recent_time_"
    private val usageScorePrefix = "usage_score_"
    private val maxRecentApps = 15
    private val recentAppsWindowMs = 24L * 60 * 60 * 1000

    // Usage weight halves every 7 days since an app's last open, so recent habits
    // outweigh old ones instead of a lifetime open count dominating forever.
    private val usageHalfLifeDays = 7.0

    private val _rawApps = MutableStateFlow<List<AppInfo>>(emptyList())

    private val _hiddenAppKeys = MutableStateFlow<Set<String>>(loadHiddenAppKeys())
    val hiddenAppKeys: StateFlow<Set<String>> = _hiddenAppKeys.asStateFlow()

    private val _customLabels = MutableStateFlow<Map<String, String>>(loadCustomLabels())
    val customLabels: StateFlow<Map<String, String>> = _customLabels.asStateFlow()

    private val _recentAppKeys = MutableStateFlow<List<String>>(loadRecentAppKeys())
    private val _recentAppTimestamps = MutableStateFlow<Map<String, Long>>(loadRecentAppTimestamps())

    private val _openCounts = MutableStateFlow<Map<String, Int>>(loadOpenCounts())
    val openCounts: StateFlow<Map<String, Int>> = _openCounts.asStateFlow()

    private val _usageScores = MutableStateFlow<Map<String, Double>>(loadUsageScores())

    private val _clockStyle = MutableStateFlow(loadClockStyle())
    val clockStyle: StateFlow<ClockStyle> = _clockStyle.asStateFlow()

    private val _lockScreenEnabled = MutableStateFlow(prefs.getBoolean(keyLockScreenEnabled, false))
    val lockScreenEnabled: StateFlow<Boolean> = _lockScreenEnabled.asStateFlow()

    private val _listDensity = MutableStateFlow(loadListDensity())
    val listDensity: StateFlow<ListDensity> = _listDensity.asStateFlow()

    private val _sortByUsageEnabled = MutableStateFlow(prefs.getBoolean(keySortByUsage, false))
    val sortByUsageEnabled: StateFlow<Boolean> = _sortByUsageEnabled.asStateFlow()

    // All installed apps, with any custom labels applied and re-sorted.
    val allApps: StateFlow<List<AppInfo>> = combine(_rawApps, _customLabels) { raw, labels ->
        raw.map { app ->
            val customLabel = labels[app.key]
            if (customLabel != null) app.copy(label = customLabel) else app
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private data class UsageData(
        val counts: Map<String, Int>,
        val scores: Map<String, Double>,
        val lastOpenTimestamps: Map<String, Long>
    )

    private val usageData: StateFlow<UsageData> = combine(
        _openCounts,
        _usageScores,
        _recentAppTimestamps
    ) { counts, scores, timestamps ->
        UsageData(counts, scores, timestamps)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UsageData(emptyMap(), emptyMap(), emptyMap()))

    // Apps displayed on the launcher screen (only non-hidden apps), optionally sorted by
    // a usage weight that decays over time so recently-used apps outrank stale ones.
    val apps: StateFlow<List<AppInfo>> = combine(
        allApps,
        _hiddenAppKeys,
        usageData,
        _sortByUsageEnabled
    ) { all, hidden, usage, sortByUsage ->
        val visible = all.filter { app -> !hidden.contains(app.key) }
        if (sortByUsage) {
            val now = System.currentTimeMillis()
            visible.sortedWith(
                compareByDescending<AppInfo> { app ->
                    val lastOpen = usage.lastOpenTimestamps[app.key]
                    val score = usage.scores[app.key]
                    if (lastOpen == null || score == null) 0.0 else decayedScore(score, lastOpen, now)
                }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
            )
        } else {
            visible
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Apps opened in the last 24 hours, most recent first.
    val recentApps: StateFlow<List<AppInfo>> = combine(
        allApps,
        _recentAppKeys,
        _recentAppTimestamps
    ) { all, recentKeys, timestamps ->
        val cutoff = System.currentTimeMillis() - recentAppsWindowMs
        val byKey = all.associateBy { it.key }
        recentKeys
            .filter { key -> (timestamps[key] ?: 0L) >= cutoff }
            .mapNotNull { byKey[it] }
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

    private fun loadRecentAppTimestamps(): Map<String, Long> {
        return prefs.all
            .filterKeys { it.startsWith(recentTimePrefix) }
            .mapKeys { it.key.removePrefix(recentTimePrefix) }
            .mapNotNull { (key, value) -> (value as? Long)?.let { key to it } }
            .toMap()
    }

    private fun loadUsageScores(): Map<String, Double> {
        return prefs.all
            .filterKeys { it.startsWith(usageScorePrefix) }
            .mapKeys { it.key.removePrefix(usageScorePrefix) }
            .mapNotNull { (key, value) -> (value as? Float)?.let { key to it.toDouble() } }
            .toMap()
    }

    // Exponentially decays a usage score from the time it was recorded up to `nowMs`,
    // halving every `usageHalfLifeDays` days so older activity fades but never vanishes.
    private fun decayedScore(score: Double, lastEventMs: Long, nowMs: Long): Double {
        val elapsedDays = (nowMs - lastEventMs).coerceAtLeast(0) / (24.0 * 60 * 60 * 1000)
        return score * Math.pow(0.5, elapsedDays / usageHalfLifeDays)
    }

    private fun loadOpenCounts(): Map<String, Int> {
        return prefs.all
            .filterKeys { it.startsWith(openCountPrefix) }
            .mapKeys { it.key.removePrefix(openCountPrefix) }
            .mapNotNull { (key, value) -> (value as? Int)?.let { key to it } }
            .toMap()
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

    fun setSortByUsageEnabled(enabled: Boolean) {
        _sortByUsageEnabled.value = enabled
        prefs.edit().putBoolean(keySortByUsage, enabled).apply()
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

        val now = System.currentTimeMillis()
        val previousOpen = _recentAppTimestamps.value[app.key]
        val updatedTimestamps = _recentAppTimestamps.value.toMutableMap()
        updatedTimestamps[app.key] = now
        _recentAppTimestamps.value = updatedTimestamps
        prefs.edit().putLong(recentTimePrefix + app.key, now).apply()

        val updatedCounts = _openCounts.value.toMutableMap()
        val newCount = (updatedCounts[app.key] ?: 0) + 1
        updatedCounts[app.key] = newCount
        _openCounts.value = updatedCounts
        prefs.edit().putInt(openCountPrefix + app.key, newCount).apply()

        val previousScore = _usageScores.value[app.key] ?: 0.0
        val decayedPrevious = if (previousOpen != null) {
            decayedScore(previousScore, previousOpen, now)
        } else {
            previousScore
        }
        val newScore = decayedPrevious + 1.0
        val updatedScores = _usageScores.value.toMutableMap()
        updatedScores[app.key] = newScore
        _usageScores.value = updatedScores
        prefs.edit().putFloat(usageScorePrefix + app.key, newScore.toFloat()).apply()
    }

    fun clearRecentApps() {
        val editor = prefs.edit().remove(keyRecentApps)
        _recentAppKeys.value.forEach { key -> editor.remove(recentTimePrefix + key) }
        editor.apply()
        _recentAppKeys.value = emptyList()
        _recentAppTimestamps.value = emptyMap()
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
            }.plus(builtInApps())
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

            withContext(Dispatchers.Main) {
                _rawApps.value = appList
            }
        }
    }

    // Games and other screens built into the launcher itself, shown alongside installed apps.
    private fun builtInApps(): List<AppInfo> {
        val context = getApplication<Application>()
        return listOf(
            AppInfo(
                label = context.getString(R.string.snake_game),
                packageName = context.packageName,
                activityName = "br.com.pilovieira.launcher.snake.SnakeActivity"
            )
        )
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
