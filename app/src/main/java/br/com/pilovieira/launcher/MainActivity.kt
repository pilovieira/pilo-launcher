package br.com.pilovieira.launcher

import android.app.role.RoleManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import java.util.Calendar

private const val CAR_MODE_WIDGET_HOST_ID = 8842

enum class Screen {
    HOME,
    LAUNCHER,
    RECENTS,
    SETTINGS,
    APP_VISIBILITY,
    HIDDEN_APPS,
    RENAME_APPS,
    CAR_MODE,
    WEATHER_DETAILS
}

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    private var currentScreen by mutableStateOf(Screen.LAUNCHER)
    private var isDefault by mutableStateOf(false)
    private var isFocusMode by mutableStateOf(false)
    private var hasNotificationAccess by mutableStateOf(false)
    private var hasUsageAccess by mutableStateOf(false)
    private var carModeEnabled by mutableStateOf(false)
    private var autoCarModeEnabled by mutableStateOf(false)
    private var weather by mutableStateOf<WeatherData?>(null)
    private var weatherRefreshing by mutableStateOf(false)
    private var weatherDetails by mutableStateOf<WeatherDetails?>(null)
    private var weatherDetailsLoading by mutableStateOf(false)
    private var autoCarDevices by mutableStateOf<List<CarModeBluetoothDevice>>(emptyList())
    private val carModeGridSize = CarModeGridSize.TWO_BY_THREE
    private var carModeRows by mutableStateOf<List<CarModeRowConfig>>(emptyList())

    private val appWidgetManager by lazy { AppWidgetManager.getInstance(this) }
    private val appWidgetHost by lazy { AppWidgetHost(this, CAR_MODE_WIDGET_HOST_ID) }
    private var pendingWidgetBind: PendingWidgetBind? = null

    private data class PendingWidgetBind(
        val rowIndex: Int,
        val column: Int,
        val appWidgetId: Int,
        val configure: ComponentName?
    )

    private val bindWidgetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val pending = pendingWidgetBind
        pendingWidgetBind = null
        if (pending == null) return@registerForActivityResult
        if (result.resultCode == RESULT_OK) {
            proceedAfterWidgetBindAllowed(pending)
        } else {
            runCatching { appWidgetHost.deleteAppWidgetId(pending.appWidgetId) }
        }
    }

    private val configureWidgetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val pending = pendingWidgetBind
        pendingWidgetBind = null
        if (pending == null) return@registerForActivityResult
        if (result.resultCode == RESULT_OK) {
            finalizeWidgetBind(pending)
        } else {
            runCatching { appWidgetHost.deleteAppWidgetId(pending.appWidgetId) }
        }
    }

    private val roleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isDefault = isDefaultLauncher()
    }

    private val notificationPolicyLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (FocusModeHelper.hasNotificationPolicyAccess(this)) {
            FocusModeHelper.setFocusModeEnabled(this, true)
            isFocusMode = true
        } else {
            Toast.makeText(this, getString(R.string.permission_needed_focus_mode), Toast.LENGTH_SHORT).show()
            isFocusMode = FocusModeHelper.isFocusModeEnabled(this)
        }
    }

    private val postNotificationsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.setLockScreenEnabled(true)
    }

    private val bluetoothConnectLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            autoCarModeEnabled = true
            CarModePrefs.setAutoEnabled(this, true)
        } else {
            Toast.makeText(this, getString(R.string.permission_needed_auto_car_mode), Toast.LENGTH_SHORT).show()
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            refreshWeather()
        }
    }

    private fun refreshWeather(force: Boolean = false) {
        val cached = WeatherHelper.getCached(this)
        if (cached != null) weather = cached
        if (!force && !WeatherHelper.isStale(cached)) return
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (weatherRefreshing) return
        weatherRefreshing = true
        lifecycleScope.launch {
            val location = WeatherHelper.getLastKnownLocation(this@MainActivity)
                ?: WeatherHelper.requestFreshLocation(this@MainActivity)
            if (location != null) {
                val fetched = WeatherHelper.fetchWeather(this@MainActivity, location.first, location.second)
                if (fetched != null) weather = fetched
            }
            weatherRefreshing = false
        }
    }

    private fun openWeatherDetails() {
        currentScreen = Screen.WEATHER_DETAILS
        if (weatherDetailsLoading) return
        weatherDetailsLoading = true
        lifecycleScope.launch {
            val cachedLocation = weather?.let { it.latitude to it.longitude }
            val location = cachedLocation
                ?: WeatherHelper.getLastKnownLocation(this@MainActivity)
                ?: WeatherHelper.requestFreshLocation(this@MainActivity)
            if (location != null) {
                val details = WeatherHelper.fetchWeatherDetails(this@MainActivity, location.first, location.second)
                if (details != null) weatherDetails = details
            }
            weatherDetailsLoading = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        isDefault = isDefaultLauncher()
        isFocusMode = FocusModeHelper.isFocusModeEnabled(this)
        hasNotificationAccess = NotificationAccessHelper.isNotificationListenerEnabled(this)
        hasUsageAccess = UsageStatsHelper.hasUsageAccess(this)
        carModeEnabled = CarModePrefs.isEnabled(this)
        autoCarModeEnabled = CarModePrefs.isAutoEnabled(this)
        autoCarDevices = CarModePrefs.getAutoDevices(this)
        carModeRows = CarModePrefs.loadRows(this, carModeGridSize)
        if (carModeEnabled) {
            currentScreen = Screen.CAR_MODE
        }

        weather = WeatherHelper.getCached(this)
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            refreshWeather()
        } else {
            locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        setContent {
            val apps by viewModel.apps.collectAsState()
            val allApps by viewModel.allApps.collectAsState()
            val hiddenAppKeys by viewModel.hiddenAppKeys.collectAsState()
            val recentApps by viewModel.recentApps.collectAsState()
            val clockStyle by viewModel.clockStyle.collectAsState()
            val lockScreenEnabled by viewModel.lockScreenEnabled.collectAsState()
            val listDensity by viewModel.listDensity.collectAsState()

            MaterialTheme(colorScheme = darkColorScheme()) {
            when (currentScreen) {
                Screen.HOME -> {
                    BackHandler {
                        // Home screen: back button does nothing.
                    }
                    HomeScreen(
                        isFocusMode = isFocusMode,
                        clockStyle = clockStyle,
                        weather = weather,
                        weatherRefreshing = weatherRefreshing,
                        onRefreshWeather = { refreshWeather(force = true) },
                        onOpenWeatherDetails = { openWeatherDetails() },
                        onSwipeUp = { currentScreen = Screen.LAUNCHER },
                        onSwipeDown = { currentScreen = Screen.RECENTS }
                    )
                }
                Screen.WEATHER_DETAILS -> {
                    BackHandler {
                        currentScreen = Screen.HOME
                    }
                    WeatherDetailsScreen(
                        details = weatherDetails,
                        loading = weatherDetailsLoading,
                        onBackClick = { currentScreen = Screen.HOME }
                    )
                }
                Screen.LAUNCHER -> {
                    BackHandler {
                        currentScreen = Screen.HOME
                    }
                    LauncherScreen(
                        apps = apps,
                        onAppClick = { app ->
                            launchApp(app)
                        },
                        onOpenSettings = {
                            currentScreen = Screen.SETTINGS
                        },
                        isDefaultLauncher = isDefault,
                        onSetDefaultClick = {
                            requestSetDefaultLauncher()
                        },
                        hiddenAppsCount = hiddenAppKeys.size,
                        onOpenHiddenApps = {
                            currentScreen = Screen.HIDDEN_APPS
                        },
                        onOpenRecentApps = {
                            currentScreen = Screen.RECENTS
                        },
                        onOpenCarMode = {
                            enableCarMode()
                        },
                        listDensity = listDensity
                    )
                }
                Screen.CAR_MODE -> {
                    BackHandler {
                        // Car Mode replaces Home: back button does nothing.
                    }
                    CarModeScreen(
                        apps = apps,
                        rows = carModeRows,
                        gridSize = carModeGridSize,
                        appWidgetHost = appWidgetHost,
                        appWidgetManager = appWidgetManager,
                        onAppClick = { app ->
                            launchApp(app)
                        },
                        onExitCarMode = {
                            disableCarMode()
                        },
                        onAssignApp = { rowIndex, column, appKey ->
                            assignAppToCarModeSlot(rowIndex, column, appKey)
                        },
                        onClearSlot = { rowIndex, column ->
                            clearCarModeSlot(rowIndex, column)
                        },
                        onPickWidget = { rowIndex, column, isWide, provider ->
                            startCarModeWidgetBind(rowIndex, column, isWide, provider)
                        },
                        onSplitRow = { rowIndex ->
                            setCarModeRowWide(rowIndex, false)
                        }
                    )
                }
                Screen.RECENTS -> {
                    BackHandler {
                        currentScreen = Screen.LAUNCHER
                    }
                    RecentAppsScreen(
                        recentApps = recentApps,
                        onAppClick = { app ->
                            launchApp(app)
                        },
                        onClearClick = {
                            viewModel.clearRecentApps()
                        },
                        onBackClick = {
                            currentScreen = Screen.LAUNCHER
                        },
                        listDensity = listDensity
                    )
                }
                Screen.SETTINGS -> {
                    BackHandler {
                        currentScreen = Screen.LAUNCHER
                    }
                    SettingsScreen(
                        isFocusMode = isFocusMode,
                        onFocusModeChange = { enabled ->
                            handleFocusModeToggle(enabled)
                        },
                        autoCarModeEnabled = autoCarModeEnabled,
                        onAutoCarModeChange = { enabled ->
                            handleAutoCarModeToggle(enabled)
                        },
                        autoCarDevices = autoCarDevices,
                        onToggleAutoCarDevice = { address, name, selected ->
                            if (selected) {
                                CarModePrefs.addAutoDevice(this@MainActivity, address, name)
                            } else {
                                CarModePrefs.removeAutoDevice(this@MainActivity, address)
                            }
                            autoCarDevices = CarModePrefs.getAutoDevices(this@MainActivity)
                        },
                        currentLanguageTag = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
                            .toLanguageTags()
                            .ifEmpty { null },
                        onLanguageChange = { tag ->
                            setAppLanguage(tag)
                        },
                        isDefaultLauncher = isDefault,
                        onSetDefaultClick = {
                            requestSetDefaultLauncher()
                        },
                        hiddenAppsCount = hiddenAppKeys.size,
                        onOpenAppVisibility = {
                            currentScreen = Screen.APP_VISIBILITY
                        },
                        onOpenRenameApps = {
                            currentScreen = Screen.RENAME_APPS
                        },
                        clockStyle = clockStyle,
                        onClockStyleChange = { style ->
                            viewModel.setClockStyle(style)
                        },
                        listDensity = listDensity,
                        onListDensityChange = { density ->
                            viewModel.setListDensity(density)
                        },
                        lockScreenEnabled = lockScreenEnabled,
                        onLockScreenEnabledChange = { enabled ->
                            handleLockScreenToggle(enabled)
                        },
                        hasNotificationAccess = hasNotificationAccess,
                        onRequestNotificationAccess = {
                            requestNotificationAccess()
                        },
                        hasUsageAccess = hasUsageAccess,
                        onRequestUsageAccess = {
                            UsageStatsHelper.requestUsageAccess(this@MainActivity)
                        },
                        onBackClick = {
                            currentScreen = Screen.LAUNCHER
                        }
                    )
                }
                Screen.APP_VISIBILITY -> {
                    BackHandler {
                        currentScreen = Screen.SETTINGS
                    }
                    AppVisibilityScreen(
                        allApps = allApps,
                        hiddenAppKeys = hiddenAppKeys,
                        onToggleVisibility = { app, isVisible ->
                            viewModel.setAppVisibility(app, isVisible)
                        },
                        onBackClick = {
                            currentScreen = Screen.SETTINGS
                        }
                    )
                }
                Screen.HIDDEN_APPS -> {
                    BackHandler {
                        currentScreen = Screen.LAUNCHER
                    }
                    HiddenAppsScreen(
                        allApps = allApps,
                        hiddenAppKeys = hiddenAppKeys,
                        onAppClick = { app ->
                            launchApp(app)
                        },
                        onBackClick = {
                            currentScreen = Screen.LAUNCHER
                        },
                        listDensity = listDensity
                    )
                }
                Screen.RENAME_APPS -> {
                    BackHandler {
                        currentScreen = Screen.SETTINGS
                    }
                    RenameAppsScreen(
                        allApps = allApps,
                        onRenameApp = { app, newLabel ->
                            viewModel.renameApp(app, newLabel)
                        },
                        onBackClick = {
                            currentScreen = Screen.SETTINGS
                        }
                    )
                }
            }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            currentScreen = if (carModeEnabled) Screen.CAR_MODE else Screen.LAUNCHER
        }
    }

    override fun onResume() {
        super.onResume()
        isDefault = isDefaultLauncher()
        isFocusMode = FocusModeHelper.isFocusModeEnabled(this)
        hasNotificationAccess = NotificationAccessHelper.isNotificationListenerEnabled(this)
        hasUsageAccess = UsageStatsHelper.hasUsageAccess(this)
        if (isFocusMode) {
            FocusModeHelper.applyRingerMode(this)
        }
        viewModel.loadApps()
        refreshWeather()

        // Pick up Car Mode changes made outside this activity (e.g. the Bluetooth receiver).
        val prefsCarModeEnabled = CarModePrefs.isEnabled(this)
        if (prefsCarModeEnabled != carModeEnabled) {
            carModeEnabled = prefsCarModeEnabled
            currentScreen = if (carModeEnabled) Screen.CAR_MODE else Screen.LAUNCHER
        }
    }

    override fun onStart() {
        super.onStart()
        runCatching { appWidgetHost.startListening() }
    }

    override fun onStop() {
        super.onStop()
        runCatching { appWidgetHost.stopListening() }
    }

    private fun emptyCarModeRow(): CarModeRowConfig =
        CarModeRowConfig(wide = false, slots = List(carModeGridSize.columns) { null })

    private fun updateCarModeRow(index: Int, config: CarModeRowConfig) {
        CarModePrefs.saveRow(this, index, config)
        carModeRows = CarModePrefs.loadRows(this, carModeGridSize)
    }

    private fun setCarModeRowWide(rowIndex: Int, wide: Boolean) {
        val current = carModeRows.getOrNull(rowIndex) ?: emptyCarModeRow()
        if (current.wide == wide) return
        current.slots.forEach { content ->
            if (content is CarModeSlotContent.Widget) {
                runCatching { appWidgetHost.deleteAppWidgetId(content.appWidgetId) }
            }
        }
        updateCarModeRow(rowIndex, emptyCarModeRow().copy(wide = wide))
    }

    private fun assignAppToCarModeSlot(rowIndex: Int, column: Int, appKey: String?) {
        if (carModeRows.getOrNull(rowIndex)?.wide == true) {
            setCarModeRowWide(rowIndex, false)
        }
        val row = carModeRows.getOrNull(rowIndex) ?: emptyCarModeRow()
        val newSlots = row.slots.toMutableList()
        if (column in newSlots.indices) {
            newSlots[column] = appKey?.let { CarModeSlotContent.App(it) }
        }
        updateCarModeRow(rowIndex, row.copy(slots = newSlots))
    }

    private fun clearCarModeSlot(rowIndex: Int, column: Int) {
        val current = carModeRows.getOrNull(rowIndex) ?: return
        val existing = current.slots.getOrNull(column)
        if (existing is CarModeSlotContent.Widget) {
            runCatching { appWidgetHost.deleteAppWidgetId(existing.appWidgetId) }
        }
        if (current.wide) {
            updateCarModeRow(rowIndex, emptyCarModeRow())
        } else {
            val newSlots = current.slots.toMutableList()
            if (column in newSlots.indices) newSlots[column] = null
            updateCarModeRow(rowIndex, current.copy(slots = newSlots))
        }
    }

    private fun startCarModeWidgetBind(
        rowIndex: Int,
        column: Int,
        isWide: Boolean,
        provider: AppWidgetProviderInfo
    ) {
        if (isWide) {
            setCarModeRowWide(rowIndex, true)
        } else if (carModeRows.getOrNull(rowIndex)?.wide == true) {
            setCarModeRowWide(rowIndex, false)
        }

        val appWidgetId = appWidgetHost.allocateAppWidgetId()
        val allowed = runCatching {
            appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider.provider)
        }.getOrDefault(false)

        val pending = PendingWidgetBind(rowIndex, column, appWidgetId, provider.configure)
        if (allowed) {
            proceedAfterWidgetBindAllowed(pending)
        } else {
            pendingWidgetBind = pending
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
            }
            bindWidgetLauncher.launch(intent)
        }
    }

    private fun proceedAfterWidgetBindAllowed(pending: PendingWidgetBind) {
        val configure = pending.configure
        if (configure != null) {
            pendingWidgetBind = pending
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pending.appWidgetId)
            }
            val launched = runCatching { configureWidgetLauncher.launch(intent) }.isSuccess
            if (!launched) {
                pendingWidgetBind = null
                finalizeWidgetBind(pending)
            }
        } else {
            finalizeWidgetBind(pending)
        }
    }

    private fun finalizeWidgetBind(pending: PendingWidgetBind) {
        val current = carModeRows.getOrNull(pending.rowIndex) ?: emptyCarModeRow()
        val newSlots = current.slots.toMutableList()
        if (pending.column in newSlots.indices) {
            newSlots[pending.column] = CarModeSlotContent.Widget(pending.appWidgetId)
        }
        updateCarModeRow(pending.rowIndex, current.copy(slots = newSlots))
    }

    private fun enableCarMode() {
        carModeEnabled = true
        CarModePrefs.setEnabled(this, true)
        currentScreen = Screen.CAR_MODE
    }

    private fun disableCarMode() {
        carModeEnabled = false
        CarModePrefs.setEnabled(this, false)
        currentScreen = Screen.LAUNCHER
    }

    private fun setAppLanguage(tag: String?) {
        val localeList = if (tag == null) {
            androidx.core.os.LocaleListCompat.getEmptyLocaleList()
        } else {
            androidx.core.os.LocaleListCompat.forLanguageTags(tag)
        }
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(localeList)
        recreate()
    }

    private fun handleAutoCarModeToggle(enabled: Boolean) {
        if (!enabled) {
            autoCarModeEnabled = false
            CarModePrefs.setAutoEnabled(this, false)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            bluetoothConnectLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
            return
        }

        autoCarModeEnabled = true
        CarModePrefs.setAutoEnabled(this, true)
    }

    private fun handleLockScreenToggle(enabled: Boolean) {
        if (enabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            postNotificationsLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        viewModel.setLockScreenEnabled(enabled)
    }

    private fun requestNotificationAccess() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.could_not_open_notification_settings), Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleFocusModeToggle(enabled: Boolean) {
        if (enabled) {
            if (!FocusModeHelper.hasNotificationPolicyAccess(this)) {
                try {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    notificationPolicyLauncher.launch(intent)
                } catch (_: Exception) {
                    Toast.makeText(this, getString(R.string.could_not_open_notification_settings), Toast.LENGTH_SHORT).show()
                }
                return
            }
            FocusModeHelper.setFocusModeEnabled(this, true)
            isFocusMode = true
        } else {
            FocusModeHelper.setFocusModeEnabled(this, false)
            isFocusMode = false
        }
    }

    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == packageName
    }

    private fun requestSetDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                    roleRequestLauncher.launch(intent)
                    return
                }
            }
        }

        try {
            val intent = Intent(Settings.ACTION_HOME_SETTINGS)
            startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                startActivity(intent)
            } catch (_: Exception) {
                val intent = Intent(Settings.ACTION_SETTINGS)
                startActivity(intent)
            }
        }
    }

    private fun launchApp(app: AppInfo) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(app.packageName, app.activityName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            startActivity(intent)
            viewModel.recordAppOpened(app)
            currentScreen = if (carModeEnabled) Screen.CAR_MODE else Screen.HOME
        } catch (e: Exception) {
            val fallbackIntent = packageManager.getLaunchIntentForPackage(app.packageName)
            if (fallbackIntent != null) {
                startActivity(fallbackIntent)
                viewModel.recordAppOpened(app)
                currentScreen = if (carModeEnabled) Screen.CAR_MODE else Screen.HOME
            } else {
                Toast.makeText(this, getString(R.string.could_not_open_app, app.label), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun HomeScreen(
    isFocusMode: Boolean,
    clockStyle: ClockStyle,
    weather: WeatherData?,
    weatherRefreshing: Boolean,
    onRefreshWeather: () -> Unit,
    onOpenWeatherDetails: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .pointerInput(Unit) {
                var totalDrag = 0f
                detectVerticalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        totalDrag += dragAmount
                    },
                    onDragEnd = {
                        val threshold = 80f
                        if (totalDrag < -threshold) {
                            onSwipeUp()
                        } else if (totalDrag > threshold) {
                            onSwipeDown()
                        }
                    }
                )
            }
    ) {
        when (clockStyle) {
            ClockStyle.ANALOG -> AnalogClock(modifier = Modifier.size(220.dp).align(Alignment.Center))
            ClockStyle.DIGITAL -> Box(modifier = Modifier.align(Alignment.Center)) { DigitalClock() }
        }

        if (isFocusMode) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Color.White, shape = androidx.compose.foundation.shape.CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.focus_mode_on),
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (weather != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier.clickable(onClick = onOpenWeatherDetails),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WeatherIcon(code = weather.weatherCode, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.weather_temperature, weather.temperatureC.toInt()),
                            color = Color(0xFFAAAAAA),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .alpha(if (weatherRefreshing) 0.4f else 1f)
                            .clickable(enabled = !weatherRefreshing, onClick = onRefreshWeather),
                        contentAlignment = Alignment.Center
                    ) {
                        RefreshIcon(modifier = Modifier.size(18.dp))
                    }
                }
                if (weather.cityName != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = weather.cityName,
                        color = Color(0xFF777777),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun AnalogClock(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(Calendar.getInstance()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = Calendar.getInstance()
            kotlinx.coroutines.delay(1000)
        }
    }

    val hour = now.get(Calendar.HOUR)
    val minute = now.get(Calendar.MINUTE)
    val second = now.get(Calendar.SECOND)

    Canvas(modifier = modifier) {
        val radius = min(size.width, size.height) / 2f
        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)

        // Clock face
        drawCircle(
            color = Color.White,
            radius = radius,
            center = center,
            style = Stroke(width = 3f)
        )

        // Hour ticks
        for (i in 0 until 12) {
            val angle = Math.toRadians((i * 30 - 90).toDouble())
            val outer = androidx.compose.ui.geometry.Offset(
                x = center.x + radius * cos(angle).toFloat(),
                y = center.y + radius * sin(angle).toFloat()
            )
            val inner = androidx.compose.ui.geometry.Offset(
                x = center.x + (radius - 12f) * cos(angle).toFloat(),
                y = center.y + (radius - 12f) * sin(angle).toFloat()
            )
            drawLine(
                color = Color.White,
                start = inner,
                end = outer,
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
        }

        // Hour hand
        val hourAngle = Math.toRadians(((hour % 12) * 30 + minute * 0.5 - 90).toDouble())
        drawLine(
            color = Color.White,
            start = center,
            end = androidx.compose.ui.geometry.Offset(
                x = center.x + radius * 0.5f * cos(hourAngle).toFloat(),
                y = center.y + radius * 0.5f * sin(hourAngle).toFloat()
            ),
            strokeWidth = 6f,
            cap = StrokeCap.Round
        )

        // Minute hand
        val minuteAngle = Math.toRadians((minute * 6 - 90).toDouble())
        drawLine(
            color = Color.White,
            start = center,
            end = androidx.compose.ui.geometry.Offset(
                x = center.x + radius * 0.75f * cos(minuteAngle).toFloat(),
                y = center.y + radius * 0.75f * sin(minuteAngle).toFloat()
            ),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )

        // Second hand
        val secondAngle = Math.toRadians((second * 6 - 90).toDouble())
        drawLine(
            color = Color(0xFFAAAAAA),
            start = center,
            end = androidx.compose.ui.geometry.Offset(
                x = center.x + radius * 0.85f * cos(secondAngle).toFloat(),
                y = center.y + radius * 0.85f * sin(secondAngle).toFloat()
            ),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )

        // Center dot
        drawCircle(color = Color.White, radius = 6f, center = center)
    }
}

