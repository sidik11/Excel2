package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import com.example.ui.MainScreen
import com.example.ui.security.AppLockScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.AppSecurityManager
import com.example.util.FirebaseBridgeManager
import com.example.util.ProfileManager
import com.example.util.SettingsManager
import com.example.util.ShakeDetector
import com.example.util.ActivityLogManager
import com.example.util.PanicModeController
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.Build

class MainActivity : FragmentActivity() {

    private var backgroundTimestamp = 0L
    private var shakeDetector: ShakeDetector? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize managers
        SettingsManager.init(this)
        FirebaseBridgeManager.init(this)
        AppSecurityManager.init(this)
        ProfileManager.init(this)
        ActivityLogManager.init(this)

        shakeDetector = ShakeDetector(this) {
            val settings = SettingsManager.settings.value
            if (settings.shakeToLockEnabled) {
                // Vibrate feedback on shake lock
                try {
                    val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(150)
                    }
                } catch (_: Throwable) {}
                PanicModeController.triggerPanic()
            }
        }

        setContent {
            MyApplicationTheme {
                val isUnlocked by AppSecurityManager.isAppUnlocked.collectAsState()
                val securityConfig by AppSecurityManager.securityConfig.collectAsState()
                val settings by SettingsManager.settings.collectAsState()
                val isPanicActive by PanicModeController.isPanicActive.collectAsState()

                // Apply Anti-Screenshot and Recent App Privacy protection (masks Recent Apps switcher)
                LaunchedEffect(securityConfig.isAntiScreenshotEnabled, settings.recentAppPrivacy) {
                    if (securityConfig.isAntiScreenshotEnabled || settings.recentAppPrivacy) {
                        window.setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE
                        )
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }

                if (isPanicActive) {
                    com.example.ui.security.PanicTreeDisguiseOverlay(
                        onDismiss = { PanicModeController.dismissPanic() }
                    )
                } else if (!isUnlocked && securityConfig.isPinEnabled) {
                    AppLockScreen(
                        onUnlocked = {
                            // App is unlocked, state updates automatically
                        }
                    )
                } else {
                    MainScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        shakeDetector?.start()
        FirebaseBridgeManager.init(this)
    }

    override fun onPause() {
        super.onPause()
        shakeDetector?.stop()
    }

    override fun onStop() {
        super.onStop()
        backgroundTimestamp = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        val settings = SettingsManager.settings.value
        val config = AppSecurityManager.securityConfig.value

        if (config.isPinEnabled && settings.autoLockTimeoutMinutes > 0 && backgroundTimestamp > 0L) {
            val elapsed = System.currentTimeMillis() - backgroundTimestamp
            val timeoutMillis = settings.autoLockTimeoutMinutes * 60 * 1000L
            if (elapsed >= timeoutMillis) {
                AppSecurityManager.lockApp()
            }
        }
    }
}


