package br.com.pilovieira.launcher

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import java.util.Calendar

enum class Screen {
    HOME,
    LAUNCHER,
    RECENTS,
    SETTINGS,
    APP_VISIBILITY,
    HIDDEN_APPS,
    RENAME_APPS
}

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    private var currentScreen by mutableStateOf(Screen.LAUNCHER)
    private var isDefault by mutableStateOf(false)
    private var isFocusMode by mutableStateOf(false)

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        isDefault = isDefaultLauncher()
        isFocusMode = FocusModeHelper.isFocusModeEnabled(this)

        setContent {
            val apps by viewModel.apps.collectAsState()
            val allApps by viewModel.allApps.collectAsState()
            val hiddenAppKeys by viewModel.hiddenAppKeys.collectAsState()
            val recentApps by viewModel.recentApps.collectAsState()

            when (currentScreen) {
                Screen.HOME -> {
                    BackHandler {
                        // Home screen: back button does nothing.
                    }
                    HomeScreen(
                        isFocusMode = isFocusMode,
                        onSwipeUp = { currentScreen = Screen.LAUNCHER },
                        onSwipeDown = { currentScreen = Screen.RECENTS }
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
                        }
                    )
                }
                Screen.RECENTS -> {
                    BackHandler {
                        currentScreen = Screen.HOME
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
                            currentScreen = Screen.HOME
                        }
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
                        }
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            currentScreen = Screen.LAUNCHER
        }
    }

    override fun onResume() {
        super.onResume()
        isDefault = isDefaultLauncher()
        isFocusMode = FocusModeHelper.isFocusModeEnabled(this)
        if (isFocusMode) {
            FocusModeHelper.applyRingerMode(this)
        }
        viewModel.loadApps()
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
            currentScreen = Screen.HOME
        } catch (e: Exception) {
            val fallbackIntent = packageManager.getLaunchIntentForPackage(app.packageName)
            if (fallbackIntent != null) {
                startActivity(fallbackIntent)
                viewModel.recordAppOpened(app)
                currentScreen = Screen.HOME
            } else {
                Toast.makeText(this, getString(R.string.could_not_open_app, app.label), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun HomeScreen(
    isFocusMode: Boolean,
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
        AnalogClock(modifier = Modifier.size(220.dp).align(Alignment.Center))

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
fun LauncherScreen(
    apps: List<AppInfo>,
    onAppClick: (AppInfo) -> Unit,
    onOpenSettings: () -> Unit,
    isDefaultLauncher: Boolean,
    onSetDefaultClick: () -> Unit,
    hiddenAppsCount: Int,
    onOpenHiddenApps: () -> Unit,
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
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
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
                            onLongClick = { appForContextMenu = app }
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

                    // Settings button item at the end of the apps list
                    item(key = "launcher_settings_item") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenSettings)
                                .padding(vertical = 14.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.settings),
                                color = Color(0xFFAAAAAA),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
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
                    AppListItem(
                        app = app,
                        onClick = { onAppClick(app) }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    isFocusMode: Boolean,
    onFocusModeChange: (Boolean) -> Unit,
    isDefaultLauncher: Boolean,
    onSetDefaultClick: () -> Unit,
    hiddenAppsCount: Int,
    onOpenAppVisibility: () -> Unit,
    onOpenRenameApps: () -> Unit,
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
fun HiddenAppsScreen(
    allApps: List<AppInfo>,
    hiddenAppKeys: Set<String>,
    onAppClick: (AppInfo) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hiddenApps = allApps.filter { app -> hiddenAppKeys.contains(app.key) }

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
                        onClick = { onAppClick(app) }
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
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = app.label,
            color = Color.White,
            fontSize = 18.sp
        )
    }
}