@Composable
fun DigitalClock(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(Calendar.getInstance()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = Calendar.getInstance()
            kotlinx.coroutines.delay(1000)
        }
    }

    val hour = now.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
    val minute = now.get(Calendar.MINUTE).toString().padStart(2, '0')

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(
            text = "$hour:$minute",
            color = Color.White,
            fontSize = 56.sp,
            fontWeight = FontWeight.Light
        )
    }
}

@Composable
fun SearchIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.11f
        val lensRadius = size.minDimension * 0.34f
        val lensCenter = androidx.compose.ui.geometry.Offset(
            x = lensRadius + strokeWidth / 2f,
            y = lensRadius + strokeWidth / 2f
        )

        drawCircle(
            color = Color.White,
            radius = lensRadius,
            center = lensCenter,
            style = Stroke(width = strokeWidth)
        )

        val handleAngle = Math.toRadians(45.0)
        val handleStart = androidx.compose.ui.geometry.Offset(
            x = lensCenter.x + (lensRadius + strokeWidth * 0.2f) * cos(handleAngle).toFloat(),
            y = lensCenter.y + (lensRadius + strokeWidth * 0.2f) * sin(handleAngle).toFloat()
        )
        val handleEnd = androidx.compose.ui.geometry.Offset(
            x = size.width,
            y = size.height
        )

        drawLine(
            color = Color.White,
            start = handleStart,
            end = handleEnd,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun ClearIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.12f
        val inset = size.minDimension * 0.08f

        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(inset, inset),
            end = androidx.compose.ui.geometry.Offset(size.width - inset, size.height - inset),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(size.width - inset, inset),
            end = androidx.compose.ui.geometry.Offset(inset, size.height - inset),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun SettingsIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        val outerRadius = size.minDimension / 2f
        val innerRadius = outerRadius * 0.55f
        val toothLength = outerRadius * 0.22f
        val toothCount = 8

        for (i in 0 until toothCount) {
            val angle = Math.toRadians((i * (360.0 / toothCount)))
            val start = androidx.compose.ui.geometry.Offset(
                x = center.x + (outerRadius - toothLength) * cos(angle).toFloat(),
                y = center.y + (outerRadius - toothLength) * sin(angle).toFloat()
            )
            val end = androidx.compose.ui.geometry.Offset(
                x = center.x + outerRadius * cos(angle).toFloat(),
                y = center.y + outerRadius * sin(angle).toFloat()
            )
            drawLine(
                color = Color.White,
                start = start,
                end = end,
                strokeWidth = size.minDimension * 0.14f,
                cap = StrokeCap.Round
            )
        }

        drawCircle(
            color = Color.White,
            radius = outerRadius - toothLength * 0.6f,
            center = center,
            style = Stroke(width = size.minDimension * 0.1f)
        )

        drawCircle(
            color = Color.Black,
            radius = innerRadius * 0.55f,
            center = center
        )
    }
}

