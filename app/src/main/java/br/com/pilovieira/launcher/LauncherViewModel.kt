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
    private val keyClipboardAlertEnabled = "key_clipboard_alert_enabled"
    private val keyListDensity = "key_list_density"
    private val keySortedListDensity = "key_sorted_list_density"
    private val keyUnsortedListDensity = "key_unsorted_list_density"
    private val keySortByUsage = "key_sort_by_usage"
    private val keyFeaturedSortAlphabetical = "key_featured_sort_alphabetical"
    private val keyAutoHideUnusedApps = "key_auto_hide_unused_apps"
    private val keyAutoHiddenAppKeys = "key_auto_hidden_app_keys"
    private val customLabelPrefix = "label_"
    private val openCountPrefix = "open_count_"
    private val recentTimePrefix = "recent_time_"
    private val usageScorePrefix = "usage_score_"
    private val maxRecentApps = 15
    private val recentAppsWindowMs = 24L * 60 * 60 * 1000

    // Usage weight halves every 2 days since an app's last open, so recent habits
    // outweigh old ones instead of a lifetime open count dominating forever.
    private val usageHalfLifeDays = 2.0

    // If an app goes 3 days without being opened, its usage counter is wiped outright,
    // so it drops back into the "never used" group at the bottom of the app list.
    private val usageResetAfterMs = 3L * 24 * 60 * 60 * 1000

    // When auto-hide is enabled, an app that goes this long without being opened is
    // automatically moved into the hidden apps list.
    private val autoHideAfterMs = 7L * 24 * 60 * 60 * 1000

    private val _rawApps = MutableStateFlow<List<AppInfo>>(emptyList())

    private val _hiddenAppKeys = MutableStateFlow<Set<String>>(loadHiddenAppKeys())
    val hiddenAppKeys: StateFlow<Set<String>> = _hiddenAppKeys.asStateFlow()

    // Subset of `_hiddenAppKeys` that was hidden by the auto-hide feature rather than by
    // the user directly, so opening one of these apps again can un-hide it automatically.
    private val _autoHiddenAppKeys = MutableStateFlow<Set<String>>(loadAutoHiddenAppKeys())

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

    // Whether pasting text on the Pilfy web app should pop an on-device modal here.
    private val _clipboardAlertEnabled = MutableStateFlow(prefs.getBoolean(keyClipboardAlertEnabled, false))
    val clipboardAlertEnabled: StateFlow<Boolean> = _clipboardAlertEnabled.asStateFlow()

    private val _listDensity = MutableStateFlow(loadListDensity())
    val listDensity: StateFlow<ListDensity> = _listDensity.asStateFlow()

    // Separate list sizes for the usage-sorted app list: one for the "used" apps above
    // the separator, another for the never-used apps below it.
    private val _sortedListDensity = MutableStateFlow(loadListDensity(keySortedListDensity))
    val sortedListDensity: StateFlow<ListDensity> = _sortedListDensity.asStateFlow()

    private val _unsortedListDensity = MutableStateFlow(loadListDensity(keyUnsortedListDensity))
    val unsortedListDensity: StateFlow<ListDensity> = _unsortedListDensity.asStateFlow()

    private val _sortByUsageEnabled = MutableStateFlow(prefs.getBoolean(keySortByUsage, false))
    val sortByUsageEnabled: StateFlow<Boolean> = _sortByUsageEnabled.asStateFlow()

    // Whether the "featured" (used) apps at the top of the list are ordered alphabetically
    // instead of by their usage weight (most-accessed first).
    private val _featuredSortAlphabetical = MutableStateFlow(prefs.getBoolean(keyFeaturedSortAlphabetical, false))
    val featuredSortAlphabetical: StateFlow<Boolean> = _featuredSortAlphabetical.asStateFlow()

    // Automatically hides apps that haven't been opened in over `autoHideAfterMs`.
    private val _autoHideUnusedEnabled = MutableStateFlow(prefs.getBoolean(keyAutoHideUnusedApps, false))
    val autoHideUnusedEnabled: StateFlow<Boolean> = _autoHideUnusedEnabled.asStateFlow()

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
    // a usage weight that decays over time so recently-used apps outrank stale ones. The
    // featured (used) group can instead be ordered alphabetically via `featuredSortAlphabetical`.
    val apps: StateFlow<List<AppInfo>> = combine(
        allApps,
        _hiddenAppKeys,
        usageData,
        _sortByUsageEnabled,
        _featuredSortAlphabetical
    ) { all, hidden, usage, sortByUsage, featuredAlphabetical ->
        val visible = all.filter { app -> !hidden.contains(app.key) }
        if (sortByUsage) {
            val now = System.currentTimeMillis()
            visible.sortedWith(
                compareByDescending<AppInfo> { app ->
                    val lastOpen = usage.lastOpenTimestamps[app.key]
                    val score = usage.scores[app.key]
                    when {
                        lastOpen == null || score == null -> 0.0
                        featuredAlphabetical -> 1.0
                        else -> decayedScore(score, lastOpen, now)
                    }
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
        purgeStaleUsageStats()
        autoHideStaleApps()
        registerPackageReceiver()
    }

    private fun loadHiddenAppKeys(): Set<String> {
        return prefs.getStringSet(keyHiddenApps, emptySet())?.toSet() ?: emptySet()
    }

    private fun loadAutoHiddenAppKeys(): Set<String> {
        return prefs.getStringSet(keyAutoHiddenAppKeys, emptySet())?.toSet() ?: emptySet()
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
    // halving every `usageHalfLifeDays` days so recent opens outweigh older ones.
    private fun decayedScore(score: Double, lastEventMs: Long, nowMs: Long): Double {
        val elapsedDays = (nowMs - lastEventMs).coerceAtLeast(0) / (24.0 * 60 * 60 * 1000)
        return score * Math.pow(0.5, elapsedDays / usageHalfLifeDays)
    }

    // Wipes the usage counter/score of any app that hasn't been opened in `usageResetAfterMs`,
    // so it falls back into the "never used" group instead of lingering at the top forever.
    fun purgeStaleUsageStats() {
        val now = System.currentTimeMillis()
        val staleKeys = _openCounts.value.keys.filter { key ->
            val lastOpen = _recentAppTimestamps.value[key]
            lastOpen == null || (now - lastOpen) > usageResetAfterMs
        }
        if (staleKeys.isEmpty()) return

        val editor = prefs.edit()
        staleKeys.forEach { key ->
            editor.remove(openCountPrefix + key)
            editor.remove(usageScorePrefix + key)
        }
        editor.apply()

        _openCounts.value = _openCounts.value - staleKeys.toSet()
        _usageScores.value = _usageScores.value - staleKeys.toSet()
    }

    // When auto-hide is enabled, moves any app that hasn't been opened in over
    // `autoHideAfterMs` into the hidden apps list. Apps that have never been opened at
    // all are left alone, since there is no "last used" time to measure staleness from.
    fun autoHideStaleApps() {
        if (!_autoHideUnusedEnabled.value) return

        val now = System.currentTimeMillis()
        val staleKeys = _recentAppTimestamps.value
            .filter { (key, lastOpen) -> (now - lastOpen) > autoHideAfterMs && key !in _hiddenAppKeys.value }
            .keys
        if (staleKeys.isEmpty()) return

        val updatedHidden = _hiddenAppKeys.value + staleKeys
        _hiddenAppKeys.value = updatedHidden
        val updatedAutoHidden = _autoHiddenAppKeys.value + staleKeys
        _autoHiddenAppKeys.value = updatedAutoHidden
        prefs.edit()
            .putStringSet(keyHiddenApps, updatedHidden)
            .putStringSet(keyAutoHiddenAppKeys, updatedAutoHidden)
            .apply()
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

    private fun loadListDensity(key: String = keyListDensity): ListDensity {
        val stored = prefs.getString(key, null) ?: return ListDensity.NORMAL
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

    fun setSortedListDensity(density: ListDensity) {
        _sortedListDensity.value = density
        prefs.edit().putString(keySortedListDensity, density.name).apply()
    }

    fun setUnsortedListDensity(density: ListDensity) {
        _unsortedListDensity.value = density
        prefs.edit().putString(keyUnsortedListDensity, density.name).apply()
    }

    fun setSortByUsageEnabled(enabled: Boolean) {
        _sortByUsageEnabled.value = enabled
        prefs.edit().putBoolean(keySortByUsage, enabled).apply()
    }

    fun setFeaturedSortAlphabetical(enabled: Boolean) {
        _featuredSortAlphabetical.value = enabled
        prefs.edit().putBoolean(keyFeaturedSortAlphabetical, enabled).apply()
    }

    fun setAutoHideUnusedEnabled(enabled: Boolean) {
        _autoHideUnusedEnabled.value = enabled
        prefs.edit().putBoolean(keyAutoHideUnusedApps, enabled).apply()
        if (enabled) {
            autoHideStaleApps()
        }
    }

    fun clearUsageStats() {
        val editor = prefs.edit()
        _openCounts.value.keys.forEach { key -> editor.remove(openCountPrefix + key) }
        _usageScores.value.keys.forEach { key -> editor.remove(usageScorePrefix + key) }
        editor.apply()
        _openCounts.value = emptyMap()
        _usageScores.value = emptyMap()
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

    fun setClipboardAlertEnabled(enabled: Boolean) {
        _clipboardAlertEnabled.value = enabled
        prefs.edit().putBoolean(keyClipboardAlertEnabled, enabled).apply()
        if (enabled) {
            br.com.pilovieira.launcher.clipboard.ClipboardAlertService.start(getApplication())
        } else {
            br.com.pilovieira.launcher.clipboard.ClipboardAlertService.stop(getApplication())
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

        // A direct visibility change from the user overrides auto-hide bookkeeping,
        // whichever direction it goes.
        val updatedAutoHidden = _autoHiddenAppKeys.value - app.key
        _autoHiddenAppKeys.value = updatedAutoHidden

        prefs.edit()
            .putStringSet(keyHiddenApps, updated)
            .putStringSet(keyAutoHiddenAppKeys, updatedAutoHidden)
            .apply()
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
        if (app.key in _autoHiddenAppKeys.value) {
            val updatedHidden = _hiddenAppKeys.value - app.key
            val updatedAutoHidden = _autoHiddenAppKeys.value - app.key
            _hiddenAppKeys.value = updatedHidden
            _autoHiddenAppKeys.value = updatedAutoHidden
            prefs.edit()
                .putStringSet(keyHiddenApps, updatedHidden)
                .putStringSet(keyAutoHiddenAppKeys, updatedAutoHidden)
                .apply()
        }

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
            ),
            AppInfo(
                label = context.getString(R.string.clipboard),
                packageName = context.packageName,
                activityName = "br.com.pilovieira.launcher.clipboard.ClipboardActivity"
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
