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

class MainActivity : FragmentActivity() {

    private var backgroundTimestamp = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize managers
        SettingsManager.init(this)
        AppSecurityManager.init(this)
        ProfileManager.init(this)
        FirebaseBridgeManager.init(this)

        setContent {
            MyApplicationTheme {
                val isUnlocked by AppSecurityManager.isAppUnlocked.collectAsState()
                val securityConfig by AppSecurityManager.securityConfig.collectAsState()
                val settings by SettingsManager.settings.collectAsState()

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

                if (!isUnlocked && securityConfig.isPinEnabled) {
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