@Composable
fun RefreshIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2.6f
        val stroke = size.minDimension * 0.14f

        drawArc(
            color = Color.White,
            startAngle = -260f,
            sweepAngle = 260f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )

        val angle = Math.toRadians(-260.0)
        val tip = androidx.compose.ui.geometry.Offset(
            center.x + (radius * cos(angle)).toFloat(),
            center.y + (radius * sin(angle)).toFloat()
        )
        val arrowSize = size.minDimension * 0.22f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(tip.x - arrowSize, tip.y - arrowSize * 0.3f)
            lineTo(tip.x + arrowSize * 0.2f, tip.y)
            lineTo(tip.x - arrowSize * 0.3f, tip.y + arrowSize)
            close()
        }
        drawPath(path, color = Color.White)
    }
}

private enum class WeatherGlyph { CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, RAIN, SNOW, STORM }

private fun weatherGlyphFor(code: Int): WeatherGlyph = when (code) {
    0 -> WeatherGlyph.CLEAR
    1, 2 -> WeatherGlyph.PARTLY_CLOUDY
    3 -> WeatherGlyph.CLOUDY
    45, 48 -> WeatherGlyph.FOG
    51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> WeatherGlyph.RAIN
    71, 73, 75, 77, 85, 86 -> WeatherGlyph.SNOW
    95, 96, 99 -> WeatherGlyph.STORM
    else -> WeatherGlyph.CLOUDY
}

