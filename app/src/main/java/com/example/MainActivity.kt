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

import com.example.util.FlipToPanicDetector
import android.view.KeyEvent

class MainActivity : FragmentActivity() {

    private var backgroundTimestamp = 0L
    private var shakeDetector: ShakeDetector? = null
    private var flipToPanicDetector: FlipToPanicDetector? = null
    private var lastVolumeClickTime = 0L
    private var volumeClickCount = 0

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
                vibratePanic()
                PanicModeController.triggerPanic()
            }
        }

        flipToPanicDetector = FlipToPanicDetector(this) {
            val settings = SettingsManager.settings.value
            if (settings.flipToPanicEnabled) {
                vibratePanic()
                PanicModeController.triggerPanic()
            }
        }

        setContent {
            MyApplicationTheme {
                val isUnlocked by AppSecurityManager.isAppUnlocked.collectAsState()
                val securityConfig by AppSecurityManager.securityConfig.collectAsState()
                val settings by SettingsManager.settings.collectAsState()
                val isPanicActive by PanicModeController.isPanicActive.collectAsState()

                // Apply Anti-Screenshot protection (controls window FLAG_SECURE)
                LaunchedEffect(securityConfig.isAntiScreenshotEnabled) {
                    if (securityConfig.isAntiScreenshotEnabled) {
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
        flipToPanicDetector?.start()
        FirebaseBridgeManager.init(this)
    }

    override fun onPause() {
        super.onPause()
        shakeDetector?.stop()
        flipToPanicDetector?.stop()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val keyCode = event.keyCode
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                val settings = SettingsManager.settings.value
                if (settings.volumeButtonPanicEnabled) {
                    val now = System.currentTimeMillis()
                    if (now - lastVolumeClickTime < 650L) {
                        volumeClickCount++
                        if (volumeClickCount >= 2) {
                            volumeClickCount = 0
                            lastVolumeClickTime = 0L
                            vibratePanic()
                            PanicModeController.triggerPanic()
                            return true
                        }
                    } else {
                        volumeClickCount = 1
                        lastVolumeClickTime = now
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun vibratePanic() {
        try {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(180)
            }
        } catch (_: Throwable) {}
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


