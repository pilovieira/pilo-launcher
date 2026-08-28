package br.com.pilovieira.launcher

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
class LockScreenActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setupShowOverKeyguard()

        val clockStyle = loadClockStyle()

        setContent {
            val notificationCount by PiloNotificationListenerService.notificationCount.collectAsState()

            androidx.compose.material3.MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
                LockScreenContent(
                    clockStyle = clockStyle,
                    notificationCount = notificationCount,
                    onUnlockRequested = { requestUnlock() }
                )
            }
        }
    }

    private fun loadClockStyle(): ClockStyle {
        val prefs = getSharedPreferences("launcher_app_prefs", MODE_PRIVATE)
        val stored = prefs.getString("key_clock_style", null) ?: return ClockStyle.ANALOG
        return try {
            ClockStyle.valueOf(stored)
        } catch (_: IllegalArgumentException) {
            ClockStyle.ANALOG
        }
    }

    private fun setupShowOverKeyguard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    private fun requestUnlock() {
        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as? KeyguardManager

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || keyguardManager == null) {
            finish()
            return
        }

        // Let the system show its own single unlock UI (biometric/PIN/pattern as configured)
        // and only finish once it confirms the device is actually unlocked. Do NOT run our own
        // BiometricPrompt beforehand -- that would authenticate once for us and then a second
        // time for the real keyguard, showing two unlock screens back to back.
        keyguardManager.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    finish()
                }
            }
        )
    }
}

@Composable
private fun LockScreenContent(
    clockStyle: ClockStyle,
    notificationCount: Int,
    onUnlockRequested: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .clickable(
                indication = null,
                interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) { onUnlockRequested() }
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (clockStyle) {
                ClockStyle.ANALOG -> AnalogClock(modifier = Modifier.size(220.dp))
                ClockStyle.DIGITAL -> DigitalClock()
            }

            if (notificationCount > 0) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = if (notificationCount == 1) {
                        stringResource(R.string.notification_count_singular, notificationCount)
                    } else {
                        stringResource(R.string.notification_count_plural, notificationCount)
                    },
                    color = Color(0xFFAAAAAA),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Text(
            text = stringResource(R.string.tap_to_unlock),
            color = Color(0xFF666666),
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
        )
    }
}