@Composable
fun WeatherIcon(code: Int, modifier: Modifier = Modifier) {
    val glyph = weatherGlyphFor(code)
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        fun drawSun(cx: Float, cy: Float, radius: Float) {
            drawCircle(color = Color.White, radius = radius, center = androidx.compose.ui.geometry.Offset(cx, cy))
            val rayLength = radius * 0.6f
            val rayStroke = radius * 0.24f
            for (i in 0 until 8) {
                val angle = Math.toRadians((i * 45.0))
                val startR = radius * 1.25f
                val endR = startR + rayLength
                drawLine(
                    color = Color.White,
                    start = androidx.compose.ui.geometry.Offset(
                        cx + (startR * cos(angle)).toFloat(),
                        cy + (startR * sin(angle)).toFloat()
                    ),
                    end = androidx.compose.ui.geometry.Offset(
                        cx + (endR * cos(angle)).toFloat(),
                        cy + (endR * sin(angle)).toFloat()
                    ),
                    strokeWidth = rayStroke,
                    cap = StrokeCap.Round
                )
            }
        }

        fun drawCloud(cx: Float, cy: Float, scale: Float) {
            val r1 = h * 0.16f * scale
            val r2 = h * 0.13f * scale
            val r3 = h * 0.11f * scale
            drawCircle(color = Color.White, radius = r1, center = androidx.compose.ui.geometry.Offset(cx, cy))
            drawCircle(
                color = Color.White,
                radius = r2,
                center = androidx.compose.ui.geometry.Offset(cx - r1 * 1.1f, cy + r1 * 0.35f)
            )
            drawCircle(
                color = Color.White,
                radius = r3,
                center = androidx.compose.ui.geometry.Offset(cx + r1 * 1.1f, cy + r1 * 0.4f)
            )
            drawRoundRect(
                color = Color.White,
                topLeft = androidx.compose.ui.geometry.Offset(cx - r1 * 1.5f, cy),
                size = androidx.compose.ui.geometry.Size(r1 * 3f, r1 * 0.9f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r1 * 0.4f, r1 * 0.4f)
            )
        }

        when (glyph) {
            WeatherGlyph.CLEAR -> drawSun(w / 2f, h / 2f, h * 0.22f)
            WeatherGlyph.PARTLY_CLOUDY -> {
                drawSun(w * 0.38f, h * 0.36f, h * 0.16f)
                drawCloud(w * 0.55f, h * 0.6f, 1f)
            }
            WeatherGlyph.CLOUDY, WeatherGlyph.FOG -> {
                drawCloud(w / 2f, h * 0.5f, 1.15f)
                if (glyph == WeatherGlyph.FOG) {
                    val lineY = h * 0.85f
                    drawLine(
                        color = Color.White,
                        start = androidx.compose.ui.geometry.Offset(w * 0.2f, lineY),
                        end = androidx.compose.ui.geometry.Offset(w * 0.8f, lineY),
                        strokeWidth = h * 0.05f,
                        cap = StrokeCap.Round
                    )
                }
            }
            WeatherGlyph.RAIN -> {
                drawCloud(w / 2f, h * 0.4f, 1f)
                val dropStroke = h * 0.06f
                val dropY = h * 0.72f
                val dropLen = h * 0.16f
                for (dx in listOf(-0.16f, 0f, 0.16f)) {
                    drawLine(
                        color = Color.White,
                        start = androidx.compose.ui.geometry.Offset(w * (0.5f + dx), dropY),
                        end = androidx.compose.ui.geometry.Offset(w * (0.5f + dx) - dropLen * 0.3f, dropY + dropLen),
                        strokeWidth = dropStroke,
                        cap = StrokeCap.Round
                    )
                }
            }
            WeatherGlyph.SNOW -> {
                drawCloud(w / 2f, h * 0.4f, 1f)
                val dotRadius = h * 0.035f
                val dotY = h * 0.78f
                for (dx in listOf(-0.16f, 0f, 0.16f)) {
                    drawCircle(
                        color = Color.White,
                        radius = dotRadius,
                        center = androidx.compose.ui.geometry.Offset(w * (0.5f + dx), dotY)
                    )
                }
            }
            WeatherGlyph.STORM -> {
                drawCloud(w / 2f, h * 0.38f, 1f)
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.55f, h * 0.62f)
                    lineTo(w * 0.42f, h * 0.82f)
                    lineTo(w * 0.52f, h * 0.82f)
                    lineTo(w * 0.42f, h * 1.0f)
                    lineTo(w * 0.62f, h * 0.76f)
                    lineTo(w * 0.5f, h * 0.76f)
                    close()
                }
                drawPath(path, color = Color.White)
            }
        }
    }
}

@Composable
fun CarIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = size.minDimension * 0.09f

        // Roof / windshield
        drawRoundRect(
            color = Color.White,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.28f, h * 0.06f),
            size = androidx.compose.ui.geometry.Size(w * 0.44f, h * 0.28f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.1f, w * 0.1f),
            style = Stroke(width = stroke)
        )

        // Body
        drawRoundRect(
            color = Color.White,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.06f, h * 0.32f),
            size = androidx.compose.ui.geometry.Size(w * 0.88f, h * 0.4f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.14f, w * 0.14f),
            style = Stroke(width = stroke)
        )

        // Headlights
        val headlightRadius = size.minDimension * 0.06f
        val headlightY = h * 0.52f
        drawCircle(color = Color.White, radius = headlightRadius, center = androidx.compose.ui.geometry.Offset(w * 0.2f, headlightY))
        drawCircle(color = Color.White, radius = headlightRadius, center = androidx.compose.ui.geometry.Offset(w * 0.8f, headlightY))

        // Wheels peeking out at the bottom
        val wheelWidth = w * 0.16f
        val wheelHeight = h * 0.14f
        val wheelY = h * 0.66f
        drawRoundRect(
            color = Color.White,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.1f, wheelY),
            size = androidx.compose.ui.geometry.Size(wheelWidth, wheelHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.03f, w * 0.03f)
        )
        drawRoundRect(
            color = Color.White,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.74f, wheelY),
            size = androidx.compose.ui.geometry.Size(wheelWidth, wheelHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.03f, w * 0.03f)
        )
    }
}

@Composable
fun LauncherScreen(
    apps: List<AppInfo>,
    onAppClick: (AppInfo) -> Unit,
    onOpenSettings: () -> Unit,
    isDefaultLauncher: Boolean,
    onSetDefaultClick: () -> Unit,
    hiddenAppsCount: Int,
    onOpenHiddenApps: () -> Unit,
    onOpenRecentApps: () -> Unit,
    onOpenCarMode: () -> Unit,
    listDensity: ListDensity,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var appForContextMenu by remember { mutableStateOf<AppInfo?>(null) }
    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) {
            apps
        } else {
            apps.filter { it.label.contains(searchQuery, ignoreCase = true) }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .pointerInput(Unit) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        totalDrag += dragAmount
                    },
                    onDragEnd = {
                        val threshold = 80f
                        if (totalDrag < -threshold) {
                            onOpenHiddenApps()
                        } else if (totalDrag > threshold) {
                            onOpenRecentApps()
                        }
                    }
                )
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(
                            width = 1.dp,
                            color = Color(0xFF333333),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable(onClick = onOpenCarMode),
                    contentAlignment = Alignment.Center
                ) {
                    CarIcon(modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .border(
                            width = 1.dp,
                            color = Color(0xFF333333),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search_apps),
                                color = Color(0xFF777777),
                                fontSize = 13.sp
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    SearchIcon(modifier = Modifier.size(14.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(
                            width = 1.dp,
                            color = Color(0xFF333333),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable(onClick = onOpenSettings),
                    contentAlignment = Alignment.Center
                ) {
                    SettingsIcon(modifier = Modifier.size(16.dp))
                }
            }

            if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_apps_to_show),
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 24.dp,
                        end = 24.dp,
                        top = 8.dp,
                        bottom = if (!isDefaultLauncher) 80.dp else 16.dp
                    )
                ) {
                    items(
                        items = filteredApps,
                        key = { it.key }
                    ) { app ->
                        AppListItem(
                            app = app,
                            onClick = { onAppClick(app) },
                            onLongClick = { appForContextMenu = app },
                            listDensity = listDensity
                        )
                    }

                    // Hidden Apps button (only shown when there are hidden apps)
                    if (hiddenAppsCount > 0) {
                        item(key = "launcher_hidden_apps_item") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onOpenHiddenApps)
                                    .padding(vertical = 14.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.hidden_apps) + " ($hiddenAppsCount)",
                                    color = Color(0xFFAAAAAA),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!isDefaultLauncher) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E1E1E)
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.set_as_default_launcher),
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = onSetDefaultClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = stringResource(R.string.set_default), fontSize = 13.sp)
                    }
                }
            }
        }
    }

    val contextApp = appForContextMenu
    if (contextApp != null) {
        AppContextMenuDialog(
            app = contextApp,
            onDismiss = { appForContextMenu = null }
        )
    }
}

@Composable
fun AppContextMenuDialog(
    app: AppInfo,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = app.label)
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.app_info),
                    fontSize = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            try {
                                val intent = Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.fromParts("package", app.packageName, null)
                                )
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.could_not_open_app_info),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            onDismiss()
                        }
                        .padding(vertical = 12.dp)
                )
                Text(
                    text = stringResource(R.string.uninstall),
                    fontSize = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            try {
                                val intent = Intent(
                                    Intent.ACTION_DELETE,
                                    android.net.Uri.fromParts("package", app.packageName, null)
                                )
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.could_not_uninstall_app),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            onDismiss()
                        }
                        .padding(vertical = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun RecentAppsScreen(
    recentApps: List<AppInfo>,
    onAppClick: (AppInfo) -> Unit,
    onClearClick: () -> Unit,
    onBackClick: () -> Unit,
    listDensity: ListDensity,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val usageTimes = remember {
        val oneDayMs = 24L * 60 * 60 * 1000
        UsageStatsHelper.getUsageTimeByPackage(context, System.currentTimeMillis() - oneDayMs)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .pointerInput(Unit) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        totalDrag += dragAmount
                    },
                    onDragEnd = {
                        val threshold = 80f
                        if (totalDrag < -threshold) {
                            onBackClick()
                        }
                    }
                )
            }
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.back),
                color = Color.Gray,
                fontSize = 16.sp,
                modifier = Modifier.clickable(onClick = onBackClick)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.recent_apps),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (recentApps.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clickable(onClick = onClearClick)
                        .padding(8.dp)
                ) {
                    ClearIcon(modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (recentApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_recent_apps),
                    color = Color.Gray,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(
                    items = recentApps,
                    key = { it.key }
                ) { app ->
                    RecentAppListItem(
                        app = app,
                        usageTimeMillis = usageTimes[app.packageName] ?: 0L,
                        onClick = { onAppClick(app) },
                        listDensity = listDensity
                    )
                }
            }
        }
    }
}

@Composable
fun RecentAppListItem(
    app: AppInfo,
    usageTimeMillis: Long,
    onClick: () -> Unit,
    listDensity: ListDensity = ListDensity.NORMAL,
    modifier: Modifier = Modifier
) {
    val verticalPadding = if (listDensity == ListDensity.COMPACT) 6.dp else 12.dp
    val fontSize = if (listDensity == ListDensity.COMPACT) 15.sp else 18.sp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = app.label,
            color = Color.White,
            fontSize = fontSize
        )
        Text(
            text = if (usageTimeMillis > 0L) UsageStatsHelper.formatDuration(usageTimeMillis) else "",
            color = Color.Gray,
            fontSize = fontSize
        )
    }
}

@Composable
fun SettingsScreen(
    isFocusMode: Boolean,
    onFocusModeChange: (Boolean) -> Unit,
    autoCarModeEnabled: Boolean,
    onAutoCarModeChange: (Boolean) -> Unit,
    autoCarDevices: List<CarModeBluetoothDevice>,
    onToggleAutoCarDevice: (address: String, name: String, selected: Boolean) -> Unit,
    currentLanguageTag: String?,
    onLanguageChange: (String?) -> Unit,
    isDefaultLauncher: Boolean,
    onSetDefaultClick: () -> Unit,
    hiddenAppsCount: Int,
    onOpenAppVisibility: () -> Unit,
    onOpenRenameApps: () -> Unit,
    clockStyle: ClockStyle,
    onClockStyleChange: (ClockStyle) -> Unit,
    listDensity: ListDensity,
    onListDensityChange: (ListDensity) -> Unit,
    lockScreenEnabled: Boolean,
    onLockScreenEnabledChange: (Boolean) -> Unit,
    hasNotificationAccess: Boolean,
    onRequestNotificationAccess: () -> Unit,
    hasUsageAccess: Boolean,
    onRequestUsageAccess: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Top Back Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.back),
                color = Color.Gray,
                fontSize = 16.sp,
                modifier = Modifier.clickable(onClick = onBackClick)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.settings),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Focus Mode Setting Item
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.focus_mode),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isFocusMode) {
                        stringResource(R.string.focus_mode_active)
                    } else {
                        stringResource(R.string.disabled)
                    },
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Switch(
                checked = isFocusMode,
                onCheckedChange = onFocusModeChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = Color.White,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFF333333),
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = Color(0xFF222222)
        )

        // Auto Car Mode Setting Item
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.auto_car_mode),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.auto_car_mode_desc),
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Switch(
                checked = autoCarModeEnabled,
                onCheckedChange = onAutoCarModeChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = Color.White,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFF333333),
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }

        if (autoCarModeEnabled) {
            var showDevicePicker by remember { mutableStateOf(false) }
            val context = LocalContext.current

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDevicePicker = true }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.auto_car_mode_device),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (autoCarDevices.isEmpty()) {
                            stringResource(R.string.auto_car_mode_device_any)
                        } else {
                            autoCarDevices.joinToString(", ") { it.name }
                        },
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }

                Text(
                    text = stringResource(R.string.edit_arrow),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            if (showDevicePicker) {
                val bondedDevices = remember {
                    runCatching {
                        val manager = context.getSystemService(android.bluetooth.BluetoothManager::class.java)
                        manager?.adapter?.bondedDevices?.toList() ?: emptyList()
                    }.getOrDefault(emptyList())
                }
                val selectedAddresses = autoCarDevices.map { it.address }.toSet()

                AlertDialog(
                    onDismissRequest = { showDevicePicker = false },
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    textContentColor = Color.White,
                    title = { Text(stringResource(R.string.auto_car_mode_device)) },
                    text = {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Text(
                                text = stringResource(R.string.auto_car_mode_device_desc),
                                color = Color.Gray,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            if (bondedDevices.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.auto_car_mode_no_paired_devices),
                                    color = Color.Gray,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )
                            }
                            bondedDevices.forEach { device ->
                                val name = runCatching { device.name }.getOrNull() ?: device.address
                                val checked = selectedAddresses.contains(device.address)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onToggleAutoCarDevice(device.address, name, !checked)
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.material3.Checkbox(
                                        checked = checked,
                                        onCheckedChange = { newChecked ->
                                            onToggleAutoCarDevice(device.address, name, newChecked)
                                        },
                                        colors = androidx.compose.material3.CheckboxDefaults.colors(
                                            checkedColor = Color.White,
                                            checkmarkColor = Color.Black,
                                            uncheckedColor = Color.Gray
                                        )
                                    )
                                    Text(
                                        text = name,
                                        color = Color.White,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showDevicePicker = false }) {
                            Text(stringResource(R.string.snake_settings_close))
                        }
                    }
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = Color(0xFF222222)
        )

        // Manage App Visibility Setting Item
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenAppVisibility)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.visible_apps),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (hiddenAppsCount == 0) {
                        stringResource(R.string.all_apps_visible)
                    } else if (hiddenAppsCount == 1) {
                        stringResource(R.string.apps_hidden_singular, hiddenAppsCount)
                    } else {
                        stringResource(R.string.apps_hidden_plural, hiddenAppsCount)
                    },
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }

            Text(
                text = stringResource(R.string.edit_arrow),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = Color(0xFF222222)
        )

        // Language Setting Item
        var showLanguageDialog by remember { mutableStateOf(false) }
        val currentLanguageLabel = APP_LANGUAGES.find { it.tag == currentLanguageTag }?.label
            ?: APP_LANGUAGES.first().label

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showLanguageDialog = true }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.language),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentLanguageLabel,
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }

            Text(
                text = stringResource(R.string.edit_arrow),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        if (showLanguageDialog) {
            AlertDialog(
                onDismissRequest = { showLanguageDialog = false },
                containerColor = Color.Black,
                titleContentColor = Color.White,
                textContentColor = Color.White,
                title = { Text(stringResource(R.string.language)) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        APP_LANGUAGES.forEach { language ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onLanguageChange(language.tag)
                                        showLanguageDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.RadioButton(
                                    selected = language.tag == currentLanguageTag,
                                    onClick = {
                                        onLanguageChange(language.tag)
                                        showLanguageDialog = false
                                    },
                                    colors = androidx.compose.material3.RadioButtonDefaults.colors(
                                        selectedColor = Color.White,
                                        unselectedColor = Color.Gray
                                    )
                                )
                                Text(
                                    text = language.label,
                                    color = Color.White,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLanguageDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = Color(0xFF222222)
        )

        // Usage Access Setting Item
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onRequestUsageAccess)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.usage_access),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (hasUsageAccess) {
                        stringResource(R.string.usage_access_granted)
                    } else {
                        stringResource(R.string.usage_access_desc)
                    },
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }

            Text(
                text = stringResource(R.string.edit_arrow),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = Color(0xFF222222)
        )

        // Rename Apps Setting Item
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenRenameApps)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.rename_apps),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.rename_apps_desc),
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }

            Text(
                text = stringResource(R.string.edit_arrow),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = Color(0xFF222222)
        )

        // Clock Style Setting Item
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.clock_style),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ClockStyleOption(
                    label = stringResource(R.string.clock_style_analog),
                    selected = clockStyle == ClockStyle.ANALOG,
                    onClick = { onClockStyleChange(ClockStyle.ANALOG) }
                )
                ClockStyleOption(
                    label = stringResource(R.string.clock_style_digital),
                    selected = clockStyle == ClockStyle.DIGITAL,
                    onClick = { onClockStyleChange(ClockStyle.DIGITAL) }
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = Color(0xFF222222)
        )

        // List Density Setting Item
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.list_density),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ClockStyleOption(
                    label = stringResource(R.string.list_density_compact),
                    selected = listDensity == ListDensity.COMPACT,
                    onClick = { onListDensityChange(ListDensity.COMPACT) }
                )
                ClockStyleOption(
                    label = stringResource(R.string.list_density_normal),
                    selected = listDensity == ListDensity.NORMAL,
                    onClick = { onListDensityChange(ListDensity.NORMAL) }
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = Color(0xFF222222)
        )

        // Lock Screen Setting Item
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.lock_screen),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.lock_screen_desc),
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Switch(
                checked = lockScreenEnabled,
                onCheckedChange = onLockScreenEnabledChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = Color.White,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFF333333),
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }

        if (lockScreenEnabled && !hasNotificationAccess) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onRequestNotificationAccess)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.grant_notification_access),
                    color = Color(0xFFAAAAAA),
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.edit_arrow),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = Color(0xFF222222)
        )

        // Default Launcher Setting Item
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.default_launcher),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isDefaultLauncher) {
                        stringResource(R.string.currently_default)
                    } else {
                        stringResource(R.string.not_set_as_default)
                    },
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }

            if (!isDefaultLauncher) {
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = onSetDefaultClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = stringResource(R.string.set), fontSize = 13.sp)
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = Color(0xFF222222)
        )

        // More Apps
        val context = androidx.compose.ui.platform.LocalContext.current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://appsfuncionais.web.app/google-play-apps")
                    )
                    context.startActivity(intent)
                }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.more_apps),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun ClockStyleOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = if (selected) Color.White else Color(0xFF333333),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Color(0xFFAAAAAA),
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun AppVisibilityScreen(
    allApps: List<AppInfo>,
    hiddenAppKeys: Set<String>,
    onToggleVisibility: (AppInfo, Boolean) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Top Back Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.back),
                color = Color.Gray,
                fontSize = 16.sp,
                modifier = Modifier.clickable(onClick = onBackClick)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.visible_apps),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.choose_visible_apps_desc),
            color = Color.Gray,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(
                items = allApps,
                key = { it.key }
            ) { app ->
                val isVisible = !hiddenAppKeys.contains(app.key)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleVisibility(app, !isVisible) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.label,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Text(
                            text = app.packageName,
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }

                    Switch(
                        checked = isVisible,
                        onCheckedChange = { checked ->
                            onToggleVisibility(app, checked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Color.White,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFF333333),
                            uncheckedBorderColor = Color.Transparent
                        )
                    )
                }

                HorizontalDivider(
                    color = Color(0xFF1E1E1E),
                    thickness = 0.5.dp
                )
            }
        }
    }
}


@Composable
fun CarModeAppIcon(app: AppInfo, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(app.key) {
        runCatching {
            val drawable = try {
                context.packageManager.getActivityIcon(ComponentName(app.packageName, app.activityName))
            } catch (_: Exception) {
                context.packageManager.getApplicationIcon(app.packageName)
            }
            drawable.toBitmap(config = Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = app.label,
            contentScale = ContentScale.Fit,
            modifier = modifier
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = app.label.take(1).uppercase(),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun CarModeScreen(
    apps: List<AppInfo>,
    rows: List<CarModeRowConfig>,
    gridSize: CarModeGridSize,
    appWidgetHost: AppWidgetHost,
    appWidgetManager: AppWidgetManager,
    onAppClick: (AppInfo) -> Unit,
    onExitCarMode: () -> Unit,
    onAssignApp: (rowIndex: Int, column: Int, appKey: String?) -> Unit,
    onClearSlot: (rowIndex: Int, column: Int) -> Unit,
    onPickWidget: (rowIndex: Int, column: Int, isWide: Boolean, provider: AppWidgetProviderInfo) -> Unit,
    onSplitRow: (rowIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    data class SlotRef(val rowIndex: Int, val column: Int, val isWide: Boolean)

    var actionSlot by remember { mutableStateOf<SlotRef?>(null) }
    var appPickerSlot by remember { mutableStateOf<SlotRef?>(null) }
    var widgetPickerSlot by remember { mutableStateOf<SlotRef?>(null) }

    fun contentAt(ref: SlotRef): CarModeSlotContent? =
        rows.getOrNull(ref.rowIndex)?.slots?.getOrNull(ref.column)

    val widgetSlot = widgetPickerSlot
    if (widgetSlot != null) {
        BackHandler { widgetPickerSlot = null }
        CarModeWidgetPickerScreen(
            onPick = { provider ->
                onPickWidget(widgetSlot.rowIndex, widgetSlot.column, widgetSlot.isWide, provider)
                widgetPickerSlot = null
            },
            onCancel = { widgetPickerSlot = null },
            modifier = modifier
        )
        return
    }

    val appSlot = appPickerSlot
    if (appSlot != null) {
        val usedAppKeys = rows.flatMapIndexed { rowIndex, row ->
            row.slots.mapIndexedNotNull { column, content ->
                (content as? CarModeSlotContent.App)?.appKey?.takeIf {
                    !(rowIndex == appSlot.rowIndex && column == appSlot.column)
                }
            }
        }.toSet()

        BackHandler { appPickerSlot = null }
        CarModePickerScreen(
            apps = apps,
            excludedKeys = usedAppKeys,
            onPick = { app ->
                onAssignApp(appSlot.rowIndex, appSlot.column, app.key)
                appPickerSlot = null
            },
            onCancel = { appPickerSlot = null },
            modifier = modifier
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.car_mode),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .border(width = 1.dp, color = Color(0xFF333333), shape = RoundedCornerShape(10.dp))
                    .clickable(onClick = onExitCarMode)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.car_mode_close),
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            for (rowIndex in rows.indices) {
                val row = rows[rowIndex]
                val hasWidget = row.wide || row.slots.any { it is CarModeSlotContent.Widget }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (hasWidget) Modifier.weight(1f)
                            else Modifier.aspectRatio(gridSize.columns.toFloat())
                        ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (row.wide) {
                        val content = row.slots.getOrNull(0)
                        CarModeCell(
                            content = content,
                            apps = apps,
                            appWidgetHost = appWidgetHost,
                            appWidgetManager = appWidgetManager,
                            onClick = {
                                val app = (content as? CarModeSlotContent.App)
                                    ?.let { c -> apps.find { it.key == c.appKey } }
                                if (app != null) onAppClick(app)
                                else if (content == null) actionSlot = SlotRef(rowIndex, 0, true)
                            },
                            onLongClick = { actionSlot = SlotRef(rowIndex, 0, true) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    } else {
                        for (column in 0 until gridSize.columns) {
                            val content = row.slots.getOrNull(column)
                            val app = (content as? CarModeSlotContent.App)
                                ?.let { c -> apps.find { it.key == c.appKey } }
                            CarModeCell(
                                content = content,
                                apps = apps,
                                appWidgetHost = appWidgetHost,
                                appWidgetManager = appWidgetManager,
                                onClick = {
                                    if (app != null) onAppClick(app)
                                    else if (content == null) actionSlot = SlotRef(rowIndex, column, false)
                                },
                                onLongClick = { actionSlot = SlotRef(rowIndex, column, false) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }
    }

    val slotForAction = actionSlot
    if (slotForAction != null) {
        CarModeSlotActionDialog(
            hasContent = contentAt(slotForAction) != null,
            isWideSlot = slotForAction.isWide,
            onChooseApp = {
                actionSlot = null
                appPickerSlot = SlotRef(slotForAction.rowIndex, slotForAction.column, false)
            },
            onChooseWidgetThisCell = {
                actionSlot = null
                widgetPickerSlot = SlotRef(slotForAction.rowIndex, slotForAction.column, false)
            },
            onChooseWidgetFullRow = {
                actionSlot = null
                widgetPickerSlot = SlotRef(slotForAction.rowIndex, 0, true)
            },
            onSplitRow = {
                actionSlot = null
                onSplitRow(slotForAction.rowIndex)
            },
            onRemove = {
                actionSlot = null
                onClearSlot(slotForAction.rowIndex, slotForAction.column)
            },
            onDismiss = { actionSlot = null }
        )
    }
}

@Composable
private fun CarModeSlotActionDialog(
    hasContent: Boolean,
    isWideSlot: Boolean,
    onChooseApp: () -> Unit,
    onChooseWidgetThisCell: () -> Unit,
    onChooseWidgetFullRow: () -> Unit,
    onSplitRow: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.Black,
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text(stringResource(R.string.car_mode_slot_title)) },
        text = {
            Column {
                if (isWideSlot) {
                    CarModeActionRow(stringResource(R.string.car_mode_choose_widget), onChooseWidgetFullRow)
                    CarModeActionRow(stringResource(R.string.car_mode_split_row), onSplitRow)
                } else {
                    CarModeActionRow(stringResource(R.string.car_mode_choose_app), onChooseApp)
                    CarModeActionRow(stringResource(R.string.car_mode_choose_widget_cell), onChooseWidgetThisCell)
                    CarModeActionRow(stringResource(R.string.car_mode_choose_widget_wide), onChooseWidgetFullRow)
                }
                if (hasContent) {
                    CarModeActionRow(
                        label = stringResource(R.string.car_mode_remove_app),
                        onClick = onRemove,
                        color = Color(0xFFEE5555)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun CarModeActionRow(label: String, onClick: () -> Unit, color: Color = Color.White) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Text(text = label, color = color, fontSize = 16.sp)
    }
}

@Composable
private fun CarModeCell(
    content: CarModeSlotContent?,
    apps: List<AppInfo>,
    appWidgetHost: AppWidgetHost,
    appWidgetManager: AppWidgetManager,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .border(width = 1.dp, color = Color(0xFF333333), shape = RoundedCornerShape(12.dp))
    ) {
        when (content) {
            is CarModeSlotContent.Widget -> {
                CarModeWidgetView(
                    appWidgetId = content.appWidgetId,
                    appWidgetHost = appWidgetHost,
                    appWidgetManager = appWidgetManager,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(24.dp)
                        .background(Color(0x99000000), RoundedCornerShape(6.dp))
                        .clickable(onClick = onLongClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "⋮", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            is CarModeSlotContent.App -> {
                val app = apps.find { it.key == content.appKey }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (app != null) {
                        CarModeAppIcon(app = app, modifier = Modifier.fillMaxSize().aspectRatio(1f))
                    }
                }
            }
            null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "+",
                        color = Color(0xFF555555),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CarModeWidgetView(
    appWidgetId: Int,
    appWidgetHost: AppWidgetHost,
    appWidgetManager: AppWidgetManager,
    modifier: Modifier = Modifier
) {
    val info = remember(appWidgetId) { runCatching { appWidgetManager.getAppWidgetInfo(appWidgetId) }.getOrNull() }
    if (info == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(text = "!", color = Color.Gray, fontSize = 20.sp)
        }
        return
    }
    BoxWithConstraints(modifier = modifier) {
        val widthDp = maxWidth.value.toInt()
        val heightDp = maxHeight.value.toInt()
        AndroidView(
            factory = { ctx ->
                appWidgetHost.createView(ctx, appWidgetId, info).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    if (widthDp > 0 && heightDp > 0) {
                        updateAppWidgetSize(null, widthDp, heightDp, widthDp, heightDp)
                    }
                }
            },
            update = { view ->
                if (widthDp > 0 && heightDp > 0) {
                    view.updateAppWidgetSize(null, widthDp, heightDp, widthDp, heightDp)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun WidgetPreviewImage(provider: AppWidgetProviderInfo, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(provider) {
        runCatching {
            val drawable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                provider.loadPreviewImage(context, 0)
                    ?: context.packageManager.getApplicationIcon(provider.provider.packageName)
            } else {
                context.packageManager.getApplicationIcon(provider.provider.packageName)
            }
            drawable.toBitmap(config = Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier.padding(6.dp)
        )
    } else {
        Box(modifier = modifier)
    }
}

@Composable
private fun CarModeWidgetPickerScreen(
    onPick: (AppWidgetProviderInfo) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val providers = remember {
        runCatching { AppWidgetManager.getInstance(context).installedProviders }.getOrDefault(emptyList())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.back),
                color = Color.Gray,
                fontSize = 16.sp,
                modifier = Modifier.clickable(onClick = onCancel)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.car_mode_choose_widget),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (providers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.car_mode_no_widgets_found),
                    color = Color.Gray,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(
                    items = providers,
                    key = { "${it.provider.flattenToString()}" }
                ) { provider ->
                    val label = remember(provider) {
                        runCatching { provider.loadLabel(context.packageManager) }
                            .getOrDefault(provider.provider.packageName)
                    }
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(provider) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            WidgetPreviewImage(
                                provider = provider,
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(Color(0xFF111111), RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = label, color = Color.White, fontSize = 16.sp)
                                Text(
                                    text = provider.provider.packageName,
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        HorizontalDivider(color = Color(0xFF1E1E1E), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CarModePickerScreen(
    apps: List<AppInfo>,
    excludedKeys: Set<String>,
    onPick: (AppInfo) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val availableApps = remember(apps, excludedKeys) {
        apps.filter { app -> !excludedKeys.contains(app.key) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.back),
                color = Color.Gray,
                fontSize = 16.sp,
                modifier = Modifier.clickable(onClick = onCancel)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.car_mode_choose_app),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(
                items = availableApps,
                key = { it.key }
            ) { app ->
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(app) }
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = app.label,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    }
                    HorizontalDivider(color = Color(0xFF1E1E1E), thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
fun WeatherDetailsScreen(
    details: WeatherDetails?,
    loading: Boolean,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.back),
                color = Color.Gray,
                fontSize = 16.sp,
                modifier = Modifier.clickable(onClick = onBackClick)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.weather_details_title),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (details == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (loading) {
                        stringResource(R.string.weather_details_loading)
                    } else {
                        stringResource(R.string.weather_details_unavailable)
                    },
                    color = Color.Gray,
                    fontSize = 16.sp
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                if (details.cityName != null) {
                    Text(
                        text = details.cityName,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    WeatherIcon(code = details.weatherCode, modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.weather_temperature, details.temperatureC.toInt()),
                        color = Color.White,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = stringResource(R.string.weather_feels_like, details.feelsLikeC.toInt()),
                    color = Color.Gray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    WeatherStatTile(
                        label = stringResource(R.string.weather_humidity),
                        value = "${details.humidity}%"
                    )
                    WeatherStatTile(
                        label = stringResource(R.string.weather_wind),
                        value = stringResource(R.string.weather_wind_value, details.windSpeedKmh.toInt())
                    )
                    WeatherStatTile(
                        label = stringResource(R.string.weather_precipitation),
                        value = stringResource(R.string.weather_precipitation_value, details.precipitationMm)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.weather_forecast_title),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                val todayLabel = stringResource(R.string.weather_today)
                details.daily.forEachIndexed { index, day ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = weatherDayLabel(day.date, index, todayLabel),
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier.width(56.dp)
                        )
                        WeatherIcon(code = day.weatherCode, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.weather_precipitation_percent, day.precipitationProbability),
                            color = Color(0xFF5599FF),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(
                                R.string.weather_temp_range,
                                day.tempMinC.toInt(),
                                day.tempMaxC.toInt()
                            ),
                            color = Color(0xFFAAAAAA),
                            fontSize = 14.sp
                        )
                    }
                    HorizontalDivider(color = Color(0xFF1E1E1E), thickness = 0.5.dp)
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun WeatherStatTile(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = label, color = Color.Gray, fontSize = 12.sp)
    }
}

private fun weatherDayLabel(dateStr: String, index: Int, todayLabel: String): String {
    if (index == 0) return todayLabel
    return runCatching {
        val parsed = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(dateStr)
        java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault()).format(parsed!!)
    }.getOrDefault(dateStr)
}

@Composable
fun HiddenAppsScreen(
    allApps: List<AppInfo>,
    hiddenAppKeys: Set<String>,
    onAppClick: (AppInfo) -> Unit,
    onBackClick: () -> Unit,
    listDensity: ListDensity,
    modifier: Modifier = Modifier
) {
    val hiddenApps = allApps.filter { app -> hiddenAppKeys.contains(app.key) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .pointerInput(Unit) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        totalDrag += dragAmount
                    },
                    onDragEnd = {
                        val threshold = 80f
                        if (totalDrag > threshold) {
                            onBackClick()
                        }
                    }
                )
            }
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Top Back Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.back),
                color = Color.Gray,
                fontSize = 16.sp,
                modifier = Modifier.clickable(onClick = onBackClick)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.hidden_apps),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (hiddenApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_hidden_apps),
                    color = Color.Gray,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(
                    items = hiddenApps,
                    key = { it.key }
                ) { app ->
                    AppListItem(
                        app = app,
                        onClick = { onAppClick(app) },
                        listDensity = listDensity
                    )
                }
            }
        }
    }
}

@Composable
fun RenameAppsScreen(
    allApps: List<AppInfo>,
    onRenameApp: (AppInfo, String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var appBeingRenamed by remember { mutableStateOf<AppInfo?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.back),
                color = Color.Gray,
                fontSize = 16.sp,
                modifier = Modifier.clickable(onClick = onBackClick)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.rename_apps),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.rename_apps_desc),
            color = Color.Gray,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(
                items = allApps,
                key = { it.key }
            ) { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { appBeingRenamed = app }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = app.label,
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(R.string.rename),
                        color = Color(0xFFAAAAAA),
                        fontSize = 13.sp
                    )
                }

                HorizontalDivider(
                    color = Color(0xFF1E1E1E),
                    thickness = 0.5.dp
                )
            }
        }
    }

    val app = appBeingRenamed
    if (app != null) {
        RenameAppDialog(
            app = app,
            onConfirm = { newLabel ->
                onRenameApp(app, newLabel)
                appBeingRenamed = null
            },
            onDismiss = { appBeingRenamed = null }
        )
    }
}

@Composable
fun RenameAppDialog(
    app: AppInfo,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember(app) { mutableStateOf(app.label) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.rename_apps))
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text(text = app.label) }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(text = stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppListItem(
    app: AppInfo,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    listDensity: ListDensity = ListDensity.NORMAL,
    modifier: Modifier = Modifier
) {
    val verticalPadding = if (listDensity == ListDensity.COMPACT) 6.dp else 12.dp
    val fontSize = if (listDensity == ListDensity.COMPACT) 15.sp else 18.sp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = verticalPadding)
    ) {
        Text(
            text = app.label,
            color = Color.White,
            fontSize = fontSize
        )
    }
}
